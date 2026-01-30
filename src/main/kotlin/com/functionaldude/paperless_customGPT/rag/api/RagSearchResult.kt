package com.functionaldude.paperless_customGPT.rag.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Single semantic search hit with additional metadata to aid follow-up calls.")
data class RagSearchResult(
  @field:Schema(
    description = "Document id that can be used with the documents API to fetch the full record.",
    example = "1234"
  )
  val paperlessDocId: Int,
  @field:Schema(
    description = "Document title if Paperless stored one.",
    example = "Car insurance renewal",
    nullable = true
  )
  val title: String?,
  @field:Schema(
    description = "Counterparty/correspondent responsible for the document.",
    example = "ACME Insurance",
    nullable = true
  )
  val correspondentName: String?,
  @field:Schema(description = "Snippet of the document content to help the agent understand why it matched.")
  val snippet: String,
  @field:Schema(description = "Vector similarity score (higher is more relevant).", example = "0.82")
  val score: Double,
)
