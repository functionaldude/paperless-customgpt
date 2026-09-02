package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.*
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.LocalDate
import java.util.*

class PaperlessMcpToolsTest {
  private val documentService = mock(PaperlessDocumentService::class.java)
  private val ragQueryService = mock(RagQueryService::class.java)
  private val binaryService = mock(PaperlessDocumentBinaryService::class.java)
  private val tools = PaperlessMcpTools(documentService, ragQueryService, binaryService)

  @Test
  fun `get raw documents returns each binary with its stored MIME type`() {
    val document = "PK\\u0003\\u0004office document".toByteArray()
    val secondDocument = "second document".toByteArray()
    `when`(binaryService.findDocument(262)).thenReturn(
      BinaryDocument(
        document,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "Quarterly report #1 (final).docx",
      )
    )
    `when`(binaryService.findDocument(263)).thenReturn(
      BinaryDocument(
        secondDocument,
        "image/png",
        "scan überblick.png"
      )
    )

    val result = tools.getRawDocuments(listOf(262, 263))

    assertThat(result.content()).hasSize(2)
    val firstContent = (result.content()[0] as EmbeddedResource).resource() as BlobResourceContents
    assertThat(firstContent.uri()).isEqualTo("paperless://documents/262/262-Quarterly%20report%20%231%20(final).docx")
    assertThat(firstContent.mimeType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    assertThat(Base64.getDecoder().decode(firstContent.blob())).isEqualTo(document)

    val secondContent = (result.content()[1] as EmbeddedResource).resource() as BlobResourceContents
    assertThat(secondContent.uri()).isEqualTo("paperless://documents/263/263-scan%20%C3%BCberblick.png")
    assertThat(secondContent.mimeType()).isEqualTo("image/png")
    assertThat(Base64.getDecoder().decode(secondContent.blob())).isEqualTo(secondDocument)
  }

  @Test
  fun `get raw document rejects missing ids`() {
    `when`(binaryService.findDocument(404)).thenReturn(null)

    assertThatThrownBy { tools.getRawDocuments(listOf(404)) }
      .hasMessageContaining("Document not found")
  }

  @Test
  fun `find documents returns available documents for each requested id`() {
    val firstDocument = document(262, "First document")
    val secondDocument = document(263, "Second document")
    `when`(documentService.findDocumentById(262)).thenReturn(firstDocument)
    `when`(documentService.findDocumentById(263)).thenReturn(secondDocument)

    val result = tools.findDocumentsByIds(listOf(262, 404, 263))

    assertThat(result.documents).containsExactly(firstDocument, secondDocument)
  }

  @Test
  fun `find documents rejects requests with no matching ids`() {
    `when`(documentService.findDocumentById(404)).thenReturn(null)

    assertThatThrownBy { tools.findDocumentsByIds(listOf(404)) }
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

  @Test
  fun `find documents by document type normalizes input and wraps results`() {
    val matchingDocument = document(262, "Invoice")
    val fromDate = LocalDate.parse("2026-01-01")
    val toDate = LocalDate.parse("2026-01-31")
    `when`(documentService.findDocumentsByDocumentType("Invoice", fromDate, toDate))
      .thenReturn(listOf(matchingDocument))

    val result = tools.findDocumentsByDocumentType(
      documentTypeName = "  Invoice  ",
      fromDate = "2026-01-01",
      toDate = "2026-01-31",
    )

    assertThat(result.documents).containsExactly(matchingDocument)
    verify(documentService).findDocumentsByDocumentType("Invoice", fromDate, toDate)
  }

  @Test
  fun `find documents by document type rejects invalid input`() {
    assertThatThrownBy { tools.findDocumentsByDocumentType("  ") }
      .hasMessageContaining("Document type name must not be blank")
    assertThatThrownBy { tools.findDocumentsByDocumentType("Invoice", fromDate = "01-01-2026") }
      .hasMessageContaining("fromDate must use YYYY-MM-DD format")
    assertThatThrownBy {
      tools.findDocumentsByDocumentType(
        documentTypeName = "Invoice",
        fromDate = "2026-02-01",
        toDate = "2026-01-31",
      )
    }.hasMessageContaining("fromDate must not be after toDate")
  }

  private fun document(id: Int, title: String) = DocumentDto(
    id = id,
    title = title,
    documentDate = LocalDate.parse("2026-01-01"),
    modifiedAt = null,
    mimeType = "application/pdf",
    content = "full text",
    ownerUsername = null,
    note = null,
    correspondentName = null,
    documentType = null,
    tags = null,
    sourceUrl = "https://paperless.example/documents/$id",
  )
}
