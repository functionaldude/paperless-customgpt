package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.TagDto
import com.functionaldude.paperless_customGPT.documents.TagList
import com.functionaldude.paperless_customGPT.rag.RagSearchResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.util.JacksonUtils
import java.time.LocalDate

class McpDtoSerializationTest {

  private val objectMapper = JacksonUtils.getDefaultJsonMapper()

  @Test
  fun `document output omits null optional fields`() {
    val json = objectMapper.writeValueAsString(
      DocumentDto(
        id = 262,
        title = "Document",
        documentDate = LocalDate.parse("2026-01-01"),
        modifiedAt = null,
        mimeType = "application/pdf",
        content = "content",
        ownerUsername = null,
        note = null,
        correspondentName = null,
        documentType = null,
        tags = emptyList(),
        sourceUrl = "https://paperless.example/documents/262",
      )
    )

    assertThat(json).doesNotContain(""""modifiedAt"""")
    assertThat(json).doesNotContain(""""ownerUsername"""")
    assertThat(json).doesNotContain(""""note"""")
    assertThat(json).doesNotContain(""""correspondentName"""")
    assertThat(json).doesNotContain(""""documentType"""")
    assertThat(json).contains(""""tags":[]""")
    assertThat(json).doesNotContain(""""resourceUrl"""")
  }

  @Test
  fun `tag output preserves parent links and zero counts and omits parent only for roots`() {
    val json = objectMapper.readTree(
      objectMapper.writeValueAsString(
        TagList(
          listOf(
            TagDto(id = 1, name = "Finance", parentId = null, documentCount = 0),
            TagDto(id = 2, name = "Invoices", parentId = 1, documentCount = 3),
          )
        )
      )
    )

    val tags = json.path("tags")
    assertThat(tags.size()).isEqualTo(2)
    assertThat(tags[0].path("id").asInt()).isEqualTo(1)
    assertThat(tags[0].has("parentId")).isFalse()
    assertThat(tags[0].has("documentCount")).isTrue()
    assertThat(tags[0].path("documentCount").asInt()).isZero()
    assertThat(tags[1].path("name").asString()).isEqualTo("Invoices")
    assertThat(tags[1].path("parentId").asInt()).isEqualTo(1)
    assertThat(tags[1].path("documentCount").asInt()).isEqualTo(3)
  }

  @Test
  fun `rag output omits null optional fields`() {
    val json = objectMapper.writeValueAsString(
      RagSearchResult(
        paperlessDocId = 262,
        title = null,
        correspondentName = null,
        snippet = "snippet",
        score = 0.42,
        sourceUrl = "https://paperless.example/documents/262",
      )
    )

    assertThat(json).doesNotContain(""""title"""")
    assertThat(json).doesNotContain(""""correspondentName"""")
    assertThat(json).doesNotContain(""""resourceUrl"""")
  }
}
