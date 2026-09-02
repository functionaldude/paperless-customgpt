package com.functionaldude.paperless_customGPT

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
  properties = [
    "app.public-url=https://paperless-gpt.example.test",
    "app.auth.issuer-uri=https://idp.example.test/application/o/paperless/",
    "app.auth.audience=https://paperless-gpt.example.test/mcp",
    "app.auth.scopes=openid,paperless_gpt",
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
    assertThat(response.contentAsString)
      .contains("\"scopes_supported\":[\"openid\",\"paperless_gpt\"]")
    assertThat(response.contentAsString).contains("bearer_methods_supported")
    assertThat(response.contentAsString).doesNotContain("tls_client_certificate_bound_access_tokens")
  }

  @Test
  fun `non mcp endpoints require authentication`() {
    val response = mockMvc.perform(
      get("/documents")
    )
      .andExpect(status().isUnauthorized)
      .andReturn()
      .response

    val header = response.getHeader("WWW-Authenticate")
    val body = response.contentAsString

    assertThat(header.isNullOrBlank()).isFalse()
    assertThat(header).contains("""resource_metadata="https://paperless-gpt.example.test/.well-known/oauth-protected-resource/mcp"""")
    assertThat(header).contains("""scope="openid paperless_gpt"""")
    assertThat(body).isBlank()
  }

  @Test
  fun `mcp initialize requires authentication`() {
    val response = mockMvc.perform(
      post("/mcp")
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .content(initializeRequest())
    )
      .andExpect(status().isUnauthorized)
      .andReturn()
      .response

    assertThat(response.getHeader("WWW-Authenticate"))
      .contains("""resource_metadata="https://paperless-gpt.example.test/.well-known/oauth-protected-resource/mcp"""")
      .contains("""scope="openid paperless_gpt"""")
    assertThat(response.contentAsString).isBlank()
  }

  @Test
  fun `mcp tools list with bearer advertises oauth security schemes`() {
    val sessionId = initializeMcp()

    val response = mockMvc.perform(
      post("/mcp")
        .header("Mcp-Session-Id", sessionId)
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .with(paperlessJwt())
        .content(
          """
          {
            "jsonrpc": "2.0",
            "id": 2,
            "method": "tools/list",
            "params": {}
          }
          """.trimIndent()
        )
    )
      .andExpect(status().isOk)
      .andReturn()
      .response

    assertThat(response.contentAsString).contains(""""tools"""")
    assertThat(response.contentAsString).contains(""""outputSchema"""")
    assertThat(response.contentAsString).contains(""""description":"Human readable document title."""")
    assertThat(response.contentAsString).contains(""""description":"Ranked snippets most relevant to the question."""")
    assertThat(response.contentAsString).contains(""""name":"search"""")
    assertThat(response.contentAsString).contains(""""name":"fetch"""")
    assertThat(response.contentAsString).contains(""""id"""")
    assertThat(response.contentAsString).contains(""""url"""")
    assertThat(response.contentAsString).contains(""""resourceUrl"""")
    assertThat(response.contentAsString).contains(""""readOnlyHint":true""")
    assertThat(response.contentAsString).contains(""""destructiveHint":false""")
    assertThat(response.contentAsString).contains(""""idempotentHint":true""")
    assertThat(response.contentAsString).contains(""""openWorldHint":false""")
    assertThat(response.contentAsString).doesNotContain(""""destructiveHint":true""")
    assertThat(response.contentAsString).doesNotContain(""""openWorldHint":true""")
    assertThat(response.contentAsString)
      .contains(""""securitySchemes":[{"type":"oauth2","scopes":["openid","paperless_gpt"]}]""")
    assertThat(response.contentAsString)
      .contains(""""_meta":{"securitySchemes":[{"type":"oauth2","scopes":["openid","paperless_gpt"]}]}""")
  }

  @Test
  fun `mcp advertises the binary document resource template`() {
    val sessionId = initializeMcp()

    val response = mockMvc.perform(
      post("/mcp")
        .header("Mcp-Session-Id", sessionId)
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .with(paperlessJwt())
        .content(
          """
          {
            "jsonrpc": "2.0",
            "id": 6,
            "method": "resources/templates/list",
            "params": {}
          }
          """.trimIndent()
        )
    )
      .andExpect(status().isOk)
      .andReturn()
      .response

    assertThat(response.contentAsString)
      .contains("\"uriTemplate\":\"paperless://documents/{id}/content\"")
      .contains("\"name\":\"paperless-document\"")
      .contains("\"mimeType\":\"application/pdf\"")
  }

  @Test
  fun `mcp tool call without bearer token is rejected by path auth`() {
    val sessionId = initializeMcp()

    val response = mockMvc.perform(
      post("/mcp")
        .header("Mcp-Session-Id", sessionId)
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .content(
          """
          {
            "jsonrpc": "2.0",
            "id": 3,
            "method": "tools/call",
            "params": {
              "name": "listDocuments",
              "arguments": {}
            }
          }
          """.trimIndent()
        )
    )
      .andExpect(status().isUnauthorized)
      .andReturn()
      .response

    assertThat(response.getHeader("WWW-Authenticate"))
      .contains("""resource_metadata="https://paperless-gpt.example.test/.well-known/oauth-protected-resource/mcp"""")
      .contains("""scope="openid paperless_gpt"""")
    assertThat(response.contentAsString).isBlank()
  }

  @Test
  fun `mcp requests require the paperless scope`() {
    mockMvc.perform(
      post("/mcp")
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .with(jwt())
        .content(initializeRequest())
    )
      .andExpect(status().isForbidden)
  }

  @Test
  fun `fetch returns matching structured and text content for ChatGPT compatibility`() {
    val sessionId = initializeMcp()

    val response = mockMvc.perform(
      post("/mcp")
        .header("Mcp-Session-Id", sessionId)
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .with(paperlessJwt())
        .content(
          """
          {
            "jsonrpc": "2.0",
            "id": 4,
            "method": "tools/call",
            "params": {
              "name": "fetch",
              "arguments": { "id": "$DOC_ID" }
            }
          }
          """.trimIndent()
        )
    )
      .andExpect(status().isOk)
      .andReturn()
      .response

    assertThat(response.contentAsString)
      .contains("\"structuredContent\"")
      .contains("\"id\":\"$DOC_ID\"")
      .contains("\"url\":")
      .contains("\"content\":[{\"type\":\"text\",\"text\":\"{\\\"")
  }

  @Test
  fun `search returns matching structured and text content for ChatGPT compatibility`() {
    val sessionId = initializeMcp()

    val response = mockMvc.perform(
      post("/mcp")
        .header("Mcp-Session-Id", sessionId)
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .with(paperlessJwt())
        .content(
          """
          {
            "jsonrpc": "2.0",
            "id": 5,
            "method": "tools/call",
            "params": {
              "name": "search",
              "arguments": { "query": " " }
            }
          }
          """.trimIndent()
        )
    )
      .andExpect(status().isOk)
      .andReturn()
      .response

    assertThat(response.contentAsString)
      .contains("\"structuredContent\":{\"results\":[]}")
      .contains("\"content\":[{\"type\":\"text\",\"text\":\"{\\\"results\\\":[]}\"}]")
  }

  private fun initializeMcp(): String {
    val response = mockMvc.perform(
      post("/mcp")
        .contentType("application/json")
        .accept("application/json", "text/event-stream")
        .with(paperlessJwt())
        .content(initializeRequest())
    )
      .andExpect(status().isOk)
      .andReturn()
      .response

    return response.getHeader("Mcp-Session-Id")!!
  }

  private fun paperlessJwt() = jwt().jwt { token ->
    token.claim("scope", "paperless_gpt")
  }

  private fun initializeRequest(): String {
    return """
      {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "protocolVersion": "2025-11-25",
          "capabilities": {},
          "clientInfo": {
            "name": "test-client",
            "version": "1"
          }
        }
      }
    """.trimIndent()
  }
}
