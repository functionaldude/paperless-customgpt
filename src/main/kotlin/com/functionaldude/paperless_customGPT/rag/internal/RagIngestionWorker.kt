package com.functionaldude.paperless_customGPT.rag.internal

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore
import kotlin.time.measureTime

@Component
class RagIngestionWorker(
  private val ragIngestionService: RagIngestionService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  // Run every minute
  @Scheduled(fixedDelayString = "PT1M")
  fun run() {
    log.info("Ingestion worker: checking for candidates")
    var candidates = ragIngestionService.findIngestCandidates(limit = 20)

    do {
      if (candidates.isEmpty()) {
        log.info("Ingestion worker: nothing to do")
        return
      }

      log.info("Ingestion worker: processing ${candidates.size} candidates")
      val semaphore = Semaphore(6)

      measureTime {
        candidates
          .parallelStream()
          .forEach { candidate ->
            try {
              semaphore.acquire()
              ragIngestionService.processCandidate(candidate)
            } catch (e: Exception) {
              log.error("Failed to process candidate ${candidate.paperlessDocId}", e)
            } finally {
              semaphore.release()
            }
          }
      }.let { duration -> log.info("Processed ${candidates.size} candidates in ${duration.inWholeSeconds} seconds") }

      candidates = ragIngestionService.findIngestCandidates(limit = 20)
    } while (candidates.isNotEmpty())
  }
}
