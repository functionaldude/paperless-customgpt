package com.functionaldude.paperless_customGPT.mcp

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
class ChatGptCompatibilityMcpTools(
  private val paperlessDocumentService: PaperlessDocumentService,
  private val ragQueryService: RagQueryService,
) {
  @McpTool(
    name = "search",
    description = "Use this to find Paperless documents relevant to a natural-language research question. Fetch a result by id to read its full content.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false,
    ),
  )
  fun search(
    @McpToolParam(description = "Natural-language search query.")
    query: String,
  ): ChatGptSearchResponse {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) {
      return ChatGptSearchResponse(emptyList())
    }

    val results = ragQueryService.findDocumentsSimilarTo(normalizedQuery, SEARCH_RESULT_LIMIT)
      .map { result ->
        ChatGptSearchResult(
          id = result.paperlessDocId.toString(),
          title = result.title ?: "(untitled)",
          url = result.sourceUrl,
        )
      }

    return ChatGptSearchResponse(results)
  }

  @McpTool(
    name = "fetch",
    description = "Use this after search to retrieve one Paperless document. It returns extracted text plus a metadata resourceUrl for reading the original visual PDF when layout or OCR accuracy matters.",
    generateOutputSchema = true,
    annotations = McpTool.McpAnnotations(
      readOnlyHint = true,
      destructiveHint = false,
      idempotentHint = true,
      openWorldHint = false,
    ),
  )
  fun fetch(
    @McpToolParam(description = "Paperless document id returned by search.")
    id: String,
  ): ChatGptFetchResponse {
    val documentId = id.toIntOrNull()
      ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Document id must be a number")
    val document = paperlessDocumentService.findDocumentById(documentId)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")

    return ChatGptFetchResponse(
      id = document.id.toString(),
      title = document.title,
      text = document.content,
      url = document.sourceUrl,
      metadata = ChatGptFetchMetadata(
        documentDate = document.documentDate,
        modifiedAt = document.modifiedAt,
        mimeType = document.mimeType,
        ownerUsername = document.ownerUsername,
        note = document.note,
        correspondentName = document.correspondentName,
        tags = document.tags,
        resourceUrl = document.resourceUrl,
      ),
    )
  }

  companion object {
    const val SEARCH_RESULT_LIMIT = 10
  }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChatGptSearchResult(
  @field:JsonPropertyDescription("Stable Paperless document identifier that can be passed to fetch.")
  val id: String,
  @field:JsonPropertyDescription("Human-readable Paperless document title.")
  val title: String,
  @field:JsonPropertyDescription("Absolute URL that the user can open to view the cited document.")
  val url: String,
)

data class ChatGptSearchResponse(
  @field:JsonPropertyDescription("Documents relevant to the supplied query.")
  val results: List<ChatGptSearchResult>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChatGptFetchResponse(
  @field:JsonPropertyDescription("Stable Paperless document identifier.")
  val id: String,
  @field:JsonPropertyDescription("Human-readable Paperless document title.")
  val title: String,
  @field:JsonPropertyDescription("Complete extracted text of the Paperless document.")
  val text: String,
  @field:JsonPropertyDescription("Absolute URL that the user can open to view the cited document.")
  val url: String,
  @field:JsonPropertyDescription("Additional Paperless document metadata.")
  val metadata: ChatGptFetchMetadata,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChatGptFetchMetadata(
  val documentDate: LocalDate,
  @field:JsonProperty(required = false)
  val modifiedAt: OffsetDateTime?,
  val mimeType: String,
  @field:JsonProperty(required = false)
  val ownerUsername: String?,
  @field:JsonProperty(required = false)
  val note: String?,
  @field:JsonProperty(required = false)
  val correspondentName: String?,
  @field:JsonProperty(required = false)
  val tags: List<String>?,
  @field:JsonPropertyDescription("MCP resource URI for reading the original visual document as a binary blob.")
  val resourceUrl: String,
)
