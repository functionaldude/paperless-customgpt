package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.RagQueryResponse
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class PaperlessMcpTools(
  private val paperlessDocumentService: PaperlessDocumentService,
  private val ragQueryService: RagQueryService,
) {
  companion object {
    private const val DEFAULT_TOP_K = 5
    private const val MAX_TOP_K = 20
  }

  @McpTool(
    name = "listDocuments",
    description = "Returns all Paperless PDF documents with metadata and full extracted content."
  )
  fun listDocuments(): List<DocumentDto> {
    return paperlessDocumentService.findAllDocuments()
  }

  @McpTool(
    name = "findDocumentById",
    description = "Fetches one Paperless PDF document by id and returns metadata plus extracted content."
  )
  fun findDocumentById(
    @McpToolParam(description = "Numeric Paperless document id.")
    id: String
  ): DocumentDto {
    val documentId = id.toIntOrNull()
      ?: throw IllegalArgumentException("Document id must be a number")

    return paperlessDocumentService.findDocumentById(documentId)
      ?: throw IllegalArgumentException("Document not found")
  }

  @McpTool(
    name = "searchRag",
    description = "Runs pgvector semantic search against ingested Paperless documents."
  )
  fun searchRag(
    @McpToolParam(description = "Natural language query for semantic search.")
    query: String,
    @McpToolParam(description = "Optional number of results to return. Values over 20 are clamped.")
    topK: Int? = null
  ): RagQueryResponse {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) {
      throw IllegalArgumentException("Query must not be blank")
    }

    val requestedTopK = topK?.takeIf { it > 0 } ?: DEFAULT_TOP_K
    val effectiveTopK = requestedTopK.coerceAtMost(MAX_TOP_K)

    val results = ragQueryService.findDocumentsSimilarTo(trimmedQuery, effectiveTopK)
    return RagQueryResponse(results)
  }
}
