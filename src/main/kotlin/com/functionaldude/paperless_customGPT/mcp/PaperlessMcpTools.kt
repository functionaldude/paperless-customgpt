package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.RagQueryResponse
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class PaperlessMcpTools(
  private val paperlessDocumentService: PaperlessDocumentService,
  private val ragQueryService: RagQueryService,
) {
  @McpTool(
    name = "listDocuments",
    description = "Returns every Paperless PDF document together with metadata and extracted content.",
    generateOutputSchema = false, // Somehow this errors on MCPJam but probably not a big deal
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun listDocuments(): List<DocumentDto> {
    return paperlessDocumentService.findAllDocuments()
  }

  @McpTool(
    name = "findDocumentById",
    description = "Looks up the Paperless document for the supplied identifier and returns its metadata and content.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun findDocumentById(
    @McpToolParam(description = "Numeric Paperless document id.")
    id: String
  ): DocumentDto {
    val documentId = id.toIntOrNull()
      ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Document id must be a number")

    return paperlessDocumentService.findDocumentById(documentId)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")
  }

  @McpTool(
    name = "searchRag",
    description = "Uses pgvector similarity search to retrieve the most relevant Paperless documents for the provided question.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun searchRag(
    @McpToolParam(description = "Natural language prompt used to search previously ingested Paperless documents.")
    query: String,
    @McpToolParam(description = "Optional number of top results to return. Values over 50 are clamped.")
    topK: Int? = null
  ): RagQueryResponse {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must not be blank")
    }

    val requestedTopK = topK?.takeIf { it > 0 } ?: DEFAULT_TOP_K
    val effectiveTopK = requestedTopK.coerceAtMost(MAX_TOP_K)
    val results = ragQueryService.findDocumentsSimilarTo(normalizedQuery, effectiveTopK)
    return RagQueryResponse(results)
  }

  companion object {
    const val DEFAULT_TOP_K = 5
    const val MAX_TOP_K = 50
  }
}
