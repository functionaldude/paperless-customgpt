package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.DocumentList
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.RagQueryResponse
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Component
class PaperlessMcpTools(
  private val paperlessDocumentService: PaperlessDocumentService,
  private val ragQueryService: RagQueryService,
) {
  @McpTool(
    name = "listDocuments",
    description = "Returns every Paperless PDF document together with metadata and extracted content.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun listDocuments(): DocumentList {
    return DocumentList(paperlessDocumentService.findAllDocuments())
  }

  @McpTool(
    name = "findDocumentById",
    description = "Looks up the Paperless document for the supplied identifier and returns its metadata and content.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false
    )
  )
  fun findDocumentById(
    @McpToolParam(description = "Numeric Paperless document id.")
    id: String
  ): DocumentDto {
    val documentId = id.toIntOrNull()
      ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Document id must be a number")

    return paperlessDocumentService.findDocumentById(documentId)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")
  }

  @McpTool(
    name = "findDocumentsByCorrespondent",
    description = "Finds Paperless PDF documents whose correspondent name contains the supplied text.",
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
    description = "Finds Paperless PDF documents that have a tag with the supplied name.",
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
    description = "Uses pgvector similarity search to retrieve the most relevant Paperless documents for the provided question.",
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
  }
}
