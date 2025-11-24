package com.functionaldude.paperless_customGPT.rag.internal

import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_SOURCE
import com.functionaldude.paperless.jooq.public.tables.references.DOCUMENTS_CORRESPONDENT
import com.functionaldude.paperless.jooq.public.tables.references.DOCUMENTS_DOCUMENT
import com.functionaldude.paperless_customGPT.rag.api.IngestStatus
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
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun processCandidate(candidate: IngestionCandidate) {
    log.info("Ingesting document ${candidate.paperlessDocId} - ${candidate.title} - ${candidate.correspondentName}")

    createDocumentSource(candidate)
    createRagEntriesForDocument(candidate.paperlessDocId)

    markIngestionComplete(candidate.paperlessDocId)

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
      )
      .from(DOCUMENTS_DOCUMENT)
      .leftJoin(DOCUMENTS_CORRESPONDENT).on(DOCUMENTS_DOCUMENT.CORRESPONDENT_ID.eq(DOCUMENTS_CORRESPONDENT.ID))
      .leftJoin(DOCUMENT_SOURCE).on(DOCUMENTS_DOCUMENT.ID.eq(DOCUMENT_SOURCE.PAPERLESS_DOC_ID))
      .where(
        DOCUMENT_SOURCE.PAPERLESS_DOC_ID.isNull
          .or(DOCUMENTS_DOCUMENT.MODIFIED.gt(DOCUMENT_SOURCE.LAST_INGESTED_AT))
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
    dsl.newRecord(DOCUMENT_SOURCE).apply {
      paperlessDocId = candidate.paperlessDocId
      status = IngestStatus.RUNNING.name

      title = candidate.title
      correspondent = candidate.correspondentName
      docDate = candidate.createdAt
      paperlessModifiedAt = candidate.modifiedAt

      lastIngestedAt = null
      updatedAt = OffsetDateTime.now()

      store()
    }
  }

  private fun markIngestionComplete(paperlessDocId: Int) {
    dsl
      .update(DOCUMENT_SOURCE)
      .set(DOCUMENT_SOURCE.STATUS, IngestStatus.DONE.name)
      .set(DOCUMENT_SOURCE.LAST_INGESTED_AT, OffsetDateTime.now())
      .set(DOCUMENT_SOURCE.UPDATED_AT, OffsetDateTime.now())
      .where(DOCUMENT_SOURCE.PAPERLESS_DOC_ID.eq(paperlessDocId))
      .execute()
  }

  private fun createRagEntriesForDocument(documentId: Int) {
    Thread.sleep(1000)
  }
}