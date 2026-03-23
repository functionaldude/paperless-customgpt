package com.functionaldude.paperless_customGPT

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class McpOAuthIntegrationTests(
  @Autowired private val mockMvc: MockMvc,
) {

  @Test
  fun `oauth authorization server metadata is exposed`() {
    val response = mockMvc.perform(get("/.well-known/oauth-authorization-server"))
      .andExpect(status().isOk)
      .andReturn()
      .response

    val body = response.contentAsString
    assertThat(body).contains("authorization_endpoint")
    assertThat(body).contains("token_endpoint")
    assertThat(body).contains("registration_endpoint")
  }

  @Test
  fun `oauth protected resource metadata is exposed for mcp`() {
    val candidatePaths = listOf(
      "/.well-known/oauth-protected-resource/mcp",
      "/.well-known/oauth-protected-resource",
    )

    val successfulResponse = candidatePaths
      .asSequence()
      .mapNotNull { path ->
        runCatching {
          mockMvc.perform(get(path)).andReturn().response
        }.getOrNull()
      }
      .firstOrNull { it.status == 200 }

    assertThat(successfulResponse)
      .withFailMessage("Expected one protected resource metadata endpoint to return 200")
      .isNotNull

    assertThat(successfulResponse!!.contentAsString).contains("resource")
    assertThat(successfulResponse.contentAsString).contains("bearer_methods_supported")
  }

  @Test
  fun `mcp endpoint requires authentication`() {
    val response = mockMvc.perform(
      post("/mcp")
        .contentType("application/json")
        .content("{}")
    )
      .andExpect(status().isUnauthorized)
      .andReturn()
      .response

    val header = response.getHeader("WWW-Authenticate")
    val body = response.contentAsString

    assertThat(header.isNullOrBlank()).isFalse()
    assertThat(header).contains("resource_metadata=")
    assertThat(header).contains("/.well-known/oauth-protected-resource/mcp")
    assertThat(body).isBlank()
  }
}
