package com.functionaldude.paperless_customGPT.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
  val publicUrl: String = "http://localhost:8080",
  val auth: Auth = Auth(),
) {
  data class Auth(
    val issuerUri: String = "http://localhost:9000/application/o/paperless/",
    val jwkSetUri: String = "",
    val audience: String = "",
    val scopes: List<String> = listOf("openid", "profile", "email"),
  ) {
    fun normalizedIssuerUri(): String = issuerUri.trim().ifBlank {
      throw IllegalStateException("app.auth.issuer-uri must not be blank")
    }
  }

  fun normalizedPublicUrl(): String = publicUrl.trimEnd('/')

  fun mcpResource(): String = "${normalizedPublicUrl()}/mcp"

  fun expectedAudience(): String = auth.audience.trim().ifBlank { mcpResource() }
}
