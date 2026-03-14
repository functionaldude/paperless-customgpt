package com.functionaldude.paperless_customGPT.rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.scoring.ScoringModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class RagSearchReranker(
  @Qualifier("ragRerankerScoringModel")
  rerankerScoringModelProvider: ObjectProvider<ScoringModel>,
  @Value("\${rag.reranker.enabled:true}") private val enabled: Boolean,
  @Value("\${rag.reranker.candidate-multiplier:4}") private val candidateMultiplier: Int,
  @Value("\${rag.reranker.max-candidates:40}") private val maxCandidates: Int,
) {
  private val logger = LoggerFactory.getLogger(javaClass)
  private val rerankerScoringModel = rerankerScoringModelProvider.ifAvailable

  init {
    require(candidateMultiplier > 0) { "rag.reranker.candidate-multiplier must be > 0, but was $candidateMultiplier" }
    require(maxCandidates > 0) { "rag.reranker.max-candidates must be > 0, but was $maxCandidates" }
  }

  fun candidateCount(topK: Int): Int {
    require(topK > 0) { "topK must be > 0, but was $topK" }
    if (!isEnabled()) {
      return topK
    }

    return (topK * candidateMultiplier)
      .coerceAtLeast(topK)
      .coerceAtMost(maxCandidates)
  }

  fun rerank(query: String, candidates: List<RagSearchResult>, topK: Int): List<RagSearchResult> {
    require(topK > 0) { "topK must be > 0, but was $topK" }
    if (!isEnabled() || candidates.size <= 1) {
      return candidates.take(topK)
    }

    return runCatching {
      val segments = candidates.map { candidate ->
        TextSegment.from(
          buildString {
            appendLine("title: ${candidate.title ?: "(untitled)"}")
            appendLine("correspondent: ${candidate.correspondentName ?: "(unknown)"}")
            append("snippet: ${candidate.snippet}")
          }
        )
      }

      val scores = rerankerScoringModel!!.scoreAll(segments, query).content()
      require(scores.size == candidates.size) {
        "Reranker returned ${scores.size} scores for ${candidates.size} candidates"
      }

      candidates.zip(scores)
        .sortedByDescending { it.second }
        .take(topK)
        .map { (candidate, score) -> candidate.copy(score = score) }
    }.getOrElse { error ->
      logger.warn("Falling back to vector ranking because reranking failed", error)
      candidates.take(topK)
    }
  }

  private fun isEnabled(): Boolean = enabled && rerankerScoringModel != null
}
