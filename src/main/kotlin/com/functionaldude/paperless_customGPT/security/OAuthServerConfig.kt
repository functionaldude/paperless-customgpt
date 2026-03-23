package com.functionaldude.paperless_customGPT.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

/**
 * Configures this service as an embedded OAuth 2.1 Authorization Server for the MCP endpoint.
 *
 * Goal:
 * - Let ChatGPT connect to this self-hosted app using only `https://<host>/mcp`.
 * - Expose OAuth metadata + client registration on the same host as MCP.
 * - Issue and validate JWT access tokens used for MCP and API routes.
 *
 * This class wires the server-side OAuth infrastructure (issuer, client store, authorization/consent
 * persistence, signing keys, and interactive login dependencies).
 */
@Configuration
@EnableConfigurationProperties(AppProperties::class)
class OAuthServerConfig {
  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * Declares Authorization Server endpoint settings and sets the public issuer URL.
   *
   * Why needed:
   * ChatGPT and other clients read issuer-based metadata and expect token issuer claims to match the
   * externally reachable URL of this deployment.
   */
  @Bean
  fun authorizationServerSettings(appProperties: AppProperties): AuthorizationServerSettings {
    return AuthorizationServerSettings.builder()
      .issuer(appProperties.normalizedPublicUrl())
      .build()
  }

  /**
   * Persists OAuth registered clients in Postgres.
   *
   * Why needed:
   * Dynamic client registration from ChatGPT must survive restarts; in-memory registration would break
   * reconnect scenarios.
   */
  @Bean
  fun registeredClientRepository(jdbcTemplate: JdbcTemplate): RegisteredClientRepository {
    return JdbcRegisteredClientRepository(jdbcTemplate)
  }

  /**
   * Persists OAuth authorization state (authorization code, access token, refresh token, etc.) in Postgres.
   *
   * Why needed:
   * The authorization server needs durable token/authorization state for code exchange and refresh flows.
   */
  @Bean
  fun authorizationService(
    jdbcTemplate: JdbcTemplate,
    registeredClientRepository: RegisteredClientRepository,
  ): OAuth2AuthorizationService {
    return JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository)
  }

  /**
   * Persists end-user consent decisions in Postgres.
   *
   * Why needed:
   * OAuth consent is part of authorization state and must remain stable across server restarts.
   */
  @Bean
  fun authorizationConsentService(
    jdbcTemplate: JdbcTemplate,
    registeredClientRepository: RegisteredClientRepository,
  ): OAuth2AuthorizationConsentService {
    return JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository)
  }

  /**
   * Provides signing keys (JWK) used by the authorization server for JWT access tokens.
   *
   * Behavior:
   * - If PEM keys are configured, uses those.
   * - If neither key is configured, generates an ephemeral RSA key pair (dev-friendly fallback).
   * - If only one key is configured, fails fast because signing/verification would be inconsistent.
   */
  @Bean
  fun jwkSource(appProperties: AppProperties): JWKSource<SecurityContext> {
    val oauthProperties = appProperties.oauth
    val keyId = oauthProperties.keyId.ifBlank { "paperless-customgpt-key" }

    val hasPrivateKey = oauthProperties.privateKeyPem.isNotBlank()
    val hasPublicKey = oauthProperties.publicKeyPem.isNotBlank()

    val rsaKey = when {
      hasPrivateKey && hasPublicKey -> {
        val privateKey = parsePrivateKey(oauthProperties.privateKeyPem)
        val publicKey = parsePublicKey(oauthProperties.publicKeyPem)
        RSAKey.Builder(publicKey)
          .privateKey(privateKey)
          .keyID(keyId)
          .build()
      }

      hasPrivateKey || hasPublicKey -> {
        throw IllegalStateException("Both app.oauth.private-key-pem and app.oauth.public-key-pem must be provided together")
      }

      else -> {
        log.warn("No RSA signing keys configured via app.oauth.*; generating ephemeral key pair")
        val keyPair = generateRsaKeyPair()
        RSAKey.Builder(keyPair.public as RSAPublicKey)
          .privateKey(keyPair.private as RSAPrivateKey)
          .keyID(keyId)
          .build()
      }
    }

    return ImmutableJWKSet(JWKSet(rsaKey))
  }

  /**
   * Builds a JWT decoder from the same JWK source used for signing.
   *
   * Why needed:
   * Resource endpoints can validate locally issued bearer tokens without external issuer discovery calls.
   */
  @Bean
  fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)
  }

  /**
   * Password encoder used for LOCAL interactive login credentials.
   *
   * Why needed:
   * Spring Security requires encoded credentials for username/password authentication.
   */
  @Bean
  fun passwordEncoder(): PasswordEncoder {
    return BCryptPasswordEncoder()
  }

  /**
   * Registers a single local user when login mode is LOCAL.
   *
   * Why needed:
   * OAuth authorization flows still need a human login step; LOCAL mode provides a built-in identity source
   * without external IdP dependency.
   */
  @Bean
  @ConditionalOnProperty(prefix = "app.auth", name = ["login-mode"], havingValue = "LOCAL", matchIfMissing = true)
  fun localUserDetailsService(
    appProperties: AppProperties,
    passwordEncoder: PasswordEncoder,
  ): UserDetailsService {
    val username = appProperties.auth.local.username.ifBlank {
      throw IllegalStateException("app.auth.local.username must not be blank in LOCAL login mode")
    }
    val password = appProperties.auth.local.password.ifBlank {
      throw IllegalStateException("app.auth.local.password must not be blank in LOCAL login mode")
    }

    val user = User.withUsername(username)
      .password(passwordEncoder.encode(password))
      .roles("USER")
      .build()

    return InMemoryUserDetailsManager(user)
  }

  /**
   * Configures the OAuth2 login client for delegated interactive authentication against Authentik.
   *
   * Why needed:
   * In AUTHENTIK mode, users authenticate via Authentik, then this app continues the local authorization
   * server flow and issues access tokens for MCP/API usage.
   */
  @Bean
  @ConditionalOnProperty(prefix = "app.auth", name = ["login-mode"], havingValue = "AUTHENTIK")
  fun authentikClientRegistrationRepository(appProperties: AppProperties): ClientRegistrationRepository {
    val authentik = appProperties.auth.authentik
    val issuerUri = authentik.issuerUri.ifBlank {
      throw IllegalStateException("app.auth.authentik.issuer-uri must not be blank in AUTHENTIK login mode")
    }
    val clientId = authentik.clientId.ifBlank {
      throw IllegalStateException("app.auth.authentik.client-id must not be blank in AUTHENTIK login mode")
    }
    val clientSecret = authentik.clientSecret.ifBlank {
      throw IllegalStateException("app.auth.authentik.client-secret must not be blank in AUTHENTIK login mode")
    }

    val registration = ClientRegistrations
      .fromIssuerLocation(issuerUri)
      .registrationId("authentik")
      .clientId(clientId)
      .clientSecret(clientSecret)
      .scope(authentik.scopes)
      .clientName("Authentik")
      .build()

    return InMemoryClientRegistrationRepository(registration)
  }

  /**
   * Stores delegated OAuth2 login sessions for the configured client registration.
   *
   * Why needed:
   * When AUTHENTIK login is enabled, Spring Security needs an authorized client service to keep the login
   * session context.
   */
  @Bean
  @ConditionalOnBean(ClientRegistrationRepository::class)
  fun oauth2AuthorizedClientService(
    clientRegistrationRepository: ClientRegistrationRepository,
  ): OAuth2AuthorizedClientService {
    return InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository)
  }

  /**
   * Generates an ephemeral RSA key pair used when PEM keys are not configured.
   */
  private fun generateRsaKeyPair(): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    return keyPairGenerator.generateKeyPair()
  }

  /**
   * Parses a PKCS#8 PEM private key into an RSA private key object.
   */
  private fun parsePrivateKey(privateKeyPem: String): RSAPrivateKey {
    val decoded = decodePem(
      privateKeyPem,
      beginMarker = "-----BEGIN PRIVATE KEY-----",
      endMarker = "-----END PRIVATE KEY-----",
    )
    val keySpec = PKCS8EncodedKeySpec(decoded)
    return KeyFactory.getInstance("RSA").generatePrivate(keySpec) as RSAPrivateKey
  }

  /**
   * Parses an X.509 PEM public key into an RSA public key object.
   */
  private fun parsePublicKey(publicKeyPem: String): RSAPublicKey {
    val decoded = decodePem(
      publicKeyPem,
      beginMarker = "-----BEGIN PUBLIC KEY-----",
      endMarker = "-----END PUBLIC KEY-----",
    )
    val keySpec = X509EncodedKeySpec(decoded)
    return KeyFactory.getInstance("RSA").generatePublic(keySpec) as RSAPublicKey
  }

  /**
   * Normalizes PEM text and decodes the base64 payload.
   *
   * Accepts env-friendly escaped newlines (`\\n`) and validates that the payload is not empty.
   */
  private fun decodePem(pem: String, beginMarker: String, endMarker: String): ByteArray {
    val normalized = pem
      .replace("\\n", "\n")
      .trim()

    val base64 = normalized
      .replace(beginMarker, "")
      .replace(endMarker, "")
      .replace("\n", "")
      .replace("\r", "")
      .trim()

    if (base64.isBlank()) {
      throw IllegalStateException("Invalid PEM content for marker $beginMarker")
    }

    return Base64.getDecoder().decode(base64)
  }
}
