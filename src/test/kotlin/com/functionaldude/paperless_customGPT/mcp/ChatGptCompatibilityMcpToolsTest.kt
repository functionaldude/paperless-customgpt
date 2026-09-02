package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import com.functionaldude.paperless_customGPT.rag.RagSearchResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDate

class ChatGptCompatibilityMcpToolsTest {
  private val documentService = mock(PaperlessDocumentService::class.java)
  private val ragQueryService = mock(RagQueryService::class.java)
  private val tools = ChatGptCompatibilityMcpTools(documentService, ragQueryService)

  @Test
  fun `search maps RAG results to the ChatGPT compatibility schema`() {
    `when`(ragQueryService.findDocumentsSimilarTo("invoice", 10)).thenReturn(
      listOf(
        RagSearchResult(
          262,
          null,
          "A1",
          "snippet",
          0.8,
          "https://paperless.example/documents/262",
        ),
      )
    )

    val response = tools.search(" invoice ")

    assertThat(response.results).containsExactly(
      ChatGptSearchResult("262", "(untitled)", "https://paperless.example/documents/262"),
    )
  }

  @Test
  fun `blank search returns no results without querying RAG`() {
    assertThat(tools.search("  ").results).isEmpty()
  }

  @Test
  fun `fetch maps the existing document model to the ChatGPT compatibility schema`() {
    `when`(documentService.findDocumentById(262)).thenReturn(document())

    val response = tools.fetch("262")

    assertThat(response.id).isEqualTo("262")
    assertThat(response.title).isEqualTo("Invoice")
    assertThat(response.text).isEqualTo("full text")
    assertThat(response.url).isEqualTo("https://paperless.example/documents/262")
    assertThat(response.metadata.documentDate).isEqualTo(LocalDate.parse("2026-01-01"))
    assertThat(response.metadata.correspondentName).isEqualTo("A1")
    assertThat(response.metadata.tags).containsExactly("invoice")
  }

  @Test
  fun `fetch returns empty text for a document without OCR content`() {
    `when`(documentService.findDocumentById(262)).thenReturn(document(content = "", mimeType = "image/jpeg"))

    val response = tools.fetch("262")

    assertThat(response.text).isEmpty()
    assertThat(response.metadata.mimeType).isEqualTo("image/jpeg")
  }

  @Test
  fun `fetch rejects invalid and missing document ids`() {
    assertThatThrownBy { tools.fetch("not-a-number") }
      .hasMessageContaining("Document id must be a number")

    `when`(documentService.findDocumentById(404)).thenReturn(null)

    assertThatThrownBy { tools.fetch("404") }
      .hasMessageContaining("Document not found")
  }

  private fun document(content: String = "full text", mimeType: String = "application/pdf") = DocumentDto(
    id = 262,
    title = "Invoice",
    documentDate = LocalDate.parse("2026-01-01"),
    modifiedAt = null,
    mimeType = mimeType,
    content = content,
    ownerUsername = null,
    note = null,
    correspondentName = "A1",
    tags = listOf("invoice"),
    sourceUrl = "https://paperless.example/documents/262",
  )
}
