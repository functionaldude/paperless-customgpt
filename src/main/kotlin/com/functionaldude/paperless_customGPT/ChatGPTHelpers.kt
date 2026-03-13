package com.functionaldude.paperless_customGPT

import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OpenAiNonConsequential

@Configuration
class ChatGPTHelpers {
  companion object {
    const val OPENAI_IS_CONSEQUENTIAL_EXTENSION = "x-openai-isConsequential"
  }

  @Bean
  fun openAiConsequentialFlagCustomizer(): OperationCustomizer {
    return OperationCustomizer { operation, handlerMethod ->
      if (handlerMethod.hasMethodAnnotation(OpenAiNonConsequential::class.java)) {
        val extensions = operation.extensions ?: linkedMapOf()
        extensions[OPENAI_IS_CONSEQUENTIAL_EXTENSION] = false
        operation.extensions = extensions
      }
      operation
    }
  }
}
