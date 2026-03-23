package com.functionaldude.paperless_customGPT.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
  val publicUrl: String = "http://localhost:8080",
  val auth: Auth = Auth(),
  val oauth: Oauth = Oauth(),
) {
  data class Auth(
    val loginMode: LoginMode = LoginMode.LOCAL,
    val local: Local = Local(),
    val authentik: Authentik = Authentik(),
  )

  enum class LoginMode {
    LOCAL,
    AUTHENTIK,
  }

  data class Local(
    val username: String = "paperless",
    val password: String = "paperless-change-me",
  )

  data class Authentik(
    val issuerUri: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val scopes: List<String> = listOf("openid", "profile", "email"),
  )

  data class Oauth(
    val keyId: String = "paperless-customgpt-key",
    val privateKeyPem: String = "",
    val publicKeyPem: String = "",
  )

  fun normalizedPublicUrl(): String = publicUrl.trimEnd('/')
}
