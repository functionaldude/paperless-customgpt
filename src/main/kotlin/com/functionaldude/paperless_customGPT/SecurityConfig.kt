package com.functionaldude.paperless_customGPT

import com.functionaldude.paperless_customGPT.security.AppProperties
import org.springaicommunity.mcp.security.authorizationserver.config.McpAuthorizationServerConfigurer
import org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.csrf.CookieCsrfTokenRepository

/**
 * Security entrypoint for three stages of the request lifecycle:
 *
 * 1) Authorization Server stage:
 *    Handles OAuth metadata + authorization/token/registration endpoints used by ChatGPT during connect.
 *
 * 2) Resource Server stage:
 *    Protects machine-to-machine endpoints (MCP + API routes) using bearer JWT validation and MCP-specific
 *    OAuth challenge behavior.
 *
 * 3) App/Login stage:
 *    Handles browser-facing login/logout and fallback routes.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
  private val appProperties: AppProperties,
  private val jwtDecoder: JwtDecoder,
) {

  /**
   * Stage 1: Authorization Server chain.
   *
   * Provides:
   * - Well-known metadata endpoints for discovery.
   * - OAuth2 and OIDC authorization server endpoints.
   * - OIDC and dynamic client registration support needed by ChatGPT onboarding.
   *
   * This stage also redirects unauthenticated browser users to the interactive login mechanism.
   */
  @Bean
  @Order(1)
  fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .securityMatcher("/oauth2/**", "/.well-known/**", "/connect/**")
      .authorizeHttpRequests { auth ->
        auth
          .requestMatchers("/.well-known/**").permitAll()
          .anyRequest().authenticated()
      }
      .csrf { csrf ->
        csrf.ignoringRequestMatchers("/oauth2/token", "/oauth2/revoke", "/oauth2/introspect", "/oauth2/register")
      }
      .with(McpAuthorizationServerConfigurer.mcpAuthorizationServer()) { mcpAuth ->
        mcpAuth.authorizationServer { authorizationServer ->
          authorizationServer.oidc(Customizer.withDefaults())
          authorizationServer.clientRegistrationEndpoint(Customizer.withDefaults())
        }
      }
      .exceptionHandling { exceptions ->
        exceptions.authenticationEntryPoint(LoginUrlAuthenticationEntryPoint("/login"))
      }

    applyInteractiveLogin(http)

    return http.build()
  }

  /**
   * Stage 2: Resource Server chain for MCP/API traffic.
   *
   * Provides:
   * - Protection for MCP and API routes with bearer-token auth.
   * - MCP OAuth challenge/metadata behavior (`resource_metadata` in WWW-Authenticate).
   * - Local JWT validation using this app's own signer keys (no external issuer lookup at startup).
   *
   * Health and OpenAPI docs remain publicly reachable where appropriate.
   */
  @Bean
  @Order(2)
  fun mcpAndApiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .securityMatcher("/mcp", "/mcp/**", "/api/**", "/actuator/**")
      .csrf { csrf ->
        csrf
          .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
          .ignoringRequestMatchers("/api/**", "/mcp", "/mcp/**", "/actuator/**", "/v3/api-docs/**")
      }
      .authorizeHttpRequests { auth ->
        auth
          .requestMatchers("/actuator/health").permitAll()
          .requestMatchers("/error").permitAll()
          .requestMatchers("/api/openapi.json").permitAll()
          .requestMatchers("/v3/api-docs/**").permitAll()
          .anyRequest().authenticated()
      }
      .with(McpServerOAuth2Configurer.mcpServerOAuth2()) { oauth2 ->
        oauth2.authorizationServer(appProperties.normalizedPublicUrl())
        oauth2.protectedResourceMetadataCustomizer { metadata ->
          metadata.authorizationServer(appProperties.normalizedPublicUrl())
        }
        oauth2.jwtDecoder(jwtDecoder)
      }

    return http.build()
  }

  /**
   * Stage 3: Browser/app fallback chain.
   *
   * Provides:
   * - Default authorization rules for non-MCP/non-OAuth endpoints.
   * - Public access to root, login, docs, and health/error routes.
   * - Logout handling and the selected interactive login flow.
   */
  @Bean
  @Order(3)
  fun appSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .csrf { csrf ->
        csrf
          .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
          .ignoringRequestMatchers("/api/**", "/mcp", "/mcp/**", "/v3/api-docs/**", "/actuator/**")
      }
      .authorizeHttpRequests { auth ->
        auth
          .requestMatchers("/actuator/health").permitAll()
          .requestMatchers("/error").permitAll()
          .requestMatchers("/").permitAll()
          .requestMatchers("/api/openapi.json").permitAll()
          .requestMatchers("/v3/api-docs/**").permitAll()
          .requestMatchers("/login", "/oauth2/authorization/**", "/login/**").permitAll()
          .anyRequest().authenticated()
      }

    applyInteractiveLogin(http)

    http.logout { logout ->
      logout.logoutSuccessUrl("/")
    }

    return http.build()
  }

  /**
   * Applies the configured interactive login mode for authorization flows:
   * - OAUTH: delegated OIDC login.
   * - LOCAL: in-app username/password form login.
   */
  private fun applyInteractiveLogin(http: HttpSecurity) {
    if (appProperties.auth is AppProperties.Auth.OAuth) {
      http.oauth2Login { oauth2 ->
        oauth2.loginPage("/oauth2/authorization/oauth")
      }
    } else {
      http.formLogin(Customizer.withDefaults())
    }
  }
}
