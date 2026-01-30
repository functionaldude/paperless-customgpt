package com.functionaldude.paperless_customGPT.documents.api

import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/documents")
@Tag(
  name = "Documents",
  description = "Paperless document browsing endpoints for the agent to retrieve raw document data."
)
class DocumentController(
  private val paperlessDocumentService: PaperlessDocumentService
) {
  @Operation(
    summary = "List available documents",
    description = "Returns every Paperless PDF document together with metadata and extracted content.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Documents successfully retrieved.",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = DocumentDto::class))
          )
        ]
      )
    ]
  )
  @GetMapping("all")
  fun listDocuments(): List<DocumentDto> {
    return paperlessDocumentService.findAllDocuments()
  }

  @Operation(
    summary = "Fetch a document by id",
    description = "Looks up the Paperless document for the supplied identifier and returns its metadata and content.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Document was found and returned.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = DocumentDto::class))]
      ),
      ApiResponse(
        responseCode = "400",
        description = "The supplied id was not numeric."
      ),
      ApiResponse(
        responseCode = "404",
        description = "No document exists for the requested id."
      )
    ]
  )
  @GetMapping("{id}")
  fun findDocumentById(
    @Parameter(
      description = "Numeric Paperless document id.",
      example = "1234"
    )
    @PathVariable id: String
  ): DocumentDto {
    val documentId = id.toIntOrNull()
      ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Document id must be a number")

    return paperlessDocumentService.findDocumentById(documentId)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")
  }
}
