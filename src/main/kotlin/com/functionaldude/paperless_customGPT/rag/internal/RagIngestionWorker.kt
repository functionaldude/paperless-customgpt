package com.functionaldude.paperless_customGPT.rag.internal

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.time.measureTime

@Component
class RagIngestionWorker(
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

      measureTime {
        candidates
          .parallelStream()
          .forEach { candidate ->
            runCatching { ragIngestionService.processCandidate(candidate) }
              .onFailure { e ->
                log.error("Failed to process candidate ${candidate.paperlessDocId}", e)
              }
          }
      }.let { duration -> log.info("Processed ${candidates.size} candidates in ${duration.inWholeSeconds} seconds") }

      candidates = ragIngestionService.findIngestCandidates(limit = 20)
    } while (candidates.isNotEmpty())
  }
}
