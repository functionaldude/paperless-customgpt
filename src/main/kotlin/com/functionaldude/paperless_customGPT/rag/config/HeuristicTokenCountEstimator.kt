package com.functionaldude.paperless_customGPT.rag.config

import dev.langchain4j.data.message.*
import dev.langchain4j.model.TokenCountEstimator
import kotlin.math.ceil

class HeuristicTokenCountEstimator(
  private val estimatedCharsPerToken: Double = 3.0,
) : TokenCountEstimator {

  init {
    require(estimatedCharsPerToken > 0) {
      "estimatedCharsPerToken must be > 0, but was $estimatedCharsPerToken"
    }
  }

  override fun estimateTokenCountInText(text: String): Int {
    if (text.isBlank()) return 0
    return ceil(text.length / estimatedCharsPerToken).toInt()
  }

  override fun estimateTokenCountInMessage(message: ChatMessage): Int {
    return when (message) {
      is SystemMessage -> estimateTokenCountInText(message.text())
      is UserMessage -> estimateTokenCountInText(extractUserMessageText(message))
      is AiMessage -> estimateTokenCountInText(message.text() ?: "")
      is ToolExecutionResultMessage -> estimateTokenCountInText(message.text())
      else -> estimateTokenCountInText(message.toString())
    }
  }

  override fun estimateTokenCountInMessages(messages: Iterable<ChatMessage>): Int {
    return messages.sumOf { estimateTokenCountInMessage(it) }
  }

  private fun extractUserMessageText(message: UserMessage): String {
    if (message.hasSingleText()) return message.singleText()

    return message.contents()
      .asSequence()
      .filterIsInstance<TextContent>()
      .joinToString(separator = "\n") { it.text() }
  }
}
