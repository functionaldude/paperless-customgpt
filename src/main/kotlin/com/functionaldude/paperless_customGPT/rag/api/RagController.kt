package com.functionaldude.paperless_customGPT.rag.api

import com.functionaldude.paperless_customGPT.OpenAiNonConsequential
import com.functionaldude.paperless_customGPT.agent.AgentOperationText
import com.functionaldude.paperless_customGPT.agent.AgentOperationsService
import com.functionaldude.paperless_customGPT.agent.AgentOperationsService.Companion.DEFAULT_TOP_K
import com.functionaldude.paperless_customGPT.agent.AgentOperationsService.Companion.MAX_TOP_K
import com.functionaldude.paperless_customGPT.rag.RagQueryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as AnnotationRequestBody

@Schema(description = AgentOperationText.RAG_QUERY_REQUEST_SCHEMA_DESCRIPTION)
data class RagQueryRequest(
  @field:Schema(
    description = AgentOperationText.RAG_QUERY_DESCRIPTION,
    example = AgentOperationText.RAG_QUERY_EXAMPLE
  )
  val query: String,
  @field:Schema(
    description = AgentOperationText.RAG_TOP_K_DESCRIPTION,
    minimum = "1",
    maximum = MAX_TOP_K.toString(),
    example = DEFAULT_TOP_K.toString(),
    nullable = true
  )
  val topK: Int? = null,
)

@RestController
@RequestMapping("/api/rag")
@Tag(
  name = "RAG",
  description = AgentOperationText.RAG_TAG_DESCRIPTION
)
class RagController(
  private val agentOperationsService: AgentOperationsService,
) {

  @Operation(
    summary = AgentOperationText.RAG_SEARCH_SUMMARY,
    description = AgentOperationText.RAG_SEARCH_DESCRIPTION,
    requestBody = AnnotationRequestBody(
      required = true,
      description = AgentOperationText.RAG_SEARCH_REQUEST_BODY_DESCRIPTION,
      content = [Content(mediaType = "application/json", schema = Schema(implementation = RagQueryRequest::class))]
    ),
    responses = [
      ApiResponse(
        responseCode = "200",
        description = AgentOperationText.RAG_SEARCH_RESPONSE_DESCRIPTION,
        content = [Content(mediaType = "application/json", schema = Schema(implementation = RagQueryResponse::class))]
      ),
      ApiResponse(
        responseCode = "400",
        description = AgentOperationText.RAG_BLANK_QUERY_RESPONSE_DESCRIPTION
      )
    ]
  )
  @OpenAiNonConsequential
  @PostMapping("search")
  fun searchRag(@RequestBody request: RagQueryRequest): RagQueryResponse {
    return agentOperationsService.searchRag(request.query, request.topK)
  }
}
