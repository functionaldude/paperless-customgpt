package com.functionaldude.paperless_customGPT.documents

import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PaperlessDocumentBinaryServiceTest {
  @TempDir
  lateinit var mediaRoot: Path

  @Test
  fun `find document prefers the original and exposes only the original filename basename`() {
    val original = createFile("documents/originals/stored/original.png", "original")
    createFile("documents/archive/stored/archive.pdf", "archive")
    val service = PaperlessDocumentBinaryService(
      documentDsl(
        filename = "stored/${original.fileName}",
        archiveFilename = "stored/archive.pdf",
        originalFilename = "uploads\\scans\\My scan #1.png",
        mimeType = "image/png",
      ),
      mediaRoot.toString(),
    )

    val lookup = service.findDocument(42)

    assertThat(lookup.document).isEqualTo(BinaryDocument("original".toByteArray(), "image/png", "My scan #1.png"))
    assertThat(lookup.searchedPaths).containsExactly(original.toAbsolutePath().normalize())
    assertThat(lookup.pathResolutionErrors).isEmpty()
  }

  @Test
  fun `find document falls back to archive when the original file is missing`() {
    createFile("documents/archive/2026/archive.pdf", "archived")
    val service = PaperlessDocumentBinaryService(
      documentDsl(
        filename = "2026/missing.pdf",
        archiveFilename = "2026/archive.pdf",
        originalFilename = "invoice.pdf",
        mimeType = "application/pdf",
      ),
      mediaRoot.toString(),
    )

    val lookup = service.findDocument(42)

    assertThat(lookup.document).isEqualTo(BinaryDocument("archived".toByteArray(), "application/pdf", "invoice.pdf"))
    assertThat(lookup.searchedPaths).containsExactly(
      mediaRoot.resolve("documents/originals/2026/missing.pdf").toAbsolutePath().normalize(),
      mediaRoot.resolve("documents/archive/2026/archive.pdf").toAbsolutePath().normalize(),
    )
    assertThat(lookup.pathResolutionErrors).isEmpty()
  }

  @Test
  fun `missing document files report both absolute paths searched`() {
    val service = PaperlessDocumentBinaryService(
      documentDsl(
        filename = "2026/missing original.pdf",
        archiveFilename = "2026/missing archive.pdf",
        originalFilename = "invoice.pdf",
        mimeType = "application/pdf",
      ),
      mediaRoot.toString(),
    )

    val lookup = service.findDocument(42)

    assertThat(lookup.document).isNull()
    assertThat(lookup.searchedPaths).containsExactly(
      mediaRoot.resolve("documents/originals/2026/missing original.pdf").toAbsolutePath().normalize(),
      mediaRoot.resolve("documents/archive/2026/missing archive.pdf").toAbsolutePath().normalize(),
    )
  }

  @Test
  fun `find document reads the original with accented characters in its stored path`() {
    createFile("documents/originals/2023/none/Fodor utca átadás.pdf", "document content")
    val service = PaperlessDocumentBinaryService(
      documentDsl(
        filename = "2023/none/Fodor utca átadás.pdf",
        archiveFilename = null,
        originalFilename = "Fodor utca átadás.pdf",
        mimeType = "application/pdf",
      ),
      mediaRoot.toString(),
    )

    val lookup = service.findDocument(432)

    assertThat(lookup.document).isEqualTo(
      BinaryDocument("document content".toByteArray(), "application/pdf", "Fodor utca átadás.pdf")
    )
    assertThat(lookup.pathResolutionErrors).isEmpty()
  }

  @Test
  fun `invalid stored path reports the path conversion error and filesystem encoding`() {
    val filename = "2023/none/invalid\u0000.pdf"
    val service = PaperlessDocumentBinaryService(
      documentDsl(
        filename = filename,
        archiveFilename = null,
        originalFilename = "invoice.pdf",
        mimeType = "application/pdf",
      ),
      mediaRoot.toString(),
    )

    val lookup = service.findDocument(432)

    assertThat(lookup.document).isNull()
    assertThat(lookup.searchedPaths).isEmpty()
    assertThat(lookup.pathResolutionErrors).hasSize(1)
    assertThat(lookup.pathResolutionErrors.single()).contains(
      "$mediaRoot/documents/originals/$filename",
      "Nul character not allowed",
      "sun.jnu.encoding=${System.getProperty("sun.jnu.encoding")}",
    )
  }

  @Test
  fun `invalid original path preserves its error when falling back to the archive`() {
    val archive = createFile("documents/archive/2026/archive.pdf", "archived")
    val service = PaperlessDocumentBinaryService(
      documentDsl(
        filename = "invalid\u0000.pdf",
        archiveFilename = "2026/archive.pdf",
        originalFilename = "invoice.pdf",
        mimeType = "application/pdf",
      ),
      mediaRoot.toString(),
    )

    val lookup = service.findDocument(42)

    assertThat(lookup.document).isEqualTo(BinaryDocument("archived".toByteArray(), "application/pdf", "invoice.pdf"))
    assertThat(lookup.searchedPaths).containsExactly(archive.toAbsolutePath().normalize())
    assertThat(lookup.pathResolutionErrors).hasSize(1)
    assertThat(lookup.pathResolutionErrors.single()).contains("Nul character not allowed")
  }

  @Test
  fun `absolute and traversal paths are rejected before falling back to the archive`() {
    val outside = createFile("outside.pdf", "outside")
    val archive = createFile("documents/archive/archive.pdf", "archived")

    listOf(outside.toAbsolutePath().toString(), "../../outside.pdf").forEach { filename ->
      val service = PaperlessDocumentBinaryService(
        documentDsl(
          filename = filename,
          archiveFilename = "archive.pdf",
          originalFilename = "invoice.pdf",
          mimeType = "application/pdf",
        ),
        mediaRoot.toString(),
      )

      val lookup = service.findDocument(42)

      assertThat(lookup.document).isEqualTo(BinaryDocument("archived".toByteArray(), "application/pdf", "invoice.pdf"))
      assertThat(lookup.searchedPaths).containsExactly(archive.toAbsolutePath().normalize())
      assertThat(lookup.pathResolutionErrors).isEmpty()
    }
  }

  @Test
  fun `symlinks outside the originals root are rejected but retain the searched path`() {
    val outside = createFile("outside.pdf", "outside")
    val archive = createFile("documents/archive/archive.pdf", "archived")
    val original = mediaRoot.resolve("documents/originals/linked.pdf")
    Files.createDirectories(original.parent)
    Files.createSymbolicLink(original, outside.toAbsolutePath())
    val service = PaperlessDocumentBinaryService(
      documentDsl(
        filename = "linked.pdf",
        archiveFilename = "archive.pdf",
        originalFilename = "invoice.pdf",
        mimeType = "application/pdf",
      ),
      mediaRoot.toString(),
    )

    val lookup = service.findDocument(42)

    assertThat(lookup.document).isEqualTo(BinaryDocument("archived".toByteArray(), "application/pdf", "invoice.pdf"))
    assertThat(lookup.searchedPaths).containsExactly(
      original.toAbsolutePath().normalize(),
      archive.toAbsolutePath().normalize(),
    )
    assertThat(lookup.pathResolutionErrors).isEmpty()
  }

  private fun createFile(relativePath: String, content: String): Path {
    val path = mediaRoot.resolve(relativePath)
    Files.createDirectories(path.parent)
    return Files.writeString(path, content)
  }

  private fun documentDsl(
    filename: String?,
    archiveFilename: String?,
    originalFilename: String?,
    mimeType: String?,
  ): DSLContext {
    val effectiveFilename = DSL.field("effective_filename", String::class.java)
    val effectiveArchiveFilename = DSL.field("effective_archive_filename", String::class.java)
    val effectiveOriginalFilename = DSL.field("effective_original_filename", String::class.java)
    val effectiveMimeType = DSL.field("effective_mime_type", String::class.java)
    val fields = arrayOf(
      effectiveFilename,
      effectiveArchiveFilename,
      effectiveOriginalFilename,
      effectiveMimeType,
    )
    val resultDsl = DSL.using(SQLDialect.POSTGRES)
    val result = resultDsl.newResult(*fields)
    val record = resultDsl.newRecord(*fields)
    record.set(effectiveFilename, filename)
    record.set(effectiveArchiveFilename, archiveFilename)
    record.set(effectiveOriginalFilename, originalFilename)
    record.set(effectiveMimeType, mimeType)
    result.add(record)

    return DSL.using(MockConnection { arrayOf(MockResult(1, result)) }, SQLDialect.POSTGRES)
  }
}
