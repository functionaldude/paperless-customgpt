package com.functionaldude.paperless_customGPT.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppPropertiesTest {

  @Test
  fun `jwk set uri is derived from issuer uri`() {
    val auth = AppProperties.Auth(
      issuerUri = "https://idp.example.test/application/o/paperless/"
    )

    assertThat(auth.jwkSetUri)
      .isEqualTo("https://idp.example.test/application/o/paperless/jwks/")
  }

  @Test
  fun `jwk set uri adds missing issuer trailing slash`() {
    val auth = AppProperties.Auth(
      issuerUri = "https://idp.example.test/application/o/paperless"
    )

    assertThat(auth.jwkSetUri)
      .isEqualTo("https://idp.example.test/application/o/paperless/jwks/")
  }
}
