package com.functionaldude.paperless_customGPT.rag.api

import com.functionaldude.paperless_customGPT.rag.internal.RagQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import io.swagger.v3.oas.annotations.parameters.RequestBody as AnnotationRequestBody

@Schema(description = "Parameters for a RAG similarity search.")
data class RagQueryRequest(
  @field:Schema(
    description = "Natural language prompt used to search previously ingested Paperless documents.",
    example = "What is the renewal premium for my car insurance?"
  )
  val query: String,
  @field:Schema(
    description = "Optional number of top results to return. Values over 20 are clamped.",
    minimum = "1",
    maximum = "20",
    example = "5",
    nullable = true
  )
  val topK: Int? = null,
)

@Schema(description = "Wrapper containing RAG search hits.")
data class RagQueryResponse(
  @field:ArraySchema(
    arraySchema = Schema(description = "Ranked snippets most relevant to the question."),
    schema = Schema(implementation = RagSearchResult::class)
  )
  val results: List<RagSearchResult>
)

@RestController
@RequestMapping("/api/rag")
@Tag(
  name = "RAG",
  description = "Retrieval augmented generation APIs that offer semantic search across Paperless documents."
)
class RagController(
  private val ragService: RagQueryService,
) {
  companion object {
    private const val DEFAULT_TOP_K = 5
    private const val MAX_TOP_K = 20
  }

  @Operation(
    summary = "Run a semantic search",
    description = "Uses pgvector similarity search to retrieve the most relevant Paperless documents for the provided question.",
    requestBody = AnnotationRequestBody(
      required = true,
      description = "Search parameters including the natural language query and optional limit for the number of hits.",
      content = [Content(mediaType = "application/json", schema = Schema(implementation = RagQueryRequest::class))]
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Search results were computed successfully.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = RagQueryResponse::class))]
      ),
      ApiResponse(
        responseCode = "400",
        description = "Query text was blank."
      )
    ]
  )
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
