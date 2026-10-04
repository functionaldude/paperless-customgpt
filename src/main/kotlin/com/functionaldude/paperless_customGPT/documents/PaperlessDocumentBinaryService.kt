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
import java.nio.file.InvalidPathException
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

data class BinaryDocumentLookup(
  val document: BinaryDocument? = null,
  val searchedPaths: List<Path> = emptyList(),
  val pathResolutionErrors: List<String> = emptyList(),
)

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

  fun findDocument(documentId: Int): BinaryDocumentLookup {
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
      .fetchOne() ?: return BinaryDocumentLookup(null)

    val filename = record.get("effective_filename", String::class.java)
    val archiveFilename = record.get("effective_archive_filename", String::class.java)
    val originalFilename =
      record.get("effective_original_filename", String::class.java) ?: return BinaryDocumentLookup(null)
    val fileName = originalFilename
      .replace('\\', '/')
      .substringAfterLast('/')
      .takeIf { it.isNotBlank() }
      ?: return BinaryDocumentLookup(null)
    val mimeType = record.get("effective_mime_type", String::class.java)
      ?: PaperlessDocumentService.DEFAULT_MIME_TYPE

    val resolutions = buildList {
      val originalResolution = filename?.let { resolveFile(originalsRoot, it) }
      if (originalResolution != null) add(originalResolution)

      val archiveResolution = archiveFilename?.let { resolveFile(archiveRoot, it) }
      if (archiveResolution != null) add(archiveResolution)
    }
    val source = resolutions
      .filterIsInstance<FileResolution.Resolved>()
      .firstOrNull()?.path
      ?: return BinaryDocumentLookup(
        searchedPaths = resolutions.mapNotNull { it.searchedPath },
        pathResolutionErrors = resolutions.filterIsInstance<FileResolution.InvalidPath>().map { it.error }
      )

    val document = try {
      BinaryDocument(Files.readAllBytes(source), mimeType, fileName)
    } catch (_: IOException) {
      null
    }
    return BinaryDocumentLookup(
      document = document,
      searchedPaths = resolutions.mapNotNull { it.searchedPath },
      pathResolutionErrors = resolutions.filterIsInstance<FileResolution.InvalidPath>().map { it.error }
    )
  }

  private fun resolveFile(root: Path, filename: String): FileResolution {
    val relativePath = try {
      Path.of(filename)
    } catch (exception: InvalidPathException) {
      return FileResolution.InvalidPath(
        "Cannot resolve stored path '$root/$filename': ${exception.reason} " +
            "(sun.jnu.encoding=${System.getProperty("sun.jnu.encoding")})"
      )
    }
    if (relativePath.isAbsolute) return FileResolution.Rejected()

    val candidate = root.resolve(relativePath).normalize()
    if (!candidate.startsWith(root)) return FileResolution.Rejected()
    if (!Files.isRegularFile(candidate)) return FileResolution.Unavailable(candidate)

    return try {
      val realRoot = root.toRealPath()
      val realCandidate = candidate.toRealPath()
      when {
        !realCandidate.startsWith(realRoot) -> FileResolution.Rejected(candidate)
        !Files.isRegularFile(realCandidate) -> FileResolution.Unavailable(candidate)
        else -> FileResolution.Resolved(realCandidate, candidate)
      }
    } catch (_: IOException) {
      FileResolution.Unavailable(candidate)
    }
  }

  private sealed class FileResolution {
    open val searchedPath: Path? = null

    data class Resolved(val path: Path, override val searchedPath: Path) : FileResolution()

    data class Unavailable(override val searchedPath: Path) : FileResolution()

    data class InvalidPath(val error: String) : FileResolution()

    data class Rejected(override val searchedPath: Path? = null) : FileResolution()
  }
}
