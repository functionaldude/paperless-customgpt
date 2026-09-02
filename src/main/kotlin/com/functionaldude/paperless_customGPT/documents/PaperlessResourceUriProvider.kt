package com.functionaldude.paperless_customGPT.documents

import org.springframework.stereotype.Component

@Component
class PaperlessResourceUriProvider {
  fun documentResourceUri(documentId: Int): String = "paperless://documents/$documentId/content"
}
