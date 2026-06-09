package com.functionaldude.paperless_customGPT.rag

import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_CHUNK
import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_SOURCE
import com.functionaldude.paperless_customGPT.documents.PaperlessUrlProvider
import com.functionaldude.paperless_customGPT.rag.api.IngestStatus
import com.functionaldude.paperless_customGPT.rag.internal.EmbeddingDimensionReducer
import dev.langchain4j.model.embedding.EmbeddingModel
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RagQueryService(
  private val dsl: DSLContext,
  private val embeddingModel: EmbeddingModel,
  private val embeddingDimensionReducer: EmbeddingDimensionReducer,
  private val paperlessUrlProvider: PaperlessUrlProvider,
  @Value("\${rag.hnsw-ef-search:400}") private val hnswEfSearch: Int,
) {
  init {
    require(hnswEfSearch > 0) { "rag.hnsw-ef-search must be > 0, but was $hnswEfSearch" }
  }

  @Transactional(readOnly = true)
  fun findDocumentsSimilarTo(query: String, topK: Int): List<RagSearchResult> {
    // hnsw.ef_search controls how many graph candidates HNSW explores per query:
    // higher values usually improve recall (our priority) at the cost of slower search.
    // pgvector exposes it as a session setting, so we use SET LOCAL to scope it to this
    // transaction and avoid leaking the tuned value across pooled DB connections.
    dsl.execute("SET LOCAL hnsw.ef_search = $hnswEfSearch")

    val queryEmbedding = embeddingDimensionReducer.reduce(embeddingModel.embed(query).content().vector())

    // jooq doesn’t know the <-> operator
    val similarityField = DSL.field(
      "({0} <-> {1})",
      Double::class.java,
      DOCUMENT_CHUNK.EMBEDDING,
      DSL.`val`(queryEmbedding, DOCUMENT_CHUNK.EMBEDDING.dataType)
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
          sourceUrl = paperlessUrlProvider.documentUrl(record.get(DOCUMENT_SOURCE.PAPERLESS_DOC_ID)!!),
        )
      }

    return records
  }
}
