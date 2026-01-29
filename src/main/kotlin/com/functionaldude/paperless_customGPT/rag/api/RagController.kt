package com.functionaldude.paperless_customGPT.rag.api

import com.functionaldude.paperless_customGPT.rag.internal.RagQueryService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class RagQueryRequest(
  val query: String,
  val topK: Int? = null,
)

data class RagQueryResponse(
  val results: List<RagSearchResult>
)

@RestController
@RequestMapping("/api/rag")
class RagController(
  private val ragService: RagQueryService,
) {
  companion object {
    private const val DEFAULT_TOP_K = 5
    private const val MAX_TOP_K = 20
  }

  @PostMapping("search")
  fun searchRag(@RequestBody request: RagQueryRequest): RagQueryResponse {
    val query = request.query.trim()
    if (query.isEmpty()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must not be blank")
    }

    val requestedTopK = request.topK?.takeIf { it > 0 } ?: DEFAULT_TOP_K
    val effectiveTopK = requestedTopK.coerceAtMost(MAX_TOP_K)

    val results = ragService.findDocumentsSimilarTo(query, effectiveTopK)
    return RagQueryResponse(results)
  }
}
