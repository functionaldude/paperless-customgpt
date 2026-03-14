package com.functionaldude.paperless_customGPT.rag

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.output.Response
import dev.langchain4j.model.scoring.ScoringModel
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class LocalAiRerankerScoringModel(
  baseUrl: String,
  private val apiKey: String,
  private val modelName: String,
  private val timeout: Duration,
  private val httpClient: HttpClient,
  private val objectMapper: ObjectMapper,
) : ScoringModel {
  private val rerankUri = URI.create("${baseUrl.trimEnd('/')}/rerank")

  override fun scoreAll(segments: List<TextSegment>, query: String): Response<List<Double>> {
    if (segments.isEmpty()) {
      return Response.from(emptyList())
    }

    val requestBody = objectMapper.writeValueAsString(
      LocalAiRerankRequest(
        model = modelName,
        query = query,
        documents = segments.map(TextSegment::text),
        topN = segments.size,
      )
    )

    val requestBuilder = HttpRequest.newBuilder(rerankUri)
      .timeout(timeout)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(requestBody))

    if (apiKey.isNotBlank()) {
      requestBuilder.header("Authorization", "Bearer $apiKey")
    }

    val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    require(response.statusCode() in 200..299) {
      "LocalAI reranker request failed with HTTP ${response.statusCode()}: ${response.body()}"
    }

    return Response.from(parseScores(response.body(), segments.size))
  }

  private fun parseScores(responseBody: String, expectedSize: Int): List<Double> {
    val responseJson = objectMapper.readTree(responseBody)
    val items = when {
      responseJson.has("results") -> responseJson["results"]
      responseJson.has("data") -> responseJson["data"]
      else -> throw IllegalStateException("LocalAI reranker response did not contain 'results' or 'data'")
    }

    require(items.isArray) { "LocalAI reranker response payload was not an array" }

    val scores = MutableList(expectedSize) { Double.NEGATIVE_INFINITY }
    items.forEach { item ->
      val index = item.path("index").asInt(-1)
      val score = item.scoreValue() ?: return@forEach
      if (index in 0 until expectedSize) {
        scores[index] = score
      }
    }

    require(scores.any { it > Double.NEGATIVE_INFINITY }) { "LocalAI reranker response did not contain any valid scores" }
    return scores
  }

  private fun JsonNode.scoreValue(): Double? {
    return when {
      has("relevance_score") -> get("relevance_score").asDouble()
      has("score") -> get("score").asDouble()
      else -> null
    }
  }

  private data class LocalAiRerankRequest(
    val model: String,
    val query: String,
    val documents: List<String>,
    @JsonProperty("top_n")
    val topN: Int,
  )
}
