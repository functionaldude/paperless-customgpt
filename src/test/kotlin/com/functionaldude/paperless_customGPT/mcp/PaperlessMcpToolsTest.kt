package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.*
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import io.modelcontextprotocol.spec.McpSchema.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.ai.util.JacksonUtils
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.time.LocalDate
import java.util.*
import javax.imageio.ImageIO

class PaperlessMcpToolsTest {
  private val documentService = mock(PaperlessDocumentService::class.java)
  private val ragQueryService = mock(RagQueryService::class.java)
  private val binaryService = mock(PaperlessDocumentBinaryService::class.java)
  private val tools =
    PaperlessMcpTools(documentService, ragQueryService, binaryService, PaperlessDocumentVisualService())

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
  fun `PDF response defaults to the unchanged original binary resource`() {
    val document = pdf(12)
    `when`(binaryService.findDocument(518)).thenReturn(BinaryDocumentLookup(document))

    val results = listOf(
      tools.getRawDocuments(listOf(518)),
      tools.getRawDocuments(listOf(518), format = null),
      tools.getRawDocuments(listOf(518), format = " "),
      tools.getRawDocuments(listOf(518), format = "original"),
    )

    for (result in results) {
      val resource = (result.content().single() as EmbeddedResource).resource() as BlobResourceContents
      assertThat(resource.mimeType()).isEqualTo("application/pdf")
      assertThat(Base64.getDecoder().decode(resource.blob())).isEqualTo(document.content)
    }
  }

  @Test
  fun `visual PDF response returns labelled native images and survives MCP serialization`() {
    val document = pdf(3)
    `when`(binaryService.findDocument(518)).thenReturn(BinaryDocumentLookup(document))

    val result = tools.getRawDocuments(listOf(518), format = "visual")
    val mapper = JacksonUtils.getDefaultJsonMapper()
    val json = mapper.readTree(mapper.writeValueAsString(result))

    assertThat(result.isError()).isNotEqualTo(true)
    assertThat(result.content()).hasSize(7)
    assertThat((result.content()[0] as TextContent).text()).contains("Document 518", "pages 1-3 of 3")
    assertThat(List(json.path("content").size()) { index -> json.path("content")[index].path("type").asText() })
      .containsExactly("text", "text", "image", "text", "image", "text", "image")
    assertThat(json.toString()).doesNotContain("\"blob\"", "\"resource\"", "paperless://")
    for (page in 1..3) {
      assertThat((result.content()[page * 2 - 1] as TextContent).text()).contains("page $page of 3")
      val image = result.content()[page * 2] as ImageContent
      assertThat(image.mimeType()).isEqualTo("image/png")
      val decoded = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(image.data())))
      assertThat(decoded.width).isEqualTo(208)
      assertThat(decoded.height).isEqualTo(208)
      assertThat(Color(decoded.getRGB(10, 10))).isEqualTo(Color(page * 60, 0, 0))
    }
  }

  @Test
  fun `visual PDF response returns all pages by default even beyond the explicit page limit cap`() {
    `when`(binaryService.findDocument(518)).thenReturn(BinaryDocumentLookup(pdf(12)))

    val result = tools.getRawDocuments(listOf(518), format = "visual")

    assertThat(result.content().filterIsInstance<ImageContent>()).hasSize(12)
    assertThat((result.content()[0] as TextContent).text())
      .contains("pages 1-12 of 12")
      .doesNotContain("More pages remain")
  }

  @Test
  fun `PDF pages can be retrieved without silently omitting the remainder`() {
    `when`(binaryService.findDocument(518)).thenReturn(BinaryDocumentLookup(pdf(12)))

    val first = tools.getRawDocuments(listOf(518), format = "visual", pageLimit = 5)
    assertThat(first.content().filterIsInstance<ImageContent>()).hasSize(5)
    assertThat((first.content()[0] as TextContent).text())
      .contains("pages 1-5 of 12", "format=visual", "startPage=6", "pageLimit=5", "More pages remain")

    val remaining = tools.getRawDocuments(listOf(518), format = "visual", startPage = 6)
    assertThat(remaining.content().filterIsInstance<ImageContent>()).hasSize(7)
    assertThat((remaining.content()[0] as TextContent).text())
      .contains("pages 6-12 of 12")
      .doesNotContain("More pages remain")

    val nextBatch = tools.getRawDocuments(listOf(518), format = "visual", startPage = 6, pageLimit = 5)
    assertThat(nextBatch.content().filterIsInstance<ImageContent>()).hasSize(5)
    assertThat((nextBatch.content()[0] as TextContent).text())
      .contains("pages 6-10 of 12", "format=visual", "startPage=11", "pageLimit=5", "More pages remain")
  }

  @Test
  fun `native image response preserves the image bytes`() {
    val content = byteArrayOf(1, 2, 3)
    `when`(binaryService.findDocument(263)).thenReturn(
      BinaryDocumentLookup(BinaryDocument(content, "image/png", "scan.png"))
    )

    val result = tools.getRawDocuments(listOf(263), format = "visual")

    val image = result.content().filterIsInstance<ImageContent>().single()
    assertThat(image.mimeType()).isEqualTo("image/png")
    assertThat(Base64.getDecoder().decode(image.data())).isEqualTo(content)
  }

  @Test
  fun `visual errors produce readable tool errors instead of embedded resources`() {
    `when`(binaryService.findDocument(518)).thenReturn(
      BinaryDocumentLookup(BinaryDocument("invalid PDF".toByteArray(), "application/pdf", "broken.pdf"))
    )
    `when`(binaryService.findDocument(262)).thenReturn(
      BinaryDocumentLookup(BinaryDocument(byteArrayOf(1), "application/octet-stream", "document.bin"))
    )

    for (id in listOf(518, 262)) {
      val result = tools.getRawDocuments(listOf(id), format = "visual")
      assertThat(result.isError()).isTrue()
      assertThat(result.content()).allMatch { it is TextContent }
      assertThat((result.content().single() as TextContent).text()).contains("document $id", "format=original")
    }
  }

  @Test
  fun `raw document rejects invalid format and pagination before reading files`() {
    assertThatThrownBy { tools.getRawDocuments(listOf(518), format = "pdf") }
      .hasMessageContaining("format must be visual or original")
    assertThatThrownBy { tools.getRawDocuments(listOf(518), startPage = 0) }
      .hasMessageContaining("startPage must be greater than zero")
    for (limit in listOf(0, 11, Int.MAX_VALUE)) {
      assertThatThrownBy { tools.getRawDocuments(listOf(518), pageLimit = limit) }
        .hasMessageContaining("pageLimit must be between 1 and 10")
    }
    verifyNoInteractions(binaryService)
  }

  @Test
  fun `page beyond the end of a PDF returns a tool error`() {
    `when`(binaryService.findDocument(518)).thenReturn(BinaryDocumentLookup(pdf(3)))

    val result = tools.getRawDocuments(listOf(518), format = "visual", startPage = Int.MAX_VALUE)

    assertThat(result.isError()).isTrue()
    assertThat((result.content().single() as TextContent).text()).contains("exceeds the document's 3 pages")
  }

  @Test
  fun `large PDF pages are scaled to a bounded image size`() {
    val content = PDDocument().use { pdf ->
      pdf.addPage(PDPage(PDRectangle(3000f, 1000f)))
      ByteArrayOutputStream().use { output -> pdf.save(output); output.toByteArray() }
    }
    `when`(binaryService.findDocument(518)).thenReturn(
      BinaryDocumentLookup(BinaryDocument(content, "application/pdf", "large.pdf"))
    )

    val result = tools.getRawDocuments(listOf(518), format = "visual")

    val image = result.content().filterIsInstance<ImageContent>().single()
    val decoded = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(image.data())))
    assertThat(decoded.width).isLessThanOrEqualTo(2048)
    assertThat(decoded.height).isLessThanOrEqualTo(2048)
  }

  private fun pdf(pages: Int): BinaryDocument = PDDocument().use { pdf ->
    repeat(pages) { index ->
      val page = PDPage(PDRectangle(100f, 100f))
      pdf.addPage(page)
      PDPageContentStream(pdf, page).use { stream ->
        stream.setNonStrokingColor(Color(((index + 1) * 60).coerceAtMost(255), 0, 0))
        stream.addRect(0f, 0f, 100f, 100f)
        stream.fill()
      }
    }
    ByteArrayOutputStream().use { output ->
      pdf.save(output)
      BinaryDocument(output.toByteArray(), "application/pdf", "statement.pdf")
    }
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
