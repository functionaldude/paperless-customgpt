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

  @Test
  fun `findDocumentsByCorrespondent filters and orders by creation date`() {
    val document = paperlessDocumentService.findDocumentById(DOC_ID)!!

    val documents = paperlessDocumentService.findDocumentsByCorrespondent("a1")

    assertThat(documents).extracting<Int> { it.id }.contains(DOC_ID)
    assertThat(documents.map { it.documentDate }).isSortedAccordingTo(reverseOrder())
    assertThat(
      paperlessDocumentService.findDocumentsByCorrespondent(
        "A1",
        fromDate = document.documentDate,
        toDate = document.documentDate,
      )
    ).extracting<Int> { it.id }.contains(DOC_ID)
    assertThat(
      paperlessDocumentService.findDocumentsByCorrespondent(
        "A1",
        fromDate = document.documentDate.plusDays(1),
      )
    ).extracting<Int> { it.id }.doesNotContain(DOC_ID)
  }

  @Test
  fun `findDocumentsByTag filters and preserves all document tags`() {
    val document = paperlessDocumentService.findDocumentById(DOC_ID)!!

    val documents = paperlessDocumentService.findDocumentsByTag("VIA E-MAIL")

    assertThat(documents).extracting<Int> { it.id }.contains(DOC_ID)
    assertThat(documents.map { it.documentDate }).isSortedAccordingTo(reverseOrder())
    assertThat(documents.first { it.id == DOC_ID }.tags).contains("via E-Mail")
    assertThat(
      paperlessDocumentService.findDocumentsByTag(
        "via E-Mail",
        toDate = document.documentDate.minusDays(1),
      )
    ).extracting<Int> { it.id }.doesNotContain(DOC_ID)
  }
}
