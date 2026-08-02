package com.functionaldude.paperless_customGPT

import com.functionaldude.paperless_customGPT.security.AppProperties
import com.functionaldude.paperless_customGPT.security.McpBearerAuthenticationEntryPoint
import org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AppProperties::class)
class SecurityConfig(
  private val appProperties: AppProperties,
  private val jwtDecoder: JwtDecoder,
  private val mcpBearerAuthenticationEntryPoint: McpBearerAuthenticationEntryPoint,
) {

  init {
    appProperties.auth.validate()
  }

  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .csrf { csrf ->
        // Keep CSRF protection for browser-facing endpoints. MCP uses bearer tokens rather
        // than cookie authentication, and actuator endpoints are intended for service access.
        csrf
          .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
          .ignoringRequestMatchers("/mcp", "/mcp/**", "/actuator/**")
      }
      .authorizeHttpRequests { auth ->
        // Health, error handling, the landing page, and OAuth resource discovery must be
        // reachable without a token. Everything else, including MCP tool calls, is protected.
        auth
          .requestMatchers("/actuator/health").permitAll()
          .requestMatchers("/error").permitAll()
          .requestMatchers("/").permitAll()
          .requestMatchers("/.well-known/oauth-protected-resource/**").permitAll()
          .requestMatchers("/mcp", "/mcp/**").hasAuthority("SCOPE_${appProperties.auth.normalizedRequiredScope}")
          .anyRequest().authenticated()
      }
      .with(McpServerOAuth2Configurer.mcpServerOAuth2()) { oauth2 ->
        // Configure this application as an OAuth2 resource server for the MCP endpoint and
        // advertise the external resource and authorization-server URLs to MCP clients.
        // This enables the .well-known/oauth-protected-resource/mcp discovery endpoint
        oauth2.authorizationServer(appProperties.auth.normalizedIssuerUri)
        oauth2.resourceName("paperless-customGPT")
        oauth2.resourcePath("/mcp")
        oauth2.protectedResourceMetadataCustomizer { metadata ->
          metadata.resource(appProperties.mcpResource)
          metadata.authorizationServer(appProperties.auth.normalizedIssuerUri)
          metadata.resourceName("paperless-customGPT")
          appProperties.auth.scopes.forEach { scope -> metadata.scope(scope) }
          metadata.claims { claims ->
            // Mutual-TLS-bound access tokens are not supported by this deployment.
            claims.remove("tls_client_certificate_bound_access_tokens")
          }
        }
        // Validate bearer tokens with the application's issuer, audience, and signature rules.
        oauth2.jwtDecoder(jwtDecoder)
        oauth2.oauth2ResourceServer { resourceServer ->
          // Return MCP-compatible authentication challenges when a token is missing or invalid.
          // Currently spring mcp-server-security does not return required scopes in WWW-Authenticate header
          // -> use custom handler
          resourceServer.authenticationEntryPoint(mcpBearerAuthenticationEntryPoint)
        }
      }

    return http.build()
  }
}
