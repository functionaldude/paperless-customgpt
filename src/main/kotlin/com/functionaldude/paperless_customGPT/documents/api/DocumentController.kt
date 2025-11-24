package com.functionaldude.paperless_customGPT.documents.api

import com.functionaldude.paperless_customGPT.documents.DocumentDto
import com.functionaldude.paperless_customGPT.documents.PaperlessDocumentService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/documents")
class DocumentController(
  private val paperlessDocumentService: PaperlessDocumentService
) {
  @RequestMapping("all")
  fun listDocuments(): List<DocumentDto> {
    return paperlessDocumentService.findAllDocuments()
  }

  @RequestMapping("{id}")
  fun findDocumentById(@PathVariable id: String): DocumentDto? {
    val documentId = id.toIntOrNull() ?: return null
    return paperlessDocumentService.findDocumentById(documentId)
  }
}