package com.functionaldude.paperless_customGPT.documents

import com.functionaldude.paperless_customGPT.DOC_ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class DocumentIntegrationTests {

  @Autowired
  private lateinit var paperlessDocumentService: PaperlessDocumentService

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
}
