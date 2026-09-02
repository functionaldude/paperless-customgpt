package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.BinaryDocument
import com.functionaldude.paperless_customGPT.documents.DocumentList
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentBinaryService
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.*

class PaperlessMcpToolsTest {
  private val documentService = mock(PaperlessDocumentService::class.java)
  private val ragQueryService = mock(RagQueryService::class.java)
  private val binaryService = mock(PaperlessDocumentBinaryService::class.java)
  private val tools = PaperlessMcpTools(documentService, ragQueryService, binaryService)

  @Test
  fun `get raw document returns a non PDF binary with its stored MIME type`() {
    val document = "PK\\u0003\\u0004office document".toByteArray()
    `when`(binaryService.findDocument(262)).thenReturn(
      BinaryDocument(document, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    )

    val result = tools.getRawDocument("262")

    assertThat(result.content()).hasSize(1)
    val embeddedResource = result.content().single() as EmbeddedResource
    val content = embeddedResource.resource() as BlobResourceContents
    assertThat(content.uri()).isEqualTo("paperless://documents/262/content")
    assertThat(content.mimeType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    assertThat(Base64.getDecoder().decode(content.blob())).isEqualTo(document)
  }

  @Test
  fun `get raw document rejects invalid and missing ids`() {
    assertThatThrownBy { tools.getRawDocument("invalid") }
      .hasMessageContaining("Document id must be a number")

    `when`(binaryService.findDocument(404)).thenReturn(null)

    assertThatThrownBy { tools.getRawDocument("404") }
      .hasMessageContaining("Document not found")
  }

  @Test
  fun `list documents applies its default page size and clamps oversized pages`() {
    `when`(documentService.findDocumentsPage(50, 0)).thenReturn(DocumentList(emptyList()))
    `when`(documentService.findDocumentsPage(100, 7)).thenReturn(DocumentList(emptyList()))

    tools.listDocuments()
    tools.listDocuments(limit = 101, offset = 7)

    verify(documentService).findDocumentsPage(50, 0)
    verify(documentService).findDocumentsPage(100, 7)
  }

  @Test
  fun `list documents rejects invalid pagination values`() {
    assertThatThrownBy { tools.listDocuments(limit = 0) }
      .hasMessageContaining("limit must be greater than zero")
    assertThatThrownBy { tools.listDocuments(offset = -1) }
      .hasMessageContaining("offset must not be negative")
  }
}
