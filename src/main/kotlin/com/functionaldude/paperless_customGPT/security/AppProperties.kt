package com.functionaldude.paperless_customGPT.security

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

    val jwkSetUri: String get() = "${normalizedIssuerUri.trimEnd('/')}/jwks/"
  }

  val normalizedPublicUrl: String get() = publicUrl.trimEnd('/')

  val mcpResource: String get() = "$normalizedPublicUrl/mcp"

  val mcpProtectedResourceMetadataUrl: String get() = "$normalizedPublicUrl/.well-known/oauth-protected-resource/mcp"

  val expectedAudience: String get() = auth.audience.trim().ifBlank { mcpResource }

  companion object {
    val DEFAULT_SCOPES = listOf("openid", "profile", "email")
  }
}
