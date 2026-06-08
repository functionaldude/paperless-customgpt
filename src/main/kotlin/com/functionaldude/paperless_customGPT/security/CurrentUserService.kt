package com.functionaldude.paperless_customGPT.security

import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service

@Service
class CurrentUserService {
  private val log = LoggerFactory.getLogger(javaClass)

  fun currentUsername(): String {
    val authentication = requireJwtAuthentication()

    val username = extractUsername(authentication)
    if (username.isNullOrBlank()) {
      log.warn("Authenticated principal {} does not expose a username", authentication::class.simpleName)
      throw IllegalStateException("Authenticated principal does not expose a username")
    }

    return username
  }

  fun requireJwtAuthentication(): JwtAuthenticationToken {
    val authentication = SecurityContextHolder.getContext().authentication
      ?: throw IllegalStateException("OAuth bearer token is required")

    if (authentication !is JwtAuthenticationToken || !authentication.isAuthenticated) {
      throw IllegalStateException("OAuth bearer token is required")
    }

    return authentication
  }

  private fun extractUsername(authentication: Authentication): String? {
    return when (authentication) {
      is JwtAuthenticationToken -> {
        authentication.token.claims["preferred_username"] as? String
          ?: authentication.token.claims["username"] as? String
          ?: authentication.token.subject
          ?: authentication.name
      }

      else -> (authentication.principal as? UserDetails)?.username ?: authentication.name
    }
  }
}
