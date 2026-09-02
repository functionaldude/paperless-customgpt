package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.DocumentList
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentBinaryService
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.RagQueryResponse
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import io.modelcontextprotocol.spec.McpSchema.*
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.*

@Component
class PaperlessMcpTools(
  private val paperlessDocumentService: PaperlessDocumentService,
  private val ragQueryService: RagQueryService,
  private val paperlessDocumentBinaryService: PaperlessDocumentBinaryService,
) {
  @McpTool(
    name = "getRawDocuments",
    description = "Returns the original visual Paperless documents. Use this when layout, scans, handwriting, tables, or OCR accuracy matter.",
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false,
    ),
  )
  fun getRawDocuments(
    @McpToolParam(description = "Numeric Paperless document ids.")
    ids: List<Int>,
  ): CallToolResult {
    val result = CallToolResult.builder()

    val documents = ids
      .mapNotNull { documentId ->
        documentId to (paperlessDocumentBinaryService.findDocument(documentId) ?: return@mapNotNull null)
      }
      .takeIf { it.isNotEmpty() } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")

    documents.forEach { (documentId, document) ->
      val content = BlobResourceContents
        .builder(
          "paperless://documents/$documentId/content",
          Base64.getEncoder().encodeToString(document.content),
        )
        .mimeType(document.mimeType)
        .build()

      result.addContent(
        EmbeddedResource
          .builder(content)
          .annotations(
            Annotations.builder()
              .audience(listOf(Role.USER, Role.ASSISTANT))
              .priority(1.0)
              .build(),
          )
          .build(),
      )
    }

    return result.build()
  }

  @McpTool(
    name = "listDocuments",
    description = "Returns a paginated list of Paperless documents together with metadata and extracted content when available.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun listDocuments(
    @McpToolParam(description = "Maximum number of documents to return. Defaults to 50 and values over 100 are clamped.")
    limit: Int? = null,
    @McpToolParam(description = "Zero-based offset for the next page. Defaults to 0.")
    offset: Int? = null,
  ): DocumentList {
    val requestedLimit = limit ?: DEFAULT_PAGE_SIZE
    if (requestedLimit <= 0) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be greater than zero")
    }

    val requestedOffset = offset ?: 0
    if (requestedOffset < 0) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "offset must not be negative")
    }

    return paperlessDocumentService.findDocumentsPage(
      limit = requestedLimit.coerceAtMost(MAX_PAGE_SIZE),
      offset = requestedOffset,
    )
  }

  @McpTool(
    name = "findDocumentsByIds",
    description = "Looks up Paperless documents for the supplied identifiers and returns their metadata and content.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun findDocumentsByIds(
    @McpToolParam(description = "Numeric Paperless document ids.")
    ids: List<Int>,
  ): List<DocumentDto> = ids
    .mapNotNull(paperlessDocumentService::findDocumentById)
    .takeIf { it.isNotEmpty() }
    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")

  @McpTool(
    name = "findDocumentsByCorrespondent",
    description = "Finds Paperless documents whose correspondent name contains the supplied text.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun findDocumentsByCorrespondent(
    @McpToolParam(description = "Text to match against the correspondent name, ignoring case.")
    correspondentName: String,
    @McpToolParam(description = "Optional inclusive earliest document creation date in YYYY-MM-DD format.")
    fromDate: String? = null,
    @McpToolParam(description = "Optional inclusive latest document creation date in YYYY-MM-DD format.")
    toDate: String? = null,
  ): DocumentList {
    val normalizedName = correspondentName.trim()
    if (normalizedName.isEmpty()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Correspondent name must not be blank")
    }
    val dateRange = parseDateRange(fromDate, toDate)

    return DocumentList(
      paperlessDocumentService.findDocumentsByCorrespondent(
        normalizedName,
        dateRange.from,
        dateRange.to,
      )
    )
  }

  @McpTool(
    name = "findDocumentsByTag",
    description = "Finds Paperless documents that have a tag with the supplied name.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun findDocumentsByTag(
    @McpToolParam(description = "Exact Paperless tag name to match, ignoring case.")
    tagName: String,
    @McpToolParam(description = "Optional inclusive earliest document creation date in YYYY-MM-DD format.")
    fromDate: String? = null,
    @McpToolParam(description = "Optional inclusive latest document creation date in YYYY-MM-DD format.")
    toDate: String? = null,
  ): DocumentList {
    val normalizedName = tagName.trim()
    if (normalizedName.isEmpty()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag name must not be blank")
    }
    val dateRange = parseDateRange(fromDate, toDate)

    return DocumentList(
      paperlessDocumentService.findDocumentsByTag(
        normalizedName,
        dateRange.from,
        dateRange.to,
      )
    )
  }

  @McpTool(
    name = "searchRag",
    description = "Uses pgvector similarity search to retrieve the most relevant Paperless documents and their best-matching text snippets. Use getRawDocuments when the original visual documents may improve accuracy; use findDocumentsByIds for complete extracted text.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun searchRag(
    @McpToolParam(description = "Natural language prompt used to search previously ingested Paperless documents.")
    query: String,
    @McpToolParam(description = "Optional number of top results to return. Values over 50 are clamped.")
    topK: Int? = null,
    @McpToolParam(description = "Optional inclusive earliest document creation date in YYYY-MM-DD format.")
    fromDate: String? = null,
    @McpToolParam(description = "Optional inclusive latest document creation date in YYYY-MM-DD format.")
    toDate: String? = null,
  ): RagQueryResponse {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must not be blank")
    }

    val requestedTopK = topK?.takeIf { it > 0 } ?: DEFAULT_TOP_K
    val effectiveTopK = requestedTopK.coerceAtMost(MAX_TOP_K)
    val dateRange = parseDateRange(fromDate, toDate)
    val results = ragQueryService.findDocumentsSimilarTo(
      normalizedQuery,
      effectiveTopK,
      dateRange.from,
      dateRange.to,
    )
    return RagQueryResponse(results)
  }

  private fun parseDateRange(fromDate: String?, toDate: String?): DateRange {
    val from = parseDate(fromDate, "fromDate")
    val to = parseDate(toDate, "toDate")
    if (from != null && to != null && from > to) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must not be after toDate")
    }
    return DateRange(from, to)
  }

  private fun parseDate(value: String?, parameterName: String): LocalDate? {
    val normalizedValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
      LocalDate.parse(normalizedValue)
    } catch (_: DateTimeParseException) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$parameterName must use YYYY-MM-DD format")
    }
  }

  private data class DateRange(val from: LocalDate?, val to: LocalDate?)

  companion object {
    const val DEFAULT_TOP_K = 5
    const val MAX_TOP_K = 50
    const val DEFAULT_PAGE_SIZE = 50
    const val MAX_PAGE_SIZE = 100
  }
}
