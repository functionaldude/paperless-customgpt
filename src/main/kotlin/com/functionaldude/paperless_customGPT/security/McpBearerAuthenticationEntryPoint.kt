package com.functionaldude.paperless_customGPT.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class McpBearerAuthenticationEntryPoint(
  private val appProperties: AppProperties,
) : AuthenticationEntryPoint {
  private val delegate = BearerTokenAuthenticationEntryPoint()
  private val resourceMetadataPattern = Regex("""\s*,?\s*resource_metadata="?[^",\s]+"?""")

  override fun commence(
    request: HttpServletRequest,
    response: HttpServletResponse,
    authException: AuthenticationException,
  ) {
    delegate.commence(request, response, authException)

    val challenge = response.getHeader(HttpHeaders.WWW_AUTHENTICATE) ?: "Bearer"
    val challengeWithoutResourceMetadata = challenge
      .replace(resourceMetadataPattern, "")
      .trim()
    val separator = if (challengeWithoutResourceMetadata.equals("Bearer", ignoreCase = true)) " " else ", "

    response.setHeader(
      HttpHeaders.WWW_AUTHENTICATE,
      challengeWithoutResourceMetadata + separator +
          "resource_metadata=\"${appProperties.mcpProtectedResourceMetadataUrl}\"" +
          separator + "scope=\"${appProperties.auth.scopes.joinToString(" ")}\"",
    )
  }
}
