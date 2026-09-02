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
  val fileName: String,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BinaryDocument

    if (!content.contentEquals(other.content)) return false
    if (mimeType != other.mimeType) return false
    if (fileName != other.fileName) return false

    return true
  }

  override fun hashCode(): Int {
    var result = content.contentHashCode()
    result = 31 * result + mimeType.hashCode()
    result = 31 * result + fileName.hashCode()
    return result
  }
}

@Service
class PaperlessDocumentBinaryService(
  private val dsl: DSLContext,
  @Value("\${paperless.media-root}") mediaRoot: String,
) {
  private val documentsRoot = Path.of(mediaRoot.trim().removeSuffix("/"), "documents")
    .toAbsolutePath()
    .normalize()
  private val originalsRoot = documentsRoot.resolve("originals")
  private val archiveRoot = documentsRoot.resolve("archive")

  fun findDocument(documentId: Int): BinaryDocument? {
    val latestVersion = DOCUMENTS_DOCUMENT.`as`("latest_binary_version")
    val latestVersionId: Field<Int?> = dsl
      .select(latestVersion.ID)
      .from(latestVersion)
      .where(latestVersion.ROOT_DOCUMENT_ID.eq(DOCUMENTS_DOCUMENT.ID))
      .and(latestVersion.DELETED_AT.isNull)
      .orderBy(latestVersion.ID.desc())
      .limit(1)
      .asField<Int?>()
    val effectiveDocument = DOCUMENTS_DOCUMENT.`as`("effective_binary_document")
    val effectiveMimeType = coalesce(
      effectiveDocument.MIME_TYPE,
      inline(PaperlessDocumentService.DEFAULT_MIME_TYPE),
    )

    val record = dsl
      .select(
        effectiveDocument.FILENAME.`as`("effective_filename"),
        effectiveDocument.ARCHIVE_FILENAME.`as`("effective_archive_filename"),
        effectiveDocument.ORIGINAL_FILENAME.`as`("effective_original_filename"),
        effectiveMimeType.`as`("effective_mime_type"),
      )
      .from(DOCUMENTS_DOCUMENT)
      .join(effectiveDocument)
      .on(effectiveDocument.ID.eq(coalesce(latestVersionId, DOCUMENTS_DOCUMENT.ID)))
      .where(DOCUMENTS_DOCUMENT.ID.eq(documentId))
      .and(DOCUMENTS_DOCUMENT.ROOT_DOCUMENT_ID.isNull)
      .and(DOCUMENTS_DOCUMENT.DELETED_AT.isNull)
      .fetchOne() ?: return null

    val filename = record.get("effective_filename", String::class.java)
    val archiveFilename = record.get("effective_archive_filename", String::class.java)
    val originalFilename = record.get("effective_original_filename", String::class.java) ?: return null
    val fileName = originalFilename
      .replace('\\', '/')
      .substringAfterLast('/')
      .takeIf { it.isNotBlank() }
      ?: return null
    val mimeType = record.get("effective_mime_type", String::class.java)
      ?: PaperlessDocumentService.DEFAULT_MIME_TYPE
    val source = filename?.let { resolveFile(originalsRoot, it) }
      ?: archiveFilename?.let { resolveFile(archiveRoot, it) }
      ?: return null

    return try {
      BinaryDocument(Files.readAllBytes(source), mimeType, fileName)
    } catch (_: IOException) {
      null
    }
  }

  private fun resolveFile(root: Path, filename: String): Path? {
    val relativePath = try {
      Path.of(filename)
    } catch (_: RuntimeException) {
      return null
    }
    if (relativePath.isAbsolute) return null

    val candidate = root.resolve(relativePath).normalize()
    if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) return null

    return try {
      val realRoot = root.toRealPath()
      candidate.toRealPath().takeIf { it.startsWith(realRoot) && Files.isRegularFile(it) }
    } catch (_: IOException) {
      null
    }
  }
}
