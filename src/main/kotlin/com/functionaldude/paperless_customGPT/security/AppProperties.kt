package com.functionaldude.paperless_customGPT.security

import com.functionaldude.paperless_customGPT.mcp.McpToolAuthMetadataConfig.Companion.DEFAULT_SCOPES
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
  val publicUrl: String = "http://localhost:8080",
  val auth: Auth = Auth(),
) {
  data class Auth(
    val issuerUri: String = "http://localhost:9000/application/o/paperless/",
    val audience: String = "",
    val scopes: List<String> = DEFAULT_SCOPES,
  ) {
    val normalizedIssuerUri: String
      get() = issuerUri.trim().ifBlank { throw IllegalStateException("app.auth.issuer-uri must not be blank") }

    val jwkSetUri: String
      get() = "${normalizedIssuerUri.trimEnd('/')}/jwks/"
  }

  fun normalizedPublicUrl(): String = publicUrl.trimEnd('/')

  fun mcpResource(): String = "${normalizedPublicUrl()}/mcp"

  fun mcpProtectedResourceMetadataUrl(): String = "${normalizedPublicUrl()}/.well-known/oauth-protected-resource/mcp"

  fun expectedAudience(): String = auth.audience.trim().ifBlank { mcpResource() }
}
