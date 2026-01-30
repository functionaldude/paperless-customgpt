package com.functionaldude.paperless_customGPT.documents

import com.functionaldude.paperless.jooq.public.tables.references.*
import io.swagger.v3.oas.annotations.media.Schema
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL.arrayAgg
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime

@Schema(description = "Paperless document metadata together with extracted text content.")
data class DocumentDto(
  @field:Schema(description = "Unique numeric identifier of the Paperless document.", example = "42")
  val id: Int,
  @field:Schema(description = "Human readable document title.", example = "Renewal Policy")
  val title: String,
  @field:Schema(description = "Creation date registered in Paperless (ISO-8601).", example = "2024-03-15")
  val documentDate: LocalDate,
  @field:Schema(
    description = "Last modification timestamp if Paperless recorded one.",
    example = "2024-03-22T10:15:30Z",
    nullable = true
  )
  val modifiedAt: OffsetDateTime?,
  @field:Schema(description = "Persisted MIME type of the document.", example = "application/pdf")
  val mimeType: String,
  @field:Schema(description = "Full text content extracted from the source PDF.")
  val content: String,
  @field:Schema(description = "Optional Paperless username of the document owner.", example = "agent", nullable = true)
  val ownerUsername: String?,
  @field:Schema(description = "Free text notes added in Paperless.", nullable = true)
  val note: String?,
  @field:Schema(
    description = "Name of the correspondent linked to the document.",
    example = "ACME Insurance",
    nullable = true
  )
  val correspondentName: String?,
  @field:Schema(description = "Tags linked to the document.", example = "[\"Insurance\",\"Policy\"]", nullable = true)
  val tags: List<String>?,
)

@Service
class PaperlessDocumentService(
  private val dsl: DSLContext
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
      .leftJoin(DOCUMENTS_NOTE).on(DOCUMENTS_DOCUMENT.ID.eq(DOCUMENTS_NOTE.ID))
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
