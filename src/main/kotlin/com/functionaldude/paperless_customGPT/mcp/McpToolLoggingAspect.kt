package com.functionaldude.paperless_customGPT.mcp

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.json.JsonMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springaicommunity.mcp.annotation.McpTool
import org.springframework.ai.util.JacksonUtils
import org.springframework.stereotype.Component

@Aspect
@Component
class McpToolLoggingAspect {
  private val log = LoggerFactory.getLogger("mcp.tools")
  private val objectMapper = JsonMapper.builder()
    .addModules(JacksonUtils.instantiateAvailableModules())
    .build()

  @Around("@annotation(mcpTool)")
  fun logToolCall(joinPoint: ProceedingJoinPoint, mcpTool: McpTool): Any? {
    if (log.isInfoEnabled) {
      val signature = joinPoint.signature as MethodSignature
      val toolName = mcpTool.name.takeIf { it.isNotBlank() } ?: signature.method.name

      log.info("MCP tool called: {}", toolName)
      if (log.isDebugEnabled) {
        log.debug("MCP tool input: tool={} params={}", toolName, serializeParameters(signature, joinPoint.args))
      }
    }

    return joinPoint.proceed()
  }

  private fun serializeParameters(signature: MethodSignature, arguments: Array<Any?>): String {
    val parameters = signature.method.parameters
      .mapIndexed { index, parameter -> parameter.name to arguments.getOrNull(index) }
      .toMap(LinkedHashMap())

    return try {
      objectMapper.writeValueAsString(parameters)
    } catch (_: JsonProcessingException) {
      "<unserializable>"
    }
  }
}
