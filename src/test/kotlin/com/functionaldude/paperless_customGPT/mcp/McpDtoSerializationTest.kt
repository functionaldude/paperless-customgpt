package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.DocumentDto
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
        tags = emptyList(),
        sourceUrl = "https://paperless.example/documents/262",
      )
    )

    assertThat(json).doesNotContain(""""modifiedAt"""")
    assertThat(json).doesNotContain(""""ownerUsername"""")
    assertThat(json).doesNotContain(""""note"""")
    assertThat(json).doesNotContain(""""correspondentName"""")
    assertThat(json).contains(""""tags":[]""")
    assertThat(json).doesNotContain(""""resourceUrl"""")
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
