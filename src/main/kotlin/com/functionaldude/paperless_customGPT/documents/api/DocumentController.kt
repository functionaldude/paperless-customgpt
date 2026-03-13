package com.functionaldude.paperless_customGPT.documents.api

import com.functionaldude.paperless_customGPT.OpenAiNonConsequential
import com.functionaldude.paperless_customGPT.agent.AgentOperationText
import com.functionaldude.paperless_customGPT.agent.AgentOperationsService
import com.functionaldude.paperless_customGPT.documents.DocumentDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/documents")
@Tag(
  name = "Documents",
  description = AgentOperationText.DOCUMENTS_TAG_DESCRIPTION
)
class DocumentController(
  private val agentOperationsService: AgentOperationsService
) {
  @Operation(
    summary = AgentOperationText.LIST_DOCUMENTS_SUMMARY,
    description = AgentOperationText.LIST_DOCUMENTS_DESCRIPTION,
    responses = [
      ApiResponse(
        responseCode = "200",
        description = AgentOperationText.LIST_DOCUMENTS_RESPONSE_DESCRIPTION,
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = DocumentDto::class))
          )
        ]
      )
    ]
  )
  @OpenAiNonConsequential
  @GetMapping("all")
  fun listDocuments(): List<DocumentDto> {
    return agentOperationsService.listDocuments()
  }

  @Operation(
    summary = AgentOperationText.FIND_DOCUMENT_BY_ID_SUMMARY,
    description = AgentOperationText.FIND_DOCUMENT_BY_ID_DESCRIPTION,
    responses = [
      ApiResponse(
        responseCode = "200",
        description = AgentOperationText.FIND_DOCUMENT_BY_ID_RESPONSE_DESCRIPTION,
        content = [Content(mediaType = "application/json", schema = Schema(implementation = DocumentDto::class))]
      ),
      ApiResponse(
        responseCode = "400",
        description = AgentOperationText.INVALID_DOCUMENT_ID_RESPONSE_DESCRIPTION
      ),
      ApiResponse(
        responseCode = "404",
        description = AgentOperationText.DOCUMENT_NOT_FOUND_RESPONSE_DESCRIPTION
      )
    ]
  )
  @OpenAiNonConsequential
  @GetMapping("{id}")
  fun findDocumentById(
    @Parameter(
      description = AgentOperationText.DOCUMENT_ID_DESCRIPTION,
      example = "1234"
    )
    @PathVariable id: String
  ): DocumentDto {
    return agentOperationsService.findDocumentById(id)
  }
}
