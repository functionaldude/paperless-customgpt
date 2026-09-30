package com.functionaldude.paperless_customGPT.documents

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription

@JsonClassDescription("A Paperless tag with its position in the tag hierarchy and directly tagged document count.")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TagDto(
  @field:JsonPropertyDescription("Unique numeric identifier of the Paperless tag.")
  val id: Int,
  @field:JsonPropertyDescription("Exact Paperless tag name to pass as tagName to findDocumentsByTag.")
  val name: String,
  @field:JsonProperty(required = false)
  @field:JsonPropertyDescription("Identifier of the immediate parent tag in this list. Omitted for root tags.")
  val parentId: Int?,
  @field:JsonPropertyDescription("Number of non-deleted root documents directly assigned to this tag, excluding document versions and documents tagged only with descendants.")
  val documentCount: Int,
)

@JsonClassDescription("All Paperless tags, including unused tags, with parent links describing the complete hierarchy.")
data class TagList(
  @field:JsonPropertyDescription("All Paperless tags ordered by name and identifier. Follow parentId links to reconstruct the hierarchy.")
  val tags: List<TagDto>,
)
