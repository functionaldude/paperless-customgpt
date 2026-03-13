package com.functionaldude.paperless_customGPT.rag.internal

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.math.min
import kotlin.math.sqrt

@Component
class EmbeddingDimensionReducer(
  @Value("\${rag.embedding-dimensions:1536}") private val targetDimensions: Int,
) {
  init {
    require(targetDimensions > 0) { "rag.embedding-dimensions must be > 0, but was $targetDimensions" }
  }

  fun reduce(vector: FloatArray): FloatArray {
    val reduced = FloatArray(targetDimensions)
    // Zero-padding smaller embeddings keeps pairwise cosine relationships intact when all vectors are padded the same way.
    System.arraycopy(vector, 0, reduced, 0, min(vector.size, targetDimensions))

    return normalizeL2(reduced)
  }

  /**
   * Keep vectors on unit length after dimensionality reduction so cosine-style similarity
   * depends on direction, not raw magnitude changes introduced by truncation.
   */
  private fun normalizeL2(vector: FloatArray): FloatArray {
    val normSquared = vector.fold(0.0) { acc, value -> acc + (value * value) }
    if (normSquared == 0.0) return vector

    val norm = sqrt(normSquared).toFloat()
    for (i in vector.indices) {
      vector[i] /= norm
    }
    return vector
  }
}
