package com.functionaldude.paperless_customGPT.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import java.time.Instant

class JwtAudienceValidatorTest {
  private val issuer = "https://idp.example.test/application/o/paperless/"
  private val audience = "https://paperless-gpt.example.test/mcp"
  private val validator = DelegatingOAuth2TokenValidator(
    JwtValidators.createDefaultWithIssuer(issuer),
    JwtAudienceValidator(audience),
  )

  @Test
  fun `valid oidc issuer and audience passes validation`() {
    val result = validator.validate(jwt(issuer = issuer, audience = audience))

    assertThat(result.hasErrors()).isFalse()
  }

  @Test
  fun `wrong audience fails validation`() {
    val result = validator.validate(jwt(issuer = issuer, audience = "wrong-audience"))

    assertThat(result.hasErrors()).isTrue()
  }

  @Test
  fun `wrong issuer fails validation`() {
    val result = validator.validate(jwt(issuer = "https://issuer.example.invalid/", audience = audience))

    assertThat(result.hasErrors()).isTrue()
  }

  private fun jwt(
    issuer: String,
    audience: String,
  ): Jwt {
    val now = Instant.now()
    return Jwt.withTokenValue("token")
      .header("alg", "RS256")
      .issuer(issuer)
      .subject("paperless-user")
      .audience(listOf(audience))
      .issuedAt(now)
      .expiresAt(now.plusSeconds(300))
      .build()
  }
}
