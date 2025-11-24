package com.functionaldude.paperless_customGPT.rag.internal

import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RagIngestionWorker(
  private val dsl: DSLContext,
  private val ragIngestionService: RagIngestionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  // Run every minute
  @Scheduled(fixedDelayString = "PT1M")
  fun run() {
    var candidates = ragIngestionService.findIngestCandidates(limit = 20)

    do {
      if (candidates.isEmpty()) {
        log.info("Nothing to do")
        return
      }

      candidates.forEach { candidate -> ragIngestionService.processCandidate(candidate) }
      candidates = ragIngestionService.findIngestCandidates(limit = 20)
    } while (candidates.isNotEmpty())
  }
}