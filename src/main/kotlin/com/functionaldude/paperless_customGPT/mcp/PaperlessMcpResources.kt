package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentBinaryService
import com.functionaldude.paperless_customGPT.documents.PaperlessResourceUriProvider
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema.*
import org.springaicommunity.mcp.annotation.McpResource
import org.springframework.stereotype.Component
import java.util.*

@Component
class PaperlessMcpResources(
  private val binaryService: PaperlessDocumentBinaryService,
  private val resourceUriProvider: PaperlessResourceUriProvider,
) {
  @McpResource(
    name = "paperless-document",
    title = "Paperless document",
    uri = "paperless://documents/{id}/content",
    description = "The original visual Paperless document. Read this binary resource when visual layout, scans, handwriting, tables, or OCR accuracy matter.",
    mimeType = "application/pdf",
    annotations = McpResource.McpAnnotations(
      audience = [Role.USER, Role.ASSISTANT],
      priority = 1.0,
    ),
  )
  fun readDocument(id: String): ReadResourceResult {
    val resourceUri = id.toIntOrNull()?.let(resourceUriProvider::documentResourceUri)
      ?: throw McpError.RESOURCE_NOT_FOUND.apply("paperless://documents/$id/content")
    val document = binaryService.findDocument(id.toInt())
      ?: throw McpError.RESOURCE_NOT_FOUND.apply(resourceUri)

    return ReadResourceResult(
      listOf(
        BlobResourceContents(
          resourceUri,
          document.mimeType,
          Base64.getEncoder().encodeToString(document.content),
        )
      )
    )
  }
}
