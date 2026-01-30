package com.functionaldude.paperless_customGPT.rag.internal

import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_CHUNK
import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_SOURCE
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.api.IngestStatus
import com.functionaldude.paperless_customGPT.rag.api.RagSearchResult
import com.functionaldude.paperless_customGPT.toDoubleArray
import com.functionaldude.paperless_customGPT.toPgVectorLiteral
import dev.langchain4j.model.embedding.EmbeddingModel
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

@Service
class RagQueryService(
  private val dsl: DSLContext,
  private val paperlessDocumentService: PaperlessDocumentService,
  private val embeddingModel: EmbeddingModel,
) {
  fun findDocumentsSimilarTo(query: String, topK: Int): List<RagSearchResult> {
    val queryEmbedding = embeddingModel.embed(query).content()

    // jooq doesn’t know the <-> operator
    val similarityField = DSL.field(
      "({0} <-> {1})",
      Double::class.java,
      DOCUMENT_CHUNK.EMBEDDING,
      DSL.`val`(queryEmbedding.vector(), DOCUMENT_CHUNK.EMBEDDING.dataType)
    )

    val records = dsl
      .select(
        DOCUMENT_CHUNK.CHUNK_INDEX,
        DOCUMENT_CHUNK.CONTENT,
        DOCUMENT_CHUNK.METADATA,
        similarityField.`as`("score"),
        DOCUMENT_SOURCE.PAPERLESS_DOC_ID,
        DOCUMENT_SOURCE.TITLE,
        DOCUMENT_SOURCE.CORRESPONDENT,
      )
      .from(DOCUMENT_CHUNK)
      .join(DOCUMENT_SOURCE).on(DOCUMENT_CHUNK.DOCUMENT_SOURCE_ID.eq(DOCUMENT_SOURCE.PAPERLESS_DOC_ID))
      .where(DOCUMENT_SOURCE.STATUS.eq(IngestStatus.DONE.name))
      .orderBy(DSL.field("score").asc())
      .limit(topK)
      .fetch { record ->
        RagSearchResult(
          paperlessDocId = record.get(DOCUMENT_SOURCE.PAPERLESS_DOC_ID)!!,
          title = record.get(DOCUMENT_SOURCE.TITLE),
          correspondentName = record.get(DOCUMENT_SOURCE.CORRESPONDENT),
          snippet = record.get(DOCUMENT_CHUNK.CONTENT)!!,
          score = record.get("score", Double::class.java)!!,
        )
      }

    return records
  }
}