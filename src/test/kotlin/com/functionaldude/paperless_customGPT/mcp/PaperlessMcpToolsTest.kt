package com.functionaldude.paperless_customGPT.mcp

import com.functionaldude.paperless_customGPT.documents.DocumentList
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import com.functionaldude.paperless_customGPT.rag.RagQueryService
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class PaperlessMcpToolsTest {
  private val documentService = mock(PaperlessDocumentService::class.java)
  private val ragQueryService = mock(RagQueryService::class.java)
  private val tools = PaperlessMcpTools(documentService, ragQueryService)

  @Test
  fun `list documents applies its default page size and clamps oversized pages`() {
    `when`(documentService.findDocumentsPage(50, 0)).thenReturn(DocumentList(emptyList()))
    `when`(documentService.findDocumentsPage(100, 7)).thenReturn(DocumentList(emptyList()))

    tools.listDocuments()
    tools.listDocuments(limit = 101, offset = 7)

    verify(documentService).findDocumentsPage(50, 0)
    verify(documentService).findDocumentsPage(100, 7)
  }

  @Test
  fun `list documents rejects invalid pagination values`() {
    assertThatThrownBy { tools.listDocuments(limit = 0) }
      .hasMessageContaining("limit must be greater than zero")
    assertThatThrownBy { tools.listDocuments(offset = -1) }
      .hasMessageContaining("offset must not be negative")
  }
}
