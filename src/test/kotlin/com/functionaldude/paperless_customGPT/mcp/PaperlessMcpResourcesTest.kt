package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.BinaryDocument
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentBinaryService
import com.functionaldude.paperless_customGPT.documents.PaperlessResourceUriProvider
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.*

class PaperlessMcpResourcesTest {
  private val binaryService = mock(PaperlessDocumentBinaryService::class.java)
  private val resources = PaperlessMcpResources(binaryService, PaperlessResourceUriProvider())

  @Test
  fun `document resource returns one base64 encoded binary content item`() {
    val pdf = "%PDF-test".toByteArray()
    `when`(binaryService.findDocument(262)).thenReturn(BinaryDocument(pdf, "application/pdf"))

    val result = resources.readDocument("262")

    assertThat(result.contents()).hasSize(1)
    val content = result.contents().single() as BlobResourceContents
    assertThat(content.uri()).isEqualTo("paperless://documents/262/content")
    assertThat(content.mimeType()).isEqualTo("application/pdf")
    assertThat(Base64.getDecoder().decode(content.blob())).isEqualTo(pdf)
  }

  @Test
  fun `document resource reports invalid and missing ids as not found`() {
    assertThatThrownBy { resources.readDocument("invalid") }
      .isInstanceOf(McpError::class.java)
      .hasMessage("Resource not found")

    `when`(binaryService.findDocument(404)).thenReturn(null)

    assertThatThrownBy { resources.readDocument("404") }
      .isInstanceOf(McpError::class.java)
      .hasMessage("Resource not found")
  }
}
