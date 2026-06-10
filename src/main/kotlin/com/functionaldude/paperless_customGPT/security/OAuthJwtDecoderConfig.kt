package com.functionaldude.paperless_customGPT.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
class OAuthJwtDecoderConfig {

  @Bean
  fun jwtDecoder(appProperties: AppProperties): JwtDecoder {
    val jwtValidator = DelegatingOAuth2TokenValidator(
      JwtValidators.createDefaultWithIssuer(appProperties.auth.normalizedIssuerUri),
      JwtAudienceValidator(appProperties.expectedAudience()),
    )

    val delegate by lazy {
      NimbusJwtDecoder.withJwkSetUri(appProperties.auth.jwkSetUri).build().apply {
        setJwtValidator(jwtValidator)
      }
    }

    return JwtDecoder { token -> delegate.decode(token) }
  }
}

class JwtAudienceValidator(
  private val audience: String,
) : OAuth2TokenValidator<Jwt> {
  private val error = OAuth2Error(
    "invalid_token",
    "The required audience '$audience' is missing",
    null,
  )

  override fun validate(token: Jwt): OAuth2TokenValidatorResult {
    return if (token.audience.contains(audience)) {
      OAuth2TokenValidatorResult.success()
    } else {
      OAuth2TokenValidatorResult.failure(error)
    }
  }
}
