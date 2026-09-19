package com.tvmaze.scraper

import com.tvmaze.scraper.client.TvMazeApi
import com.tvmaze.scraper.client.dto.ShowWithCast
import com.tvmaze.scraper.repository.ScrapeStateRepository
import com.tvmaze.scraper.repository.ShowWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class FullCrawlService(
    private val api: TvMazeApi,
    private val writer: ShowWriter,
    private val state: ScrapeStateRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Walks pages from the saved checkpoint until TVMaze runs out of pages. */
    fun run() {
        // Taken before the crawl so changes made during it are picked up by the next incremental sync.
        val startedAt = Instant.now()
        var page = state.get().nextPage
        while (!Thread.currentThread().isInterrupted) {
            val shows = api.getShowsPage(page)
            if (shows.isNullOrEmpty()) {
                state.completeFullCrawl(startedAt)
                log.info("Full crawl complete after {} pages", page)
                return
            }
            // Cast is fetched before the transaction so no DB transaction stays open during slow HTTP calls.
            val withCast = shows.map { ShowWithCast(it, api.getCast(it.id)) }
            writer.savePageAndAdvance(withCast, page + 1)
            log.info("Saved page {} ({} shows)", page, shows.size)
            page++
        }
    }
}
