package com.functionaldude.paperless_customGPT.agent

import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.RagQueryResponse
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AgentOperationsService(
  private val paperlessDocumentService: PaperlessDocumentService,
  private val ragQueryService: RagQueryService,
) {
  companion object {
    const val DEFAULT_TOP_K = 5
    const val MAX_TOP_K = 50
  }

  fun listDocuments(): List<DocumentDto> {
    return paperlessDocumentService.findAllDocuments()
  }

  fun findDocumentById(id: String): DocumentDto {
    val documentId = id.toIntOrNull()
      ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, AgentOperationText.INVALID_DOCUMENT_ID_MESSAGE)

    return paperlessDocumentService.findDocumentById(documentId)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, AgentOperationText.DOCUMENT_NOT_FOUND_MESSAGE)
  }

  fun searchRag(query: String, topK: Int?): RagQueryResponse {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, AgentOperationText.RAG_BLANK_QUERY_MESSAGE)
    }

    val requestedTopK = topK?.takeIf { it > 0 } ?: DEFAULT_TOP_K
    val effectiveTopK = requestedTopK.coerceAtMost(MAX_TOP_K)
    val results = ragQueryService.findDocumentsSimilarTo(normalizedQuery, effectiveTopK)
    return RagQueryResponse(results)
  }
}
