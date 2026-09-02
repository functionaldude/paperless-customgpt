package com.functionaldude.paperless_customGPT.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class McpToolLoggingAspectTest {

  private val logger = LoggerFactory.getLogger("mcp.tools") as Logger
  private val originalLevel = logger.level

  @AfterEach
  fun restoreLoggerLevel() {
    logger.level = originalLevel
  }

  @Test
  fun `off does not log tool calls`(output: CapturedOutput) {
    logger.level = Level.OFF

    createTools().search("invoice", 3)

    assertThat(output.out).doesNotContain("MCP tool")
  }

  @Test
  fun `info logs only the tool name`(output: CapturedOutput) {
    logger.level = Level.INFO

    createTools().search("invoice", 3)

    assertThat(output.out).contains("MCP tool called: searchRag")
    assertThat(output.out).doesNotContain("MCP tool input")
    assertThat(output.out).doesNotContain("invoice")
  }

  @Test
  fun `debug logs the tool name and input parameters`(output: CapturedOutput) {
    logger.level = Level.DEBUG

    createTools().search("invoice", 3)

    assertThat(output.out).contains("MCP tool called: searchRag")
    assertThat(output.out).contains("MCP tool input: tool=searchRag")
    assertThat(output.out).contains("\"query\":\"invoice\"")
    assertThat(output.out).contains("\"topK\":3")
  }

  private fun createTools(): TestTools {
    val proxyFactory = AspectJProxyFactory(TestTools())
    proxyFactory.addAspect(McpToolLoggingAspect())
    return proxyFactory.getProxy() as TestTools
  }

  open class TestTools {
    @McpTool(name = "searchRag", description = "Test tool")
    open fun search(query: String, topK: Int): String {
      return "$query:$topK"
    }
  }
}
