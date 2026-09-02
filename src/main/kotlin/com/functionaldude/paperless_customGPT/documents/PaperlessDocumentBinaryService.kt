package com.functionaldude.paperless_customGPT.documents

import com.functionaldude.paperless.jooq.public.tables.references.DOCUMENTS_DOCUMENT
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.coalesce
import org.jooq.impl.DSL.inline
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

data class BinaryDocument(
  val content: ByteArray,
  val mimeType: String,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BinaryDocument

    if (!content.contentEquals(other.content)) return false
    if (mimeType != other.mimeType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = content.contentHashCode()
    result = 31 * result + mimeType.hashCode()
    return result
  }
}

@Service
class PaperlessDocumentBinaryService(
  private val dsl: DSLContext,
  @Value("\${paperless.media-root}") mediaRoot: String,
) {
  private val documentRoot = mediaRoot.trim().removeSuffix("/").plus("/documents/originals")
  private val mediaRoot = Path.of(documentRoot).toAbsolutePath().normalize()

  fun findDocument(documentId: Int): BinaryDocument? {
    val latestVersion = DOCUMENTS_DOCUMENT.`as`("latest_binary_version")
    val latestFilename: Field<String?> = dsl
      .select(coalesce(latestVersion.FILENAME, latestVersion.ARCHIVE_FILENAME))
      .from(latestVersion)
      .where(latestVersion.ROOT_DOCUMENT_ID.eq(DOCUMENTS_DOCUMENT.ID))
      .and(latestVersion.DELETED_AT.isNull)
      .orderBy(latestVersion.ID.desc())
      .limit(1)
      .asField<String?>()
    val latestMimeType: Field<String?> = dsl
      .select(latestVersion.MIME_TYPE)
      .from(latestVersion)
      .where(latestVersion.ROOT_DOCUMENT_ID.eq(DOCUMENTS_DOCUMENT.ID))
      .and(latestVersion.DELETED_AT.isNull)
      .orderBy(latestVersion.ID.desc())
      .limit(1)
      .asField<String?>()
    val effectiveMimeType = coalesce(
      latestMimeType,
      DOCUMENTS_DOCUMENT.MIME_TYPE,
      inline(PaperlessDocumentService.DEFAULT_MIME_TYPE),
    )

    val record = dsl
      .select(
        coalesce(
          latestFilename,
          DOCUMENTS_DOCUMENT.FILENAME,
          DOCUMENTS_DOCUMENT.ARCHIVE_FILENAME
        ).`as`("effective_filename"),
        effectiveMimeType.`as`("effective_mime_type"),
      )
      .from(DOCUMENTS_DOCUMENT)
      .where(DOCUMENTS_DOCUMENT.ID.eq(documentId))
      .and(DOCUMENTS_DOCUMENT.ROOT_DOCUMENT_ID.isNull)
      .and(DOCUMENTS_DOCUMENT.DELETED_AT.isNull)
      .fetchOne() ?: return null

    val filename = record.get("effective_filename", String::class.java) ?: return null
    val mimeType = record.get("effective_mime_type", String::class.java)
      ?: PaperlessDocumentService.DEFAULT_MIME_TYPE
    val path = resolveFile(filename) ?: return null

    return try {
      BinaryDocument(Files.readAllBytes(path), mimeType)
    } catch (_: IOException) {
      null
    }
  }

  private fun resolveFile(filename: String): Path? {
    val relativePath = try {
      Path.of(filename)
    } catch (_: RuntimeException) {
      return null
    }
    if (relativePath.isAbsolute) return null

    val candidate = mediaRoot.resolve(relativePath).normalize()
    if (!candidate.startsWith(mediaRoot) || !Files.isRegularFile(candidate)) return null

    return try {
      val realRoot = mediaRoot.toRealPath()
      candidate.toRealPath().takeIf { it.startsWith(realRoot) && Files.isRegularFile(it) }
    } catch (_: IOException) {
      null
    }
  }
}
