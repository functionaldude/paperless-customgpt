package com.functionaldude.paperless_customGPT.mcp

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
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

@Configuration
class McpToolAuthMetadataConfig {

  @Bean(name = ["mcpServerObjectMapper"], defaultCandidate = false)
  fun mcpServerObjectMapper(): JsonMapper {
    val module = SimpleModule()
      .addSerializer(McpSchema.Tool::class.java, McpToolSerializer())

    return JacksonUtils.getDefaultJsonMapper()
      .rebuild()
      .addModule(module)
      .build()
  }

  companion object {
    @Bean
    @JvmStatic
    fun mcpToolSecuritySchemesPostProcessor(
      appProperties: AppProperties,
      @Qualifier("mcpServerObjectMapper") objectMapper: JsonMapper,
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
      objectMapper: JsonMapper,
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
      objectMapper: JsonMapper,
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
      objectMapper: JsonMapper,
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

private class McpToolSerializer : ValueSerializer<McpSchema.Tool>() {
  override fun serialize(
    value: McpSchema.Tool,
    gen: JsonGenerator,
    serializers: SerializationContext,
  ) {
    gen.writeStartObject()
    gen.writeStringProperty("name", value.name())
    value.title()?.let { gen.writeStringProperty("title", it) }
    value.description()?.let { gen.writeStringProperty("description", it) }
    value.inputSchema()?.let { gen.writePOJOProperty("inputSchema", it) }
    value.outputSchema()?.let { gen.writePOJOProperty("outputSchema", it) }
    value.annotations()?.let { gen.writePOJOProperty("annotations", it) }
    value.meta()?.get("securitySchemes")?.let { gen.writePOJOProperty("securitySchemes", it) }
    value.meta()?.takeIf { it.isNotEmpty() }?.let { gen.writePOJOProperty("_meta", it) }
    gen.writeEndObject()
  }
}
