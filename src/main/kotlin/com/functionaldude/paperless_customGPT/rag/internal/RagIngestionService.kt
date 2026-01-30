package com.functionaldude.paperless_customGPT.rag.internal

import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_CHUNK
import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_SOURCE
import com.functionaldude.paperless.jooq.public.tables.references.DOCUMENTS_CORRESPONDENT
import com.functionaldude.paperless.jooq.public.tables.references.DOCUMENTS_DOCUMENT
import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService.Companion.PDF_MIME
import com.functionaldude.paperless_customGPT.rag.api.IngestStatus
import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentSplitter
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime

data class IngestionCandidate(
  val paperlessDocId: Int,
  val title: String,
  val correspondentName: String,
  val modifiedAt: OffsetDateTime,
  val createdAt: LocalDate,
)

@Service
class RagIngestionService(
  private val dsl: DSLContext,
  private val paperlessDocumentService: PaperlessDocumentService,
  private val embeddingModel: EmbeddingModel,
  private val documentSplitter: DocumentSplitter,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun processCandidate(candidate: IngestionCandidate) {
    log.info("Ingesting document ${candidate.paperlessDocId} - ${candidate.title} - ${candidate.correspondentName}")

    createDocumentSource(candidate)

    try {
      createRagEntriesForDocument(candidate.paperlessDocId)

      markIngestionComplete(candidate.paperlessDocId, IngestStatus.DONE)
    } catch (e: Exception) {
      log.error("Error ingesting document ${candidate.paperlessDocId}", e)
      markIngestionComplete(candidate.paperlessDocId, IngestStatus.ERROR, errorMessage = e.message)
      return
    }

    log.info("Done ingesting document ${candidate.paperlessDocId}")
  }

  fun findIngestCandidates(limit: Int): List<IngestionCandidate> {
    return dsl
      .select(
        DOCUMENTS_DOCUMENT.ID,
        DOCUMENTS_DOCUMENT.TITLE,
        DOCUMENTS_DOCUMENT.MODIFIED,
        DOCUMENTS_DOCUMENT.CREATED,
        DOCUMENTS_CORRESPONDENT.NAME,
        DOCUMENT_SOURCE.STATUS,
      )
      .from(DOCUMENTS_DOCUMENT)
      .leftJoin(DOCUMENTS_CORRESPONDENT).on(DOCUMENTS_DOCUMENT.CORRESPONDENT_ID.eq(DOCUMENTS_CORRESPONDENT.ID))
      .leftJoin(DOCUMENT_SOURCE).on(DOCUMENTS_DOCUMENT.ID.eq(DOCUMENT_SOURCE.PAPERLESS_DOC_ID))
      .where(
        DOCUMENTS_DOCUMENT.MIME_TYPE.eq(PDF_MIME).and(
          DOCUMENT_SOURCE.PAPERLESS_DOC_ID.isNull
            .or(DOCUMENTS_DOCUMENT.MODIFIED.gt(DOCUMENT_SOURCE.LAST_INGESTED_AT))
            .or(DOCUMENT_SOURCE.STATUS.eq(IngestStatus.ERROR.name))
        )
      )
      .orderBy(DOCUMENTS_DOCUMENT.MODIFIED.asc())
      .limit(limit)
      .fetch { record ->
        IngestionCandidate(
          paperlessDocId = record.get(DOCUMENTS_DOCUMENT.ID)!!,
          title = record.get(DOCUMENTS_DOCUMENT.TITLE)!!,
          modifiedAt = record.get(DOCUMENTS_DOCUMENT.MODIFIED)!!,
          createdAt = record.get(DOCUMENTS_DOCUMENT.CREATED)!!,
          correspondentName = record.get(DOCUMENTS_CORRESPONDENT.NAME) ?: "",
        )
      }
  }

  private fun createDocumentSource(candidate: IngestionCandidate) {
    val now = OffsetDateTime.now()

    val existingRecord = dsl
      .selectFrom(DOCUMENT_SOURCE)
      .where(DOCUMENT_SOURCE.PAPERLESS_DOC_ID.eq(candidate.paperlessDocId))
      .fetchOne()

    val record = existingRecord ?: dsl.newRecord(DOCUMENT_SOURCE).apply {
      paperlessDocId = candidate.paperlessDocId
    }

    record.apply {
      status = IngestStatus.RUNNING.name
      title = candidate.title
      correspondent = candidate.correspondentName
      docDate = candidate.createdAt
      paperlessModifiedAt = candidate.modifiedAt
      lastIngestedAt = null
      errorMessage = null
      updatedAt = now

      store()
    }
  }

  private fun markIngestionComplete(paperlessDocId: Int, status: IngestStatus, errorMessage: String? = null) {
    dsl
      .update(DOCUMENT_SOURCE)
      .set(DOCUMENT_SOURCE.STATUS, status.name)
      .set(DOCUMENT_SOURCE.ERROR_MESSAGE, errorMessage)
      .set(DOCUMENT_SOURCE.LAST_INGESTED_AT, OffsetDateTime.now())
      .set(DOCUMENT_SOURCE.UPDATED_AT, OffsetDateTime.now())
      .where(DOCUMENT_SOURCE.PAPERLESS_DOC_ID.eq(paperlessDocId))
      .execute()
  }

  private fun createRagEntriesForDocument(documentId: Int) {
    val paperlessDocument: DocumentDto = paperlessDocumentService.findDocumentById(documentId) ?: run {
      log.warn("Document $documentId not found, cannot create RAG entries")
      throw IllegalStateException("Document $documentId not found")
    }

    val fullText = buildString {
      appendLine("Title: ${paperlessDocument.title}")
      paperlessDocument.correspondentName?.let { appendLine("Correspondent: $it") }
      appendLine("Date: ${paperlessDocument.documentDate}")
      appendLine()
      appendLine(paperlessDocument.content)
      appendLine()
      appendLine("Note: ${paperlessDocument.note ?: "(no note)"}")
    }

    val metadata = mapOf(
      "paperless_doc_id" to documentId,
      "title" to paperlessDocument.title,
      "correspondent_name" to (paperlessDocument.correspondentName ?: ""),
      "document_date" to paperlessDocument.documentDate.toString(),
      "owner_username" to (paperlessDocument.ownerUsername ?: ""),
      "tags" to (paperlessDocument.tags?.joinToString(",") ?: ""),
    )

    val segments: List<TextSegment> = documentSplitter.split(Document.from(fullText, Metadata(metadata)))
    if (segments.isEmpty()) {
      log.warn("No segments produced for paperlessDocId=$documentId, skipping")
      return
    }

    val embeddings = embeddingModel.embedAll(segments).content()
    if (embeddings.size != segments.size) {
      throw IllegalStateException("Embedding count (${embeddings.size}) != segment count (${segments.size})")
    }

    // Delete old chunks first
    dsl.deleteFrom(DOCUMENT_CHUNK)
      .where(DOCUMENT_CHUNK.DOCUMENT_SOURCE_ID.eq(paperlessDocument.id))
      .execute()

    // Insert new chunks
    val now = OffsetDateTime.now()

    embeddings.forEachIndexed { index, embedding ->
      val segmentText = segments[index].text()

      val vector = embedding.vector()

      dsl.insertInto(DOCUMENT_CHUNK)
        .set(DOCUMENT_CHUNK.DOCUMENT_SOURCE_ID, documentId)
        .set(DOCUMENT_CHUNK.CHUNK_INDEX, index)
        .set(DOCUMENT_CHUNK.CONTENT, segmentText)
        .set(DOCUMENT_CHUNK.EMBEDDING, vector)
        .set(DOCUMENT_CHUNK.CREATED_AT, now)
        .execute()
    }
  }
}
