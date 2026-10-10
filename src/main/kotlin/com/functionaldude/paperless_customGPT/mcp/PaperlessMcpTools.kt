package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.*
import com.functionaldude.paperless_customGPT.rag.RagQueryResponse
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import io.modelcontextprotocol.spec.McpSchema.*
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.*

@Component
class PaperlessMcpTools(
  private val paperlessDocumentService: PaperlessDocumentService,
  private val ragQueryService: RagQueryService,
  private val paperlessDocumentBinaryService: PaperlessDocumentBinaryService,
  private val paperlessDocumentVisualService: PaperlessDocumentVisualService,
) {
  @McpTool(
    name = "getRawDocuments",
    description = "Returns original Paperless documents as embedded binary resources by default (format=original). Use this when layout, scans, handwriting, tables, or OCR accuracy matter. If the client cannot consume embedded binary resources, retry with format=visual for native images, including rendered PDF pages. Visual output defaults to all pages per document; use startPage and pageLimit to request a smaller range if the response is too large.",
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
    @McpToolParam(
      description = "Output format: original (default, embedded binary resources) or visual (native images for PDF, PNG, JPEG, and WebP; use if the client cannot consume the original response).",
      required = false
    )
    format: String? = null,
    @McpToolParam(
      description = "First page to return for each document, starting at 1. Defaults to 1; applies to visual output.",
      required = false
    )
    startPage: Int? = null,
    @McpToolParam(
      description = "Maximum pages per document for visual output. Omit to return all remaining pages; when supplied, must be between 1 and 10.",
      required = false
    )
    pageLimit: Int? = null,
  ): CallToolResult {
    val outputFormat = format?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "original"
    if (outputFormat !in setOf("visual", "original")) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be visual or original")
    }
    val firstPage = startPage ?: 1
    if (firstPage <= 0) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "startPage must be greater than zero")
    }
    if (pageLimit != null && pageLimit !in 1..PaperlessDocumentVisualService.MAX_PAGE_LIMIT) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "pageLimit must be between 1 and 10")
    }
    val result = CallToolResult.builder()

    val lookups = ids.map { documentId -> documentId to paperlessDocumentBinaryService.findDocument(documentId) }
    val documents = lookups.mapNotNull { (documentId, lookup) ->
      lookup.document?.let { documentId to it }
    }
    if (documents.isEmpty()) {
      val searchedPaths = lookups.joinToString("; ") { (documentId, lookup) ->
        val paths = lookup.searchedPaths.joinToString().ifEmpty { "none (no file path resolved)" }
        val errors = if (lookup.pathResolutionErrors.isEmpty()) "" else
          lookup.pathResolutionErrors.joinToString("; ", prefix = "; path resolution errors: ")
        "document $documentId: $paths$errors"
      }.ifEmpty { "none (no document IDs supplied)" }
      throw ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found. Searched file paths: $searchedPaths")
    }

    documents.forEach { (documentId, document) ->
      when (outputFormat) {
        "visual" -> {
          try {
            val rendered = paperlessDocumentVisualService.render(document, firstPage, pageLimit)
            val lastPage = rendered.pages.last().pageNumber
            result.addTextContent(
              buildString {
                append("Document $documentId (${document.fileName}): showing pages $firstPage-$lastPage of ${rendered.totalPages}.")
                if (lastPage < rendered.totalPages) {
                  append(" More pages remain; call getRawDocuments with ids=[$documentId], format=visual, startPage=${lastPage + 1}, pageLimit=$pageLimit.")
                }
              }
            )
            rendered.pages.forEach { page ->
              result.addTextContent("Document $documentId, page ${page.pageNumber} of ${rendered.totalPages}.")
              result.addContent(
                ImageContent.builder(Base64.getEncoder().encodeToString(page.content), page.mimeType)
                  .annotations(Annotations.builder().audience(listOf(Role.USER, Role.ASSISTANT)).priority(1.0).build())
                  .build()
              )
            }
          } catch (exception: IOException) {
            result.isError(true).addTextContent(
              "Cannot render document $documentId: ${exception.message ?: "invalid or unreadable PDF"}. " +
                  "Use fetch or findDocumentsByIds for extracted text, or format=original in a client that supports embedded binary resources."
            )
          } catch (exception: IllegalArgumentException) {
            result.isError(true).addTextContent("Cannot render document $documentId: ${exception.message}")
          }
          return@forEach
        }

        "original" -> {
          val content = BlobResourceContents
            .builder(
              URI(
                "paperless",
                "documents",
                "/$documentId/$documentId-${document.fileName}",
                null,
                null
              ).toASCIIString(),
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

        else -> result.isError(true).addTextContent("Unsupported format: $outputFormat")
      }
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
  ): DocumentList = DocumentList(
    documents = ids
      .mapNotNull(paperlessDocumentService::findDocumentById)
      .takeIf { it.isNotEmpty() }
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")
  )

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
    name = "findDocumentsByDocumentType",
    description = "Finds Paperless documents assigned to a document type with the supplied name.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun findDocumentsByDocumentType(
    @McpToolParam(description = "Exact Paperless document type name to match, ignoring case.")
    documentTypeName: String,
    @McpToolParam(description = "Optional inclusive earliest document creation date in YYYY-MM-DD format.")
    fromDate: String? = null,
    @McpToolParam(description = "Optional inclusive latest document creation date in YYYY-MM-DD format.")
    toDate: String? = null,
  ): DocumentList {
    val normalizedName = documentTypeName.trim()
    if (normalizedName.isEmpty()) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type name must not be blank")
    }
    val dateRange = parseDateRange(fromDate, toDate)

    return DocumentList(
      paperlessDocumentService.findDocumentsByDocumentType(
        normalizedName,
        dateRange.from,
        dateRange.to,
      )
    )
  }

  @McpTool(
    name = "listTags",
    description = "Returns all Paperless tags, including unused tags, with parent tag identifiers describing the hierarchy and counts of directly assigned documents. Use the returned tag names with findDocumentsByTag.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun listTags(): TagList = TagList(paperlessDocumentService.findAllTags())

  @McpTool(
    name = "findDocumentsByTag",
    description = "Finds Paperless documents directly assigned to a tag with the supplied name. Use listTags to discover available tag names and their hierarchy; descendant tags are not included automatically.",
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
