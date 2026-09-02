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
import java.time.LocalDate

@Service
class RagQueryService(
  private val dsl: DSLContext,
  private val embeddingModel: EmbeddingModel,
  private val embeddingDimensionReducer: EmbeddingDimensionReducer,
  private val paperlessUrlProvider: PaperlessUrlProvider,
  @Value("\${rag.hnsw-ef-search:400}") private val hnswEfSearch: Int,
  @Value("\${OPENAI_MODEL_NAME:text-embedding-multilingual-e5-base}") private val embeddingModelName: String,
  @Value("\${rag.qwen3-query-instruction}") private val qwen3QueryInstruction: String,
) {
  init {
    require(hnswEfSearch > 0) { "rag.hnsw-ef-search must be > 0, but was $hnswEfSearch" }
  }

  @Transactional(readOnly = true)
  fun findDocumentsSimilarTo(
    query: String,
    topK: Int,
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
  ): List<RagSearchResult> {
    require(topK > 0) { "topK must be > 0, but was $topK" }

    // hnsw.ef_search controls how many graph candidates HNSW explores per query:
    // higher values usually improve recall (our priority) at the cost of slower search.
    // pgvector exposes it as a session setting, so we use SET LOCAL to scope it to this
    // transaction and avoid leaking the tuned value across pooled DB connections.
    dsl.execute("SET LOCAL hnsw.ef_search = $hnswEfSearch")
    dsl.execute("SET LOCAL hnsw.iterative_scan = strict_order")

    val queryEmbedding = embeddingDimensionReducer.reduce(
      embeddingModel.embed(queryForEmbedding(query)).content().vector()
    )

    // jOOQ does not know the pgvector cosine-distance (<=>) operator. It matches
    // the vector_cosine_ops HNSW index created by the RAG migration.
    val distanceField = DSL.field(
      "({0} <=> {1})",
      Double::class.java,
      DOCUMENT_CHUNK.EMBEDDING,
      DSL.`val`(queryEmbedding, DOCUMENT_CHUNK.EMBEDDING.dataType)
    )

    val conditions = buildList {
      add(DOCUMENT_SOURCE.STATUS.eq(IngestStatus.DONE.name))
      fromDate?.let { add(DOCUMENT_SOURCE.DOC_DATE.ge(it)) }
      toDate?.let { add(DOCUMENT_SOURCE.DOC_DATE.le(it)) }
    }

    var candidateLimit = initialCandidateLimit(topK)
    while (true) {
      val candidates = dsl
        .select(
          DOCUMENT_CHUNK.CHUNK_INDEX,
          DOCUMENT_CHUNK.CONTENT,
          DOCUMENT_CHUNK.METADATA,
          distanceField.`as`("distance"),
          DOCUMENT_SOURCE.PAPERLESS_DOC_ID,
          DOCUMENT_SOURCE.TITLE,
          DOCUMENT_SOURCE.CORRESPONDENT,
        )
        .from(DOCUMENT_CHUNK)
        .join(DOCUMENT_SOURCE).on(DOCUMENT_CHUNK.DOCUMENT_SOURCE_ID.eq(DOCUMENT_SOURCE.PAPERLESS_DOC_ID))
        .where(conditions)
        .orderBy(DSL.field("distance").asc())
        .limit(candidateLimit)
        .fetch { record ->
          RagSearchResult(
            paperlessDocId = record.get(DOCUMENT_SOURCE.PAPERLESS_DOC_ID)!!,
            title = record.get(DOCUMENT_SOURCE.TITLE),
            correspondentName = record.get(DOCUMENT_SOURCE.CORRESPONDENT),
            snippet = record.get(DOCUMENT_CHUNK.CONTENT)!!,
            score = 1.0 - record.get("distance", Double::class.java)!!,
            sourceUrl = paperlessUrlProvider.documentUrl(record.get(DOCUMENT_SOURCE.PAPERLESS_DOC_ID)!!),
          )
        }
      val documents = candidates.distinctBy { it.paperlessDocId }

      if (documents.size >= topK || candidates.size < candidateLimit || candidateLimit >= MAX_CANDIDATES) {
        return documents.take(topK)
      }

      candidateLimit = (candidateLimit * 2).coerceAtMost(MAX_CANDIDATES)
    }
  }

  private fun queryForEmbedding(query: String): String {
    if (!embeddingModelName.contains(QWEN3_EMBEDDING_MODEL_MARKER, ignoreCase = true)) {
      return query
    }

    val instruction = qwen3QueryInstruction.trim().ifEmpty { DEFAULT_QWEN3_QUERY_INSTRUCTION }
    return "Instruct: $instruction\nQuery: $query"
  }

  private fun initialCandidateLimit(topK: Int): Int = maxOf(
    MIN_CANDIDATES,
    (topK.toLong() * INITIAL_CANDIDATE_MULTIPLIER).coerceAtMost(MAX_CANDIDATES.toLong()).toInt(),
  )

  companion object {
    private const val QWEN3_EMBEDDING_MODEL_MARKER = "qwen3-embedding"
    private const val DEFAULT_QWEN3_QUERY_INSTRUCTION =
      "Given a user question, retrieve relevant passages from personal documents that answer the question."
    private const val MIN_CANDIDATES = 50
    private const val INITIAL_CANDIDATE_MULTIPLIER = 10L
    private const val MAX_CANDIDATES = 2_000
  }
}
