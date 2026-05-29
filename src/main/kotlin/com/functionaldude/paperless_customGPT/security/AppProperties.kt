package com.functionaldude.paperless_customGPT.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.Name
import java.util.*

@ConfigurationProperties(prefix = "app")
data class AppProperties private constructor(
  val publicUrl: String = "http://localhost:8080",
  @param:Name("auth") private val authSelection: AuthSelection = AuthSelection(),
  val oauth: Oauth = Oauth(),
) {
  val auth: Auth
    get() = authSelection.toAuth()

  private data class AuthSelection(
    val type: String = "LOCAL",
    val local: Local = Local(),
    val oAuth: OAuth = OAuth(),
  ) {
    fun toAuth(): Auth = when (type.trim().uppercase(Locale.ROOT)) {
      "LOCAL" -> Auth.Local(
        username = local.username,
        password = local.password,
      )

      "OAUTH" -> Auth.OAuth(
        issuerUri = oAuth.issuerUri,
        clientId = oAuth.clientId,
        clientSecret = oAuth.clientSecret,
        scopes = oAuth.scopes,
      )

      else -> throw IllegalStateException(
        "Unsupported app.auth.type '$type'. Supported values: LOCAL, OAUTH"
      )
    }
  }

  private data class Local(
    val username: String = "",
    val password: String = "",
  )

  private data class OAuth(
    val issuerUri: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val scopes: List<String> = listOf("openid", "profile", "email"),
  )

  abstract class Auth {
    data class Local(
      val username: String,
      val password: String,
    ) : Auth()

    data class OAuth(
      val issuerUri: String,
      val clientId: String,
      val clientSecret: String,
      val scopes: List<String>,
    ) : Auth()
  }

  data class Oauth(
    val keyId: String = "paperless-customgpt-key",
    val privateKeyPem: String = "",
    val publicKeyPem: String = "",
  )

  fun normalizedPublicUrl(): String = publicUrl.trimEnd('/')
}
