package com.tvmaze.scraper.repository

import com.tvmaze.scraper.domain.ScrapeMode
import com.tvmaze.scraper.domain.ScrapeState
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ScrapeStateRepository(private val jdbc: JdbcTemplate) {

    fun get(): ScrapeState = jdbc.queryForObject(
        "SELECT mode, next_page, last_synced_at FROM scrape_state WHERE id = 1",
    ) { rs, _ ->
        ScrapeState(
            mode = ScrapeMode.valueOf(rs.getString("mode").uppercase()),
            nextPage = rs.getInt("next_page"),
            lastSyncedAt = rs.getTimestamp("last_synced_at")?.toInstant(),
        )
    }!!

    /** Atomically claims the job slot; false if another run is already in progress. */
    fun tryStart(): Boolean = jdbc.update(
        "UPDATE scrape_state SET status = 'running', updated_at = now() WHERE id = 1 AND status = 'idle'",
    ) == 1

    fun finish() {
        jdbc.update("UPDATE scrape_state SET status = 'idle', updated_at = now() WHERE id = 1")
    }

    /** A crash can leave status stuck on 'running'; call once at startup (single instance). */
    fun resetRunning() = finish()

    fun advancePage(nextPage: Int) {
        jdbc.update("UPDATE scrape_state SET next_page = ?, updated_at = now() WHERE id = 1", nextPage)
    }

    fun completeFullCrawl(syncedAt: Instant) {
        jdbc.update(
            "UPDATE scrape_state SET mode = 'incremental', last_synced_at = ?, updated_at = now() WHERE id = 1",
            java.sql.Timestamp.from(syncedAt),
        )
    }

    fun setLastSynced(syncedAt: Instant) {
        jdbc.update(
            "UPDATE scrape_state SET last_synced_at = ?, updated_at = now() WHERE id = 1",
            java.sql.Timestamp.from(syncedAt),
        )
    }

    fun resetToFullCrawl() {
        jdbc.update("UPDATE scrape_state SET mode = 'full', next_page = 0, updated_at = now() WHERE id = 1")
    }
}
