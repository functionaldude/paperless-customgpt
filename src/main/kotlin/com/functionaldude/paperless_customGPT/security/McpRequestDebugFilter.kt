package com.functionaldude.paperless_customGPT.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter

open class McpRequestDebugFilter : OncePerRequestFilter() {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    if (!request.isMcpDebugTarget()) {
      filterChain.doFilter(request, response)
      return
    }

    val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
    val hasBearerToken = authorizationHeader?.startsWith("Bearer ", ignoreCase = true) == true

    log.debug(
      "MCP request {} {} authHeaderPresent={} bearerTokenPresent={} accept={} contentType={} userAgent={}",
      request.method,
      request.requestURI,
      authorizationHeader != null,
      hasBearerToken,
      request.getHeader(HttpHeaders.ACCEPT),
      request.contentType,
      request.getHeader(HttpHeaders.USER_AGENT),
    )

    filterChain.doFilter(request, response)

    log.debug(
      "MCP response {} {} status={} wwwAuthenticate={}",
      request.method,
      request.requestURI,
      response.status,
      response.getHeader(HttpHeaders.WWW_AUTHENTICATE),
    )
  }

  private fun HttpServletRequest.isMcpDebugTarget(): Boolean {
    return requestURI == "/mcp" ||
        requestURI.startsWith("/mcp/") ||
        requestURI.startsWith("/.well-known/oauth-protected-resource")
  }
}
