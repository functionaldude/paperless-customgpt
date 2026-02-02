package com.functionaldude.paperless_customGPT.security

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

@Service
class CurrentUserService {
  private val log = LoggerFactory.getLogger(javaClass)

  fun currentUsername(): String {
    val authentication = SecurityContextHolder.getContext().authentication
      ?: throw IllegalStateException("No authentication found in security context")

    val username = extractUsername(authentication)
    if (username.isNullOrBlank()) {
      log.warn("Authenticated principal {} does not expose a username", authentication::class.simpleName)
      throw IllegalStateException("Authenticated principal does not expose a username")
    }

    return username
  }

  private fun extractUsername(authentication: Authentication): String? {
    return when (authentication) {
      is JwtAuthenticationToken -> {
        authentication.token.claims["preferred_username"] as? String
          ?: authentication.token.claims["username"] as? String
          ?: authentication.name
      }

      is OAuth2AuthenticationToken -> {
        val principal = authentication.principal
        principal?.attributes?.get("preferred_username") as? String
          ?: principal?.attributes?.get("username") as? String
          ?: authentication.name
      }

      else -> authentication.name
    }
  }
}
