package com.functionaldude.paperless_customGPT.rag

import com.functionaldude.paperless_customGPT.rag.internal.EmbeddingDimensionReducer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class EmbeddingDimensionReducerTest {

  @Test
  fun `reduce truncates vectors larger than target`() {
    val reducer = EmbeddingDimensionReducer(targetDimensions = 3)

    val reduced = reducer.reduce(floatArrayOf(1f, 2f, 3f, 4f, 5f))

    val norm = sqrt(14.0).toFloat()
    assertThat(reduced).containsExactly(1f / norm, 2f / norm, 3f / norm)
  }

  @Test
  fun `reduce keeps vectors with exact target size`() {
    val reducer = EmbeddingDimensionReducer(targetDimensions = 3)

    val reduced = reducer.reduce(floatArrayOf(1f, 2f, 3f))

    val norm = sqrt(14.0).toFloat()
    assertThat(reduced).containsExactly(1f / norm, 2f / norm, 3f / norm)
  }

  @Test
  fun `reduce pads vectors smaller than target with zeros`() {
    val reducer = EmbeddingDimensionReducer(targetDimensions = 5)

    val reduced = reducer.reduce(floatArrayOf(1f, 2f, 3f))

    val norm = sqrt(14.0).toFloat()
    assertThat(reduced).containsExactly(1f / norm, 2f / norm, 3f / norm, 0f, 0f)
  }

  @Test
  fun `reduce returns zero vector when input is all zeros`() {
    val reducer = EmbeddingDimensionReducer(targetDimensions = 3)

    val reduced = reducer.reduce(floatArrayOf(0f, 0f, 0f, 0f))

    assertThat(reduced).containsExactly(0f, 0f, 0f)
  }

  @Test
  fun `reduce output is unit norm for non-zero input`() {
    val reducer = EmbeddingDimensionReducer(targetDimensions = 3)

    val reduced = reducer.reduce(floatArrayOf(2f, 0f, 0f))
    val norm = sqrt(reduced.fold(0.0) { acc, value -> acc + (value * value) }.toFloat())

    assertThat(norm).isCloseTo(1.0f, Offset.offset(1e-6f))
  }
}
