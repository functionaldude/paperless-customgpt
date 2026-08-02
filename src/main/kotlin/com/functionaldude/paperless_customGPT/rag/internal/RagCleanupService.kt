package com.functionaldude.paperless_customGPT.rag.internal

import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_SOURCE
import com.functionaldude.paperless.jooq.public.tables.references.DOCUMENTS_DOCUMENT
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RagCleanupService(
  private val dsl: DSLContext,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun findDeletedPaperlessDocIds(limit: Int): List<Int> {
    return dsl
      .select(DOCUMENT_SOURCE.PAPERLESS_DOC_ID)
      .from(DOCUMENT_SOURCE)
      .leftJoin(DOCUMENTS_DOCUMENT).on(
        DOCUMENT_SOURCE.PAPERLESS_DOC_ID.eq(DOCUMENTS_DOCUMENT.ID)
          .and(DOCUMENTS_DOCUMENT.ROOT_DOCUMENT_ID.isNull)
          .and(DOCUMENTS_DOCUMENT.DELETED_AT.isNull)
      )
      .where(DOCUMENTS_DOCUMENT.ID.isNull)
      .orderBy(DOCUMENT_SOURCE.UPDATED_AT.asc())
      .limit(limit)
      .fetch { it.get(DOCUMENT_SOURCE.PAPERLESS_DOC_ID)!! }
  }

  @Transactional
  fun cleanupDocument(paperlessDocId: Int) {
    log.info("Removing RAG data for deleted paperlessDocId=$paperlessDocId")

    // No need to delete chunks explicitly here, ON DELETE CASCADE will take care of it

    dsl
      .deleteFrom(DOCUMENT_SOURCE)
      .where(DOCUMENT_SOURCE.PAPERLESS_DOC_ID.eq(paperlessDocId))
      .execute()
  }
}
