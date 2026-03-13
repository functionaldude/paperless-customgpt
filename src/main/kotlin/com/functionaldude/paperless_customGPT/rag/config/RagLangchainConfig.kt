package com.functionaldude.paperless_customGPT.rag.config

import dev.langchain4j.data.document.DocumentParser
import dev.langchain4j.data.document.DocumentSplitter
import dev.langchain4j.data.document.parser.TextDocumentParser
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
import dev.langchain4j.model.TokenCountEstimator
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class RagLangchainConfig {

  @Bean
  fun embeddingModel(
    @Value("\${OPENAI_BASE_URL:http://localhost:1234/v1}") baseUrl: String,
    @Value("\${OPENAI_MODEL_NAME:text-embedding-multilingual-e5-base}") modelName: String,
    @Value("\${OPENAI_API_KEY:lm-studio}") apiKey: String,
    @Value("\${OPENAI_FORCE_HTTP1:false}") forceHttp1: Boolean,
  ): EmbeddingModel {
    val httpClientBuilder = HttpClient.newBuilder().apply {
      if (forceHttp1) this.version(HttpClient.Version.HTTP_1_1) // LM Studio does not support HTTP2 yet
    }

    return OpenAiEmbeddingModel.builder()
      .baseUrl(baseUrl)
      .apiKey(apiKey)
      .modelName(modelName)
      .timeout(Duration.ofMinutes(5))
      .httpClientBuilder(
        JdkHttpClientBuilder().httpClientBuilder(httpClientBuilder)
      )
      .logRequests(false)
      .logResponses(false)
      .build()
  }

  @Bean
  fun documentParser(): DocumentParser = TextDocumentParser()

  @Bean
  fun tokenCountEstimator(
    @Value("\${rag.splitter.estimated-chars-per-token:4.0}") estimatedCharsPerToken: Double,
  ): TokenCountEstimator {
    return HeuristicTokenCountEstimator(estimatedCharsPerToken)
  }

  @Bean
  fun documentSplitter(
    tokenCountEstimator: TokenCountEstimator,
    @Value("\${rag.splitter.chunk-size-tokens:512}") chunkSizeTokens: Int,
    @Value("\${rag.splitter.overlap-size-tokens:128}") overlapSizeTokens: Int,
  ): DocumentSplitter {
    require(chunkSizeTokens > 0) { "rag.splitter.chunk-size-tokens must be > 0, but was $chunkSizeTokens" }
    require(overlapSizeTokens >= 0) { "rag.splitter.overlap-size-tokens must be >= 0, but was $overlapSizeTokens" }
    require(overlapSizeTokens < chunkSizeTokens) {
      "rag.splitter.overlap-size-tokens must be < rag.splitter.chunk-size-tokens ($chunkSizeTokens), but was $overlapSizeTokens"
    }

    return DocumentSplitters.recursive(
      chunkSizeTokens,
      overlapSizeTokens,
      tokenCountEstimator,
    )
  }
}
