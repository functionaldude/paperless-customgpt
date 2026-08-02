package com.functionaldude.paperless_customGPT.mcp

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.functionaldude.paperless_customGPT.security.AppProperties
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.springframework.ai.util.JacksonUtils
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpToolAuthMetadataConfig {

  @Bean(name = ["mcpServerObjectMapper"], defaultCandidate = false)
  fun mcpServerObjectMapper(): ObjectMapper {
    val module = SimpleModule()
      .addSerializer(McpSchema.Tool::class.java, McpToolSerializer())

    return JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
      .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .serializationInclusion(JsonInclude.Include.NON_NULL)
      .addModules(JacksonUtils.instantiateAvailableModules())
      .addModule(module)
      .build()
  }

  companion object {
    @Bean
    @JvmStatic
    fun mcpToolSecuritySchemesPostProcessor(
      appProperties: AppProperties,
      @Qualifier("mcpServerObjectMapper") objectMapper: ObjectMapper,
    ): BeanPostProcessor {
      val scopes = appProperties.auth.scopes

      return object : BeanPostProcessor {
        override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
          if (bean !is List<*> || bean.isEmpty()) {
            return bean
          }

          if (bean.all { it is McpServerFeatures.SyncToolSpecification }) {
            return bean.map {
              secureSyncToolSpecification(it as McpServerFeatures.SyncToolSpecification, scopes, objectMapper)
            }
          }

          if (bean.all { it is McpServerFeatures.AsyncToolSpecification }) {
            return bean.map {
              secureAsyncToolSpecification(it as McpServerFeatures.AsyncToolSpecification, scopes, objectMapper)
            }
          }

          return bean
        }
      }
    }

    private fun secureSyncToolSpecification(
      specification: McpServerFeatures.SyncToolSpecification,
      scopes: List<String>,
      objectMapper: ObjectMapper,
    ): McpServerFeatures.SyncToolSpecification = McpServerFeatures.SyncToolSpecification.builder()
      .tool(withOAuthSecurityScheme(specification.tool(), scopes))
      .callHandler { exchange, request ->
        addCompatibilityTextContent(
          specification.tool().name(),
          specification.callHandler().apply(exchange, request),
          objectMapper,
        )
      }
      .build()

    private fun secureAsyncToolSpecification(
      specification: McpServerFeatures.AsyncToolSpecification,
      scopes: List<String>,
      objectMapper: ObjectMapper,
    ): McpServerFeatures.AsyncToolSpecification = McpServerFeatures.AsyncToolSpecification.builder()
      .tool(withOAuthSecurityScheme(specification.tool(), scopes))
      .callHandler { exchange, request ->
        specification.callHandler().apply(exchange, request)
          .map { result -> addCompatibilityTextContent(specification.tool().name(), result, objectMapper) }
      }
      .build()

    private fun addCompatibilityTextContent(
      toolName: String,
      result: CallToolResult,
      objectMapper: ObjectMapper,
    ): CallToolResult {
      if (toolName !in CHATGPT_COMPATIBILITY_TOOL_NAMES || result.isError() == true || result.structuredContent() == null) {
        return result
      }

      val json = objectMapper.writeValueAsString(result.structuredContent())
      return CallToolResult.builder()
        .content(result.content().orEmpty() + TextContent(json))
        .isError(result.isError())
        .structuredContent(result.structuredContent())
        .meta(result.meta())
        .build()
    }

    private fun withOAuthSecurityScheme(tool: McpSchema.Tool, scopes: List<String>): McpSchema.Tool {
      val metadata = linkedMapOf<String, Any>().apply {
        tool.meta()?.let { this.putAll(it) }
        this["securitySchemes"] = listOf(
          linkedMapOf("type" to "oauth2", "scopes" to scopes)
        )
      }

      return McpSchema.Tool(
        tool.name(),
        tool.title(),
        tool.description(),
        tool.inputSchema(),
        tool.outputSchema(),
        tool.annotations(),
        metadata,
      )
    }

    private val CHATGPT_COMPATIBILITY_TOOL_NAMES = setOf("search", "fetch")
  }
}

private class McpToolSerializer : JsonSerializer<McpSchema.Tool>() {
  override fun serialize(
    value: McpSchema.Tool,
    gen: JsonGenerator,
    serializers: SerializerProvider,
  ) {
    gen.writeStartObject()
    gen.writeStringField("name", value.name())
    value.title()?.let { gen.writeStringField("title", it) }
    value.description()?.let { gen.writeStringField("description", it) }
    value.inputSchema()?.let { gen.writeObjectField("inputSchema", it) }
    value.outputSchema()?.let { gen.writeObjectField("outputSchema", it) }
    value.annotations()?.let { gen.writeObjectField("annotations", it) }
    value.meta()?.get("securitySchemes")?.let { gen.writeObjectField("securitySchemes", it) }
    value.meta()?.takeIf { it.isNotEmpty() }?.let { gen.writeObjectField("_meta", it) }
    gen.writeEndObject()
  }
}
