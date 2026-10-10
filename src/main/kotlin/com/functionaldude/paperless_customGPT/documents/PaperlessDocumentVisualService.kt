package com.functionaldude.paperless_customGPT.documents

import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

data class DocumentPageImage(val pageNumber: Int, val content: ByteArray, val mimeType: String)

data class DocumentPageImages(val totalPages: Int, val pages: List<DocumentPageImage>)

@Service
class PaperlessDocumentVisualService {
  fun render(document: BinaryDocument, startPage: Int, pageLimit: Int?): DocumentPageImages {
    require(startPage > 0) { "startPage must be greater than zero" }
    require(pageLimit == null || pageLimit in 1..MAX_PAGE_LIMIT) { "pageLimit must be between 1 and $MAX_PAGE_LIMIT" }

    return when (document.mimeType.lowercase()) {
      "application/pdf" -> renderPdf(document.content, startPage, pageLimit)
      "image/png", "image/jpeg", "image/webp" -> {
        require(startPage == 1) { "startPage exceeds the document's 1 page" }
        DocumentPageImages(1, listOf(DocumentPageImage(1, document.content, document.mimeType.lowercase())))
      }

      else -> throw IllegalArgumentException(
        "Visual output supports PDF, PNG, JPEG, and WebP documents; this document is ${document.mimeType}. " +
            "Use fetch or findDocumentsByIds for extracted text, or format=original in a client that supports embedded binary resources."
      )
    }
  }

  private fun renderPdf(content: ByteArray, startPage: Int, pageLimit: Int?): DocumentPageImages =
    Loader.loadPDF(content).use { pdf ->
      require(startPage <= pdf.numberOfPages) { "startPage exceeds the document's ${pdf.numberOfPages} pages" }
      val renderer = PDFRenderer(pdf).apply { isSubsamplingAllowed = true }
      val endPage = pageLimit?.let {
        min(pdf.numberOfPages.toLong(), startPage.toLong() + it - 1).toInt()
      } ?: pdf.numberOfPages
      val pages = (startPage..endPage).map { pageNumber ->
        val box = pdf.getPage(pageNumber - 1).cropBox
        val longestSide = max(box.width, box.height)
        require(box.width.isFinite() && box.height.isFinite() && box.width > 0 && box.height > 0) {
          "Page $pageNumber has invalid dimensions"
        }
        val scale = min(RENDER_DPI / 72f, MAX_IMAGE_DIMENSION / longestSide)
        val image = renderer.renderImage(pageNumber - 1, scale, ImageType.RGB)
        val bytes = try {
          ByteArrayOutputStream().use { output ->
            if (!ImageIO.write(image, "png", output)) throw IOException("PNG encoder is unavailable")
            output.toByteArray()
          }
        } finally {
          image.flush()
        }
        DocumentPageImage(pageNumber, bytes, "image/png")
      }
      DocumentPageImages(pdf.numberOfPages, pages)
    }

  companion object {
    const val MAX_PAGE_LIMIT = 10
    private const val RENDER_DPI = 150f
    private const val MAX_IMAGE_DIMENSION = 2048f
  }
}
