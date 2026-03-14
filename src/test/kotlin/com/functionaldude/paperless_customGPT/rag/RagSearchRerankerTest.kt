package com.functionaldude.paperless_customGPT.rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.output.Response
import dev.langchain4j.model.scoring.ScoringModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory

class RagSearchRerankerTest {

  @Test
  fun `reranks vector hits using scoring model output`() {
    val reranker = rerankerWith(listOf(0.12, 0.83, 0.98))

    val results = reranker.rerank("which invoice is the most recent?", candidates(), 2)

    assertThat(results.map { it.paperlessDocId }).containsExactly(3, 2)
    assertThat(results.map { it.score }).containsExactly(0.98, 0.83)
  }

  @Test
  fun `falls back to vector order when scoring model fails`() {
    val reranker = rerankerWith(error = IllegalStateException("boom"))

    val results = reranker.rerank("which invoice is the most recent?", candidates(), 2)

    assertThat(results.map { it.paperlessDocId }).containsExactly(1, 2)
    assertThat(results.map { it.score }).containsExactly(0.91, 0.84)
  }

  @Test
  fun `does not oversample or rerank when no scoring model is configured`() {
    val reranker = rerankerWith(scores = null)

    assertThat(reranker.candidateCount(3)).isEqualTo(3)
    assertThat(reranker.rerank("query", candidates(), 2).map { it.paperlessDocId }).containsExactly(1, 2)
  }

  private fun rerankerWith(
    scores: List<Double>? = emptyList(),
    enabled: Boolean = true,
    error: RuntimeException? = null,
  ): RagSearchReranker {
    val beanFactory = if (scores == null) {
      StaticListableBeanFactory()
    } else {
      StaticListableBeanFactory(mapOf("ragRerankerScoringModel" to StubScoringModel(scores, error)))
    }

    return RagSearchReranker(
      beanFactory.getBeanProvider(ScoringModel::class.java),
      enabled = enabled,
      candidateMultiplier = 4,
      maxCandidates = 40,
    )
  }

  private fun candidates() = listOf(
    RagSearchResult(
      paperlessDocId = 1,
      title = "January invoice",
      correspondentName = "ACME",
      snippet = "Invoice dated January 3 for hosting services.",
      score = 0.91,
      sourceUrl = "https://paperless.example/documents/1",
    ),
    RagSearchResult(
      paperlessDocId = 2,
      title = "February invoice",
      correspondentName = "ACME",
      snippet = "Invoice dated February 14 for support services.",
      score = 0.84,
      sourceUrl = "https://paperless.example/documents/2",
    ),
    RagSearchResult(
      paperlessDocId = 3,
      title = "March invoice",
      correspondentName = "ACME",
      snippet = "Invoice dated March 6 for platform services.",
      score = 0.79,
      sourceUrl = "https://paperless.example/documents/3",
    ),
  )

  private class StubScoringModel(
    private val scores: List<Double>,
    private val error: RuntimeException?,
  ) : ScoringModel {
    override fun scoreAll(segments: List<TextSegment>, query: String): Response<List<Double>> {
      if (error != null) {
        throw error
      }
      return Response.from(scores)
    }
  }
}
