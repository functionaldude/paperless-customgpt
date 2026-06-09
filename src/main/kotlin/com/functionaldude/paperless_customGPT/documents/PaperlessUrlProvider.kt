package com.functionaldude.paperless_customGPT.documents

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PaperlessUrlProvider(
  @Value("\${PAPERLESS_BASE_URL:http://localhost:8000}") baseUrl: String,
) {
  private val normalizedBaseUrl = baseUrl.trimEnd('/')

  init {
    require(normalizedBaseUrl.isNotBlank()) { "PAPERLESS_BASE_URL must be provided" }
  }

  fun documentUrl(documentId: Int): String = "$normalizedBaseUrl/documents/$documentId"
}