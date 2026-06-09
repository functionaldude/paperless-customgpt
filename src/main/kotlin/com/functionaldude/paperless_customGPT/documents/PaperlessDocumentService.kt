package com.functionaldude.paperless_customGPT.documents

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.functionaldude.paperless.jooq.public.tables.references.*
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL.arrayAgg
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

@Service
class PaperlessDocumentService(
  private val dsl: DSLContext,
  private val paperlessUrlProvider: PaperlessUrlProvider,
) {
  private fun findDocs(vararg conditions: Condition): List<DocumentDto> {
    return dsl
      .select(
        DOCUMENTS_DOCUMENT.ID,
        DOCUMENTS_DOCUMENT.TITLE,
        DOCUMENTS_DOCUMENT.CREATED,
        DOCUMENTS_DOCUMENT.MODIFIED,
        DOCUMENTS_DOCUMENT.MIME_TYPE,
        DOCUMENTS_DOCUMENT.CONTENT,
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
      .where(*conditions)
      .groupBy(
        DOCUMENTS_DOCUMENT.ID,
        AUTH_USER.USERNAME,
        DOCUMENTS_NOTE.NOTE,
        DOCUMENTS_CORRESPONDENT.NAME,
      )
      .orderBy(DOCUMENTS_DOCUMENT.MODIFIED.desc())
      .fetch { record ->
        DocumentDto(
          id = record.get(DOCUMENTS_DOCUMENT.ID)!!,
          title = record.get(DOCUMENTS_DOCUMENT.TITLE) ?: "(untitled)",
          documentDate = record.get(DOCUMENTS_DOCUMENT.CREATED)!!,
          modifiedAt = record.get(DOCUMENTS_DOCUMENT.MODIFIED),
          mimeType = record.get(DOCUMENTS_DOCUMENT.MIME_TYPE)!!,
          content = record.get(DOCUMENTS_DOCUMENT.CONTENT)!!,
          ownerUsername = record.get(AUTH_USER.USERNAME),
          note = record.get(DOCUMENTS_NOTE.NOTE),
          correspondentName = record.get(DOCUMENTS_CORRESPONDENT.NAME),
          tags = record.get("tag_names", Array<String>::class.java)?.filterNotNull()?.toList() ?: emptyList(),
          sourceUrl = paperlessUrlProvider.documentUrl(record.get(DOCUMENTS_DOCUMENT.ID)!!),
        )
      }
  }

  fun findAllDocuments(): List<DocumentDto> {
    return findDocs(
      DOCUMENTS_DOCUMENT.MIME_TYPE.eq(PDF_MIME)
    )
  }

  fun findDocumentById(id: Int): DocumentDto? {
    return findDocs(
      DOCUMENTS_DOCUMENT.ID.eq(id),
      DOCUMENTS_DOCUMENT.MIME_TYPE.eq(PDF_MIME)
    ).firstOrNull()
  }

  companion object {
    const val PDF_MIME = "application/pdf"
  }
}
