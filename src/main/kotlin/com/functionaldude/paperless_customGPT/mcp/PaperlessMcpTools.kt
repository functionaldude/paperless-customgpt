package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.agent.AgentOperationText
import com.functionaldude.paperless_customGPT.agent.AgentOperationsService
import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.rag.RagQueryResponse
import com.functionaldude.paperless_customGPT.security.CurrentUserService
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class PaperlessMcpTools(
  private val agentOperationsService: AgentOperationsService,
  private val currentUserService: CurrentUserService,
) {
  @McpTool(
    name = "listDocuments",
    description = AgentOperationText.LIST_DOCUMENTS_DESCRIPTION
  )
  fun listDocuments(): List<DocumentDto> {
    currentUserService.requireJwtAuthentication()
    return agentOperationsService.listDocuments()
  }

  @McpTool(
    name = "findDocumentById",
    description = AgentOperationText.FIND_DOCUMENT_BY_ID_DESCRIPTION
  )
  fun findDocumentById(
    @McpToolParam(description = AgentOperationText.DOCUMENT_ID_DESCRIPTION)
    id: String
  ): DocumentDto {
    currentUserService.requireJwtAuthentication()
    return agentOperationsService.findDocumentById(id)
  }

  @McpTool(
    name = "searchRag",
    description = AgentOperationText.RAG_SEARCH_DESCRIPTION
  )
  fun searchRag(
    @McpToolParam(description = AgentOperationText.RAG_QUERY_DESCRIPTION)
    query: String,
    @McpToolParam(description = AgentOperationText.RAG_TOP_K_DESCRIPTION)
    topK: Int? = null
  ): RagQueryResponse {
    currentUserService.requireJwtAuthentication()
    return agentOperationsService.searchRag(query, topK)
  }
}
