package com.functionaldude.paperless_customGPT.documents

import com.functionaldude.paperless_customGPT.DOC_ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class DocumentIntegrationTests {

  @Autowired
  private lateinit var paperlessDocumentService: PaperlessDocumentService

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Test
  fun `findDocumentById returns document 233`() {
    val document = paperlessDocumentService.findDocumentById(DOC_ID)

    assertThat(document).isNotNull
    assertThat(document!!.id).isEqualTo(DOC_ID)
    assertThat(document.mimeType).isEqualTo(PaperlessDocumentService.PDF_MIME)
    assertThat(document.content).isNotBlank
    assertThat(document.title).contains("Ihre aktuelle A1 Online-Rechnung 1/2026")
    assertThat(document.content).contains("Bitte nicht einzahlen!")
    assertThat(document.ownerUsername).contains("functionaldude")
    assertThat(document.correspondentName).contains("A1")
    assertThat(document.sourceUrl).endsWith("/documents/$DOC_ID")
    assertThat(document.tags).contains("via E-Mail")
  }

  @Test
  @WithMockUser
  fun `GET api documents id returns document 233`() {
    mockMvc.perform(
      get("/api/documents/$DOC_ID")
        .accept(MediaType.APPLICATION_JSON)
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.id").value(DOC_ID))
      .andExpect(jsonPath("$.content").isNotEmpty)
      .andExpect(jsonPath("$.title").isNotEmpty)
      .andExpect(jsonPath("$.ownerUsername").value("functionaldude"))
      .andExpect(jsonPath("$.correspondentName").value("A1"))
  }

  @Test
  @WithMockUser
  fun `GET api documents id rejects non numeric ids`() {
    mockMvc.perform(
      get("/api/documents/not-a-number")
        .accept(MediaType.APPLICATION_JSON)
    )
      .andExpect(status().isBadRequest)
  }
}
