package com.functionaldude.paperless_customGPT.rag

import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_CHUNK
import com.functionaldude.paperless.jooq.paperless_rag.tables.references.DOCUMENT_SOURCE
import com.functionaldude.paperless.jooq.public.tables.references.DOCUMENTS_CORRESPONDENT
import com.functionaldude.paperless.jooq.public.tables.references.DOCUMENTS_DOCUMENT
import com.functionaldude.paperless_customGPT.DOC_ID
import com.functionaldude.paperless_customGPT.rag.internal.IngestionCandidate
import com.functionaldude.paperless_customGPT.rag.internal.RagIngestionService
import com.functionaldude.paperless_customGPT.rag.internal.RagQueryService
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagIntegrationTests {

  @Autowired
  private lateinit var dsl: DSLContext

  @Autowired
  private lateinit var ragIngestionService: RagIngestionService

  @Autowired
  private lateinit var ragQueryService: RagQueryService

  @Autowired
  private lateinit var mockMvc: MockMvc

  @BeforeAll
  fun ingestDocument233() {
    ragIngestionService.processCandidate(loadCandidate())
  }

  @Test
  fun `ingestion creates document source and chunks for doc 233`() {
    val source = dsl
      .selectFrom(DOCUMENT_SOURCE)
      .where(DOCUMENT_SOURCE.PAPERLESS_DOC_ID.eq(DOC_ID))
      .fetchOne()

    assertThat(source).isNotNull
    assertThat(source!!.status).isEqualTo("DONE")
    assertThat(source.lastIngestedAt).isNotNull

    val chunkCount = dsl.fetchCount(
      dsl.selectFrom(DOCUMENT_CHUNK)
        .where(DOCUMENT_CHUNK.DOCUMENT_SOURCE_ID.eq(DOC_ID))
    )
    assertThat(chunkCount).isGreaterThan(0)
  }

  @Test
  fun `rag query returns document 233 when using its chunk text`() {
    val queryText = loadChunkTextForQuery()

    val results = ragQueryService.findDocumentsSimilarTo(queryText, 15)

    assertThat(results).isNotEmpty
    assertThat(results.any { it.paperlessDocId == DOC_ID }).isTrue
  }

  private fun loadCandidate(): IngestionCandidate {
    val record = dsl
      .select(
        DOCUMENTS_DOCUMENT.ID,
        DOCUMENTS_DOCUMENT.TITLE,
        DOCUMENTS_DOCUMENT.MODIFIED,
        DOCUMENTS_DOCUMENT.CREATED,
        DOCUMENTS_CORRESPONDENT.NAME,
      )
      .from(DOCUMENTS_DOCUMENT)
      .leftJoin(DOCUMENTS_CORRESPONDENT)
      .on(DOCUMENTS_DOCUMENT.CORRESPONDENT_ID.eq(DOCUMENTS_CORRESPONDENT.ID))
      .where(DOCUMENTS_DOCUMENT.ID.eq(DOC_ID))
      .fetchOne()

    requireNotNull(record) { "Document $DOC_ID not found in Paperless" }

    val modifiedAt = record.get(DOCUMENTS_DOCUMENT.MODIFIED)
      ?: throw IllegalStateException("Document $DOC_ID has no modified timestamp")

    val createdAt = record.get(DOCUMENTS_DOCUMENT.CREATED)
      ?: throw IllegalStateException("Document $DOC_ID has no created date")

    return IngestionCandidate(
      paperlessDocId = record.get(DOCUMENTS_DOCUMENT.ID)!!,
      title = record.get(DOCUMENTS_DOCUMENT.TITLE) ?: "(untitled)",
      modifiedAt = modifiedAt,
      createdAt = createdAt,
      correspondentName = record.get(DOCUMENTS_CORRESPONDENT.NAME) ?: "",
    )
  }

  private fun loadChunkTextForQuery(): String {
    val chunk = dsl
      .select(DOCUMENT_CHUNK.CONTENT)
      .from(DOCUMENT_CHUNK)
      .where(DOCUMENT_CHUNK.DOCUMENT_SOURCE_ID.eq(DOC_ID))
      .orderBy(DOCUMENT_CHUNK.CHUNK_INDEX.asc())
      .limit(1)
      .fetchOne(DOCUMENT_CHUNK.CONTENT)

    require(!chunk.isNullOrBlank()) { "No chunks found for doc $DOC_ID; ingestion likely failed" }
    return chunk
  }
}
