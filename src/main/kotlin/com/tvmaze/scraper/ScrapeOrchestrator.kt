package com.tvmaze.scraper

import com.tvmaze.scraper.domain.ScrapeMode
import com.tvmaze.scraper.repository.ScrapeStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ScrapeOrchestrator(
    private val state: ScrapeStateRepository,
    private val fullCrawl: FullCrawlService,
    private val incremental: IncrementalSyncService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Runs whichever job the saved state calls for; does nothing if a run is already in progress. */
    fun runOnce() {
        if (!state.tryStart()) {
            log.info("Scrape already running, skipping")
            return
        }
        try {
            if (state.get().mode == ScrapeMode.INCREMENTAL) incremental.run()
            // Incremental sync switches back to full when its last sync is too old to catch up.
            if (state.get().mode == ScrapeMode.FULL) fullCrawl.run()
        } catch (e: Exception) {
            log.error("Scrape run failed; it will resume from the saved checkpoint next time", e)
        } finally {
            state.finish()
        }
    }
}
