package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.*
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Path
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
      BinaryDocumentLookup(
        BinaryDocument(
          document,
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "Quarterly report #1 (final).docx",
        )
      )
    )
    `when`(binaryService.findDocument(263)).thenReturn(
      BinaryDocumentLookup(
        BinaryDocument(
          secondDocument,
          "image/png",
          "scan überblick.png"
        )
      )
    )
    `when`(binaryService.findDocument(404)).thenReturn(BinaryDocumentLookup(null))

    val result = tools.getRawDocuments(listOf(262, 404, 263))

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
    `when`(binaryService.findDocument(404)).thenReturn(BinaryDocumentLookup(null))

    assertThatThrownBy { tools.getRawDocuments(listOf(404)) }
      .hasMessageContaining("Document not found")
      .hasMessageContaining("document 404: none (no file path resolved)")
  }

  @Test
  fun `get raw documents includes searched file paths for each missing document in the 404`() {
    val original = Path.of("/media/documents/originals/2026/missing original.pdf")
    val archive = Path.of("/media/documents/archive/2026/missing archive.pdf")
    `when`(binaryService.findDocument(42)).thenReturn(BinaryDocumentLookup(null, listOf(original, archive)))
    `when`(binaryService.findDocument(404)).thenReturn(BinaryDocumentLookup(null))

    assertThatThrownBy { tools.getRawDocuments(listOf(42, 404)) }
      .isInstanceOfSatisfying(ResponseStatusException::class.java) { exception ->
        assertThat(exception.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(exception.reason).isEqualTo(
          "Document not found. Searched file paths: document 42: $original, $archive; " +
              "document 404: none (no file path resolved)"
        )
      }
  }

  @Test
  fun `get raw documents includes path conversion errors in the 404`() {
    val error = "Cannot resolve stored path '/media/documents/originals/2023/none/Fodor utca átadás.pdf': " +
        "Malformed input or input contains unmappable characters (sun.jnu.encoding=ANSI_X3.4-1968)"
    `when`(binaryService.findDocument(432)).thenReturn(
      BinaryDocumentLookup(null, pathResolutionErrors = listOf(error))
    )

    assertThatThrownBy { tools.getRawDocuments(listOf(432)) }
      .isInstanceOfSatisfying(ResponseStatusException::class.java) { exception ->
        assertThat(exception.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(exception.reason).contains("document 432:", "path resolution errors: $error")
      }
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
  fun `list tags returns hierarchy and counts with names usable for document lookup`() {
    val tags = listOf(
      TagDto(id = 1, name = "Finance", parentId = null, documentCount = 0),
      TagDto(id = 2, name = "Invoices", parentId = 1, documentCount = 1),
    )
    val matchingDocument = document(262, "Invoice")
    `when`(documentService.findAllTags()).thenReturn(tags)
    `when`(documentService.findDocumentsByTag("Invoices", null, null)).thenReturn(listOf(matchingDocument))

    val result = tools.listTags()

    assertThat(result.tags).containsExactlyElementsOf(tags)
    assertThat(tools.findDocumentsByTag(result.tags[1].name).documents).containsExactly(matchingDocument)
    verify(documentService).findAllTags()
  }

  @Test
  fun `list tags returns an empty collection when no tags exist`() {
    `when`(documentService.findAllTags()).thenReturn(emptyList())

    assertThat(tools.listTags().tags).isEmpty()
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
