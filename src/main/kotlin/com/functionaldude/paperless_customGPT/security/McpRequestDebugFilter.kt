package com.functionaldude.paperless_customGPT.security

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component

@Component
class McpRequestDebugFilter : Filter {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun doFilter(
    request: ServletRequest,
    response: ServletResponse,
    filterChain: FilterChain,
  ) {
    val httpRequest = request as? HttpServletRequest
    val httpResponse = response as? HttpServletResponse

    if (!log.isDebugEnabled || httpRequest == null || httpResponse == null || !httpRequest.isMcpDebugTarget()) {
      filterChain.doFilter(request, response)
      return
    }

    val authorizationHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION)
    val hasBearerToken = authorizationHeader?.startsWith("Bearer ", ignoreCase = true) == true

    log.debug(
      "MCP request {} {} authHeaderPresent={} bearerTokenPresent={} accept={} contentType={} userAgent={}",
      httpRequest.method,
      httpRequest.requestURI,
      authorizationHeader != null,
      hasBearerToken,
      httpRequest.getHeader(HttpHeaders.ACCEPT),
      httpRequest.contentType,
      httpRequest.getHeader(HttpHeaders.USER_AGENT),
    )

    filterChain.doFilter(request, response)

    log.debug(
      "MCP response {} {} status={} wwwAuthenticate={}",
      httpRequest.method,
      httpRequest.requestURI,
      httpResponse.status,
      httpResponse.getHeader(HttpHeaders.WWW_AUTHENTICATE),
    )
  }

  private fun HttpServletRequest.isMcpDebugTarget(): Boolean {
    return requestURI == "/mcp" ||
        requestURI.startsWith("/mcp/") ||
        requestURI.startsWith("/.well-known/oauth-protected-resource")
  }
}
