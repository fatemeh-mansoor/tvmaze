package com.tvmaze.scraper

import com.tvmaze.scraper.client.TvMazeApi
import com.tvmaze.scraper.client.dto.PersonDto
import com.tvmaze.scraper.client.dto.ShowDto
import com.tvmaze.scraper.domain.ScrapeMode
import com.tvmaze.scraper.repository.ScrapeStateRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeTvMazeApi : TvMazeApi {
    var pages: List<List<ShowDto>> = emptyList()
    var shows: Map<Long, ShowDto> = emptyMap()
    var casts: Map<Long, List<PersonDto>> = emptyMap()
    var updates: Map<Long, Long> = emptyMap()
    val requestedPages = mutableListOf<Int>()

    override fun getShowsPage(page: Int): List<ShowDto>? {
        requestedPages += page
        return pages.getOrNull(page)
    }

    override fun getShow(id: Long) = shows[id]
    override fun getCast(showId: Long) = casts[showId].orEmpty()
    override fun getUpdates(since: String) = updates
}

@TestConfiguration
class FakeApiConfig {
    @Bean @Primary
    fun fakeApi() = FakeTvMazeApi()
}

@Testcontainers
@SpringBootTest(properties = ["scrape.enabled=false"])
@org.springframework.context.annotation.Import(FakeApiConfig::class)
class ScrapeIntegrationTest {

    companion object {
        @Container @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16")
    }

    @Autowired lateinit var api: FakeTvMazeApi
    @Autowired lateinit var orchestrator: ScrapeOrchestrator
    @Autowired lateinit var state: ScrapeStateRepository
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun reset() {
        jdbc.update("DELETE FROM show_cast")
        jdbc.update("DELETE FROM show")
        jdbc.update("DELETE FROM person")
        jdbc.update("UPDATE scrape_state SET mode='full', next_page=0, last_synced_at=NULL, status='idle'")
        api.requestedPages.clear()
        api.pages = emptyList()
        api.shows = emptyMap()
        api.casts = emptyMap()
        api.updates = emptyMap()
    }

    private fun show(id: Long, vararg cast: PersonDto): ShowDto {
        api.casts = api.casts + (id to cast.toList())
        return ShowDto(id, "Show $id")
    }

    private fun count(table: String) = jdbc.queryForObject("SELECT count(*) FROM $table", Long::class.java)

    @Test
    fun `full crawl saves every page, dedupes people, keeps null birthdays and switches to incremental`() {
        val actor = PersonDto(10, "Actor", "1979-07-17")
        api.pages = listOf(
            listOf(show(1, actor, PersonDto(11, "No Birthday", null)), show(2)),
            listOf(show(3, actor, actor), show(4, PersonDto(12, "Bad Date", "not-a-date"))),
        )

        orchestrator.runOnce()

        assertEquals(4, count("show"))
        assertEquals(3, count("person"))
        assertEquals(4, count("show_cast"))
        assertNull(jdbc.queryForObject("SELECT birthday FROM person WHERE id = 11", java.sql.Date::class.java))
        assertNull(jdbc.queryForObject("SELECT birthday FROM person WHERE id = 12", java.sql.Date::class.java))
        val s = state.get()
        assertEquals(ScrapeMode.INCREMENTAL, s.mode)
        assertEquals(2, s.nextPage)
        assertTrue(s.lastSyncedAt != null)
    }

    @Test
    fun `crawl resumes from the checkpoint instead of starting over`() {
        api.pages = listOf(listOf(show(1)), listOf(show(2)))
        jdbc.update("UPDATE scrape_state SET next_page = 1")

        orchestrator.runOnce()

        assertEquals(listOf(1, 2), api.requestedPages)
        assertEquals(1, count("show"))
    }

    @Test
    fun `rerunning a page is safe`() {
        api.pages = listOf(listOf(show(1, PersonDto(10, "Actor", "1979-07-17"))))
        orchestrator.runOnce()
        jdbc.update("UPDATE scrape_state SET mode='full', next_page=0")

        orchestrator.runOnce()

        assertEquals(1, count("show"))
        assertEquals(1, count("show_cast"))
    }

    @Test
    fun `incremental sync refetches only changed shows and replaces their cast`() {
        val synced = Instant.now().minusSeconds(3600)
        jdbc.update("UPDATE scrape_state SET mode='incremental', last_synced_at=?", java.sql.Timestamp.from(synced))
        jdbc.update("INSERT INTO show (id, name) VALUES (1, 'Old name'), (2, 'Untouched')")
        jdbc.update("INSERT INTO person (id, name) VALUES (10, 'Old actor')")
        jdbc.update("INSERT INTO show_cast (show_id, person_id) VALUES (1, 10)")
        api.updates = mapOf(1L to Instant.now().epochSecond, 2L to synced.epochSecond - 10)
        api.shows = mapOf(1L to ShowDto(1, "New name"))
        api.casts = mapOf(1L to listOf(PersonDto(20, "New actor", "1990-01-01")))

        orchestrator.runOnce()

        assertEquals("New name", jdbc.queryForObject("SELECT name FROM show WHERE id = 1", String::class.java))
        assertEquals("Untouched", jdbc.queryForObject("SELECT name FROM show WHERE id = 2", String::class.java))
        assertEquals(listOf(20L), jdbc.queryForList("SELECT person_id FROM show_cast WHERE show_id = 1", Long::class.java))
        assertTrue(state.get().lastSyncedAt!! > synced)
    }

    @Test
    fun `stale last sync falls back to a full crawl`() {
        jdbc.update("UPDATE scrape_state SET mode='incremental', last_synced_at=?", java.sql.Timestamp.from(Instant.now().minusSeconds(60L * 86400)))
        api.pages = listOf(listOf(show(1)))

        orchestrator.runOnce()

        assertEquals(1, count("show"))
        assertEquals(ScrapeMode.INCREMENTAL, state.get().mode)
    }

    @Test
    fun `second start is refused while a run is in progress`() {
        assertTrue(state.tryStart())
        assertEquals(false, state.tryStart())
        state.finish()
    }
}
