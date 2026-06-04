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

@SpringBootTest(
  properties = [
    "app.public-url=https://paperless-gpt.example.test",
    "app.auth.issuer-uri=https://idp.example.test/application/o/paperless/",
    "app.auth.jwk-set-uri=https://idp.example.test/application/o/paperless/jwks/",
    "app.auth.audience=https://paperless-gpt.example.test/mcp",
    "spring.flyway.enabled=false",
  ]
)
@AutoConfigureMockMvc
class McpOAuthIntegrationTests(
  @Autowired private val mockMvc: MockMvc,
) {

  @Test
  fun `embedded oauth authorization server metadata is not exposed`() {
    val response = mockMvc.perform(get("/.well-known/oauth-authorization-server"))
      .andExpect(status().isUnauthorized)
      .andReturn()
      .response

    val body = response.contentAsString
    assertThat(body).doesNotContain("registration_endpoint")
  }

  @Test
  fun `oauth protected resource metadata advertises oidc issuer for mcp`() {
    val response = mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp"))
      .andExpect(status().isOk)
      .andReturn()
      .response

    assertThat(response.contentAsString).contains("\"resource\":\"https://paperless-gpt.example.test/mcp\"")
    assertThat(response.contentAsString).contains("\"authorization_servers\":[\"https://idp.example.test/application/o/paperless/\"]")
    assertThat(response.contentAsString).contains("\"scopes_supported\":[\"openid\",\"profile\",\"email\"]")
    assertThat(response.contentAsString).contains("bearer_methods_supported")
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
