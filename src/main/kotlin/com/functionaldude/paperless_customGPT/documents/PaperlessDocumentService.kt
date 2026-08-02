package com.functionaldude.paperless_customGPT.documents

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.functionaldude.paperless.jooq.public.tables.references.*
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SortField
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime

@JsonClassDescription("Paperless document metadata together with extracted text content.")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DocumentDto(
  @field:JsonPropertyDescription("Unique numeric identifier of the Paperless document.")
  val id: Int,
  @field:JsonPropertyDescription("Human readable document title.")
  val title: String,
  @field:JsonPropertyDescription("Creation date registered in Paperless, formatted as an ISO-8601 date.")
  val documentDate: LocalDate,
  @field:JsonProperty(required = false)
  @field:JsonPropertyDescription("Last modification timestamp if Paperless recorded one.")
  val modifiedAt: OffsetDateTime?,
  @field:JsonPropertyDescription("Persisted MIME type of the document.")
  val mimeType: String,
  @field:JsonPropertyDescription("Full text content extracted from the source PDF.")
  val content: String,
  @field:JsonProperty(required = false)
  @field:JsonPropertyDescription("Optional Paperless username of the document owner.")
  val ownerUsername: String?,
  @field:JsonProperty(required = false)
  @field:JsonPropertyDescription("Free text notes added in Paperless.")
  val note: String?,
  @field:JsonProperty(required = false)
  @field:JsonPropertyDescription("Name of the correspondent linked to the document.")
  val correspondentName: String?,
  @field:JsonProperty(required = false)
  @field:JsonPropertyDescription("Tags linked to the document.")
  val tags: List<String>?,
  @field:JsonPropertyDescription("Direct link to the document inside Paperless.")
  val sourceUrl: String,
)

@JsonClassDescription("Wrapper containing Paperless documents.")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DocumentList(
  @field:JsonPropertyDescription("Paperless documents matching the tool request.")
  val documents: List<DocumentDto>,
  @field:JsonProperty(required = false)
  @field:JsonPropertyDescription("Offset to pass to listDocuments for the next page, if one exists.")
  val nextOffset: Int? = null,
)

@Service
class PaperlessDocumentService(
  private val dsl: DSLContext,
  private val paperlessUrlProvider: PaperlessUrlProvider,
) {
  private fun findDocs(
    conditions: Collection<Condition>,
    orderBy: Collection<SortField<*>> = listOf(DOCUMENTS_DOCUMENT.MODIFIED.desc()),
    limit: Int? = null,
    offset: Int = 0,
  ): List<DocumentDto> {
    val latestVersion = DOCUMENTS_DOCUMENT.`as`("latest_version")
    val latestVersionContent: Field<String?> = dsl
      .select(latestVersion.CONTENT)
      .from(latestVersion)
      .where(latestVersion.ROOT_DOCUMENT_ID.eq(DOCUMENTS_DOCUMENT.ID))
      .and(latestVersion.DELETED_AT.isNull)
      .orderBy(latestVersion.ID.desc())
      .limit(1)
      .asField<String?>()
    val latestVersionMimeType: Field<String?> = dsl
      .select(latestVersion.MIME_TYPE)
      .from(latestVersion)
      .where(latestVersion.ROOT_DOCUMENT_ID.eq(DOCUMENTS_DOCUMENT.ID))
      .and(latestVersion.DELETED_AT.isNull)
      .orderBy(latestVersion.ID.desc())
      .limit(1)
      .asField<String?>()
    val effectiveContent: Field<String?> = coalesce(latestVersionContent, DOCUMENTS_DOCUMENT.CONTENT)
    val effectiveMimeType: Field<String?> = coalesce(latestVersionMimeType, DOCUMENTS_DOCUMENT.MIME_TYPE)
    val selectedEffectiveContent = effectiveContent.`as`("effective_content")
    val selectedEffectiveMimeType = effectiveMimeType.`as`("effective_mime_type")
    val effectiveConditions = listOf(
      DOCUMENTS_DOCUMENT.ROOT_DOCUMENT_ID.isNull,
      DOCUMENTS_DOCUMENT.DELETED_AT.isNull,
      effectiveMimeType.eq(PDF_MIME),
    ) + conditions

    val query = dsl
      .select(
        DOCUMENTS_DOCUMENT.ID,
        DOCUMENTS_DOCUMENT.TITLE,
        DOCUMENTS_DOCUMENT.CREATED,
        DOCUMENTS_DOCUMENT.MODIFIED,
        selectedEffectiveMimeType,
        selectedEffectiveContent,
        AUTH_USER.USERNAME,
        DOCUMENTS_NOTE.NOTE,
        DOCUMENTS_CORRESPONDENT.NAME,
        arrayAgg(DOCUMENTS_TAG.NAME).`as`("tag_names"),
      )
      .from(DOCUMENTS_DOCUMENT)
      .leftJoin(AUTH_USER).on(DOCUMENTS_DOCUMENT.OWNER_ID.eq(AUTH_USER.ID))
      .leftJoin(DOCUMENTS_NOTE).on(DOCUMENTS_DOCUMENT.ID.eq(DOCUMENTS_NOTE.DOCUMENT_ID))
      .leftJoin(DOCUMENTS_CORRESPONDENT).on(DOCUMENTS_DOCUMENT.CORRESPONDENT_ID.eq(DOCUMENTS_CORRESPONDENT.ID))
      .leftJoin(DOCUMENTS_DOCUMENT_TAGS).on(DOCUMENTS_DOCUMENT_TAGS.DOCUMENT_ID.eq(DOCUMENTS_DOCUMENT.ID))
      .leftJoin(DOCUMENTS_TAG).on(DOCUMENTS_TAG.ID.eq(DOCUMENTS_DOCUMENT_TAGS.TAG_ID))
      .where(effectiveConditions)
      .groupBy(
        DOCUMENTS_DOCUMENT.ID,
        AUTH_USER.USERNAME,
        DOCUMENTS_NOTE.NOTE,
        DOCUMENTS_CORRESPONDENT.NAME,
      )
      .orderBy(orderBy)

    return if (limit == null) {
      query.fetch { record ->
        toDocumentDto(record, selectedEffectiveMimeType, selectedEffectiveContent)
      }
    } else {
      query.limit(limit).offset(offset).fetch { record ->
        toDocumentDto(record, selectedEffectiveMimeType, selectedEffectiveContent)
      }
    }
  }

  private fun toDocumentDto(
    record: org.jooq.Record,
    selectedEffectiveMimeType: Field<String?>,
    selectedEffectiveContent: Field<String?>,
  ): DocumentDto = DocumentDto(
    id = record.get(DOCUMENTS_DOCUMENT.ID)!!,
    title = record.get(DOCUMENTS_DOCUMENT.TITLE) ?: "(untitled)",
    documentDate = record.get(DOCUMENTS_DOCUMENT.CREATED)!!,
    modifiedAt = record.get(DOCUMENTS_DOCUMENT.MODIFIED),
    mimeType = record.get(selectedEffectiveMimeType)!!,
    content = record.get(selectedEffectiveContent)!!,
    ownerUsername = record.get(AUTH_USER.USERNAME),
    note = record.get(DOCUMENTS_NOTE.NOTE),
    correspondentName = record.get(DOCUMENTS_CORRESPONDENT.NAME),
    tags = record.get("tag_names", Array<String>::class.java)?.filterNotNull()?.toList() ?: emptyList(),
    sourceUrl = paperlessUrlProvider.documentUrl(record.get(DOCUMENTS_DOCUMENT.ID)!!),
  )

  private fun findDocs(
    vararg conditions: Condition,
    orderBy: SortField<*> = DOCUMENTS_DOCUMENT.CREATED.desc()
  ): List<DocumentDto> = findDocs(conditions.toList(), listOf(orderBy))

  fun findAllDocuments(): List<DocumentDto> = findDocs()

  fun findDocumentsPage(limit: Int, offset: Int): DocumentList {
    val documents = findDocs(
      conditions = emptyList(),
      orderBy = PAGE_ORDER,
      limit = limit + 1,
      offset = offset,
    )
    val hasNextPage = documents.size > limit

    return DocumentList(
      documents = documents.take(limit),
      nextOffset = if (hasNextPage) offset + limit else null,
    )
  }

  fun findDocumentById(id: Int): DocumentDto? = findDocs(
    DOCUMENTS_DOCUMENT.ID.eq(id),
  ).firstOrNull()

  fun findDocumentsByCorrespondent(
    correspondentName: String,
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
  ): List<DocumentDto> = findDocs(
    conditions = documentFilterConditions(fromDate, toDate) +
        DOCUMENTS_CORRESPONDENT.NAME.containsIgnoreCase(correspondentName),
    orderBy = CREATION_DATE_ORDER,
  )

  fun findDocumentsByTag(
    tagName: String,
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
  ): List<DocumentDto> {
    val matchingDocumentTags = DOCUMENTS_DOCUMENT_TAGS.`as`("matching_document_tags")
    val matchingTags = DOCUMENTS_TAG.`as`("matching_tags")
    val hasTag = exists(
      dsl.selectOne()
        .from(matchingDocumentTags)
        .join(matchingTags).on(matchingTags.ID.eq(matchingDocumentTags.TAG_ID))
        .where(matchingDocumentTags.DOCUMENT_ID.eq(DOCUMENTS_DOCUMENT.ID))
        .and(matchingTags.NAME.equalIgnoreCase(tagName))
    )

    return findDocs(
      conditions = documentFilterConditions(fromDate, toDate) + hasTag,
      orderBy = CREATION_DATE_ORDER,
    )
  }

  private fun documentFilterConditions(fromDate: LocalDate?, toDate: LocalDate?): List<Condition> = buildList {
    fromDate?.let { add(DOCUMENTS_DOCUMENT.CREATED.ge(it)) }
    toDate?.let { add(DOCUMENTS_DOCUMENT.CREATED.le(it)) }
  }

  companion object {
    const val PDF_MIME = "application/pdf"

    private val CREATION_DATE_ORDER = listOf(
      DOCUMENTS_DOCUMENT.CREATED.desc(),
      DOCUMENTS_DOCUMENT.ID.desc(),
    )

    private val PAGE_ORDER = listOf(
      DOCUMENTS_DOCUMENT.MODIFIED.desc(),
      DOCUMENTS_DOCUMENT.ID.desc(),
    )
  }
}
