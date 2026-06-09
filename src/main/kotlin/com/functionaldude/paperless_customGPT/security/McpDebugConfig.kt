package com.functionaldude.paperless_customGPT.security

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpDebugConfig {

  @Bean
  fun mcpRequestDebugFilter(): McpRequestDebugFilter {
    return McpRequestDebugFilter()
  }

  @Bean
  fun mcpRequestDebugFilterRegistration(
    mcpRequestDebugFilter: McpRequestDebugFilter,
  ): FilterRegistrationBean<McpRequestDebugFilter> {
    val registration = FilterRegistrationBean(mcpRequestDebugFilter)
    registration.isEnabled = false
    return registration
  }
}
