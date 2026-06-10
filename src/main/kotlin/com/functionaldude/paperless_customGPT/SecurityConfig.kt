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

  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .csrf { csrf ->
        csrf
          .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
          .ignoringRequestMatchers("/mcp", "/mcp/**", "/actuator/**")
      }
      .authorizeHttpRequests { auth ->
        auth
          .requestMatchers("/actuator/health").permitAll()
          .requestMatchers("/error").permitAll()
          .requestMatchers("/").permitAll()
          .requestMatchers("/.well-known/oauth-protected-resource/**").permitAll()
          .anyRequest().authenticated()
      }
      .with(McpServerOAuth2Configurer.mcpServerOAuth2()) { oauth2 ->
        oauth2.authorizationServer(appProperties.auth.normalizedIssuerUri)
        oauth2.resourceName("paperless-customGPT")
        oauth2.resourcePath("/mcp")
        oauth2.protectedResourceMetadataCustomizer { metadata ->
          metadata.resource(appProperties.mcpResource())
          metadata.authorizationServer(appProperties.auth.normalizedIssuerUri)
          metadata.resourceName("paperless-customGPT")
          appProperties.auth.scopes.forEach { scope -> metadata.scope(scope) }
          metadata.claims { claims ->
            claims.remove("tls_client_certificate_bound_access_tokens")
          }
        }
        oauth2.jwtDecoder(jwtDecoder)
        oauth2.oauth2ResourceServer { resourceServer ->
          resourceServer.authenticationEntryPoint(mcpBearerAuthenticationEntryPoint)
        }
      }

    return http.build()
  }
}
