package com.functionaldude.paperless_customGPT.rag

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription

@JsonClassDescription("Single semantic search hit with additional metadata to aid follow-up calls.")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RagSearchResult(
  @field:JsonPropertyDescription("Document id that can be used with the findDocumentById tool to fetch the full record.")
  val paperlessDocId: Int,
  @field:JsonProperty(required = false)
  @field:JsonPropertyDescription("Document title if Paperless stored one.")
  val title: String?,
  @field:JsonProperty(required = false)
  @field:JsonPropertyDescription("Counterparty or correspondent responsible for the document.")
  val correspondentName: String?,
  @field:JsonPropertyDescription("Snippet of the document content to help the agent understand why it matched.")
  val snippet: String,
  @field:JsonPropertyDescription("Vector similarity score. Higher values are more relevant.")
  val score: Double,
  @field:JsonPropertyDescription("Direct link to the source document inside Paperless.")
  val sourceUrl: String,
)

@JsonClassDescription("Wrapper containing RAG search hits.")
data class RagQueryResponse(
  @field:JsonPropertyDescription("Ranked snippets most relevant to the question.")
  val results: List<RagSearchResult>
)
