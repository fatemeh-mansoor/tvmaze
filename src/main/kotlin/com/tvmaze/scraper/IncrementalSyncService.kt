package com.tvmaze.scraper

import com.tvmaze.scraper.client.TvMazeApi
import com.tvmaze.scraper.client.dto.ShowWithCast
import com.tvmaze.scraper.repository.ScrapeStateRepository
import com.tvmaze.scraper.repository.ShowWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class IncrementalSyncService(
    private val api: TvMazeApi,
    private val writer: ShowWriter,
    private val state: ScrapeStateRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun run() {
        val startedAt = Instant.now()
        val lastSynced = state.get().lastSyncedAt
        val window = lastSynced?.let { windowFor(Duration.between(it, startedAt)) }
        if (lastSynced == null || window == null) {
            // TVMaze only reports updates for the last month; older gaps need a full crawl.
            log.warn("Last sync is missing or older than a month, falling back to a full crawl")
            state.resetToFullCrawl()
            return
        }

        val changed = api.getUpdates(window).filterValues { it > lastSynced.epochSecond }.keys
        log.info("{} shows changed since {}", changed.size, lastSynced)

        for (id in changed) {
            if (Thread.currentThread().isInterrupted) return
            api.getShow(id)?.let { writer.save(ShowWithCast(it, api.getCast(id))) }
        }
        state.setLastSynced(startedAt)
    }

    private fun windowFor(age: Duration): String? = when {
        age <= Duration.ofDays(1) -> "day"
        age <= Duration.ofDays(7) -> "week"
        age <= Duration.ofDays(30) -> "month"
        else -> null
    }
}
