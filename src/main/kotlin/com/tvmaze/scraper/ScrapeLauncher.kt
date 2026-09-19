package com.tvmaze.scraper

import com.tvmaze.scraper.repository.ScrapeStateRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

@Component
@ConditionalOnProperty("scrape.enabled", havingValue = "true", matchIfMissing = true)
class ScrapeLauncher(
    private val orchestrator: ScrapeOrchestrator,
    private val state: ScrapeStateRepository,
) : ApplicationRunner {

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "scrape-startup") }

    override fun run(args: ApplicationArguments) {
        state.resetRunning()
        executor.submit(orchestrator::runOnce)
    }

    @Scheduled(
        initialDelayString = "\${scrape.incremental-interval-ms}",
        fixedDelayString = "\${scrape.incremental-interval-ms}",
    )
    fun scheduled() = orchestrator.runOnce()
}
