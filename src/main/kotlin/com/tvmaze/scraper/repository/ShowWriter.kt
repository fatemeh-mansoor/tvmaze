package com.tvmaze.scraper.repository

import com.tvmaze.scraper.client.dto.ShowWithCast
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Date

@Repository
class ShowWriter(
    private val jdbc: JdbcTemplate,
    private val state: ScrapeStateRepository,
) {

    /** Saves a page of shows and moves the checkpoint in one transaction. */
    @Transactional
    fun savePageAndAdvance(shows: List<ShowWithCast>, nextPage: Int) {
        upsert(shows)
        state.advancePage(nextPage)
    }

    @Transactional
    fun save(show: ShowWithCast) = upsert(listOf(show))

    private fun upsert(shows: List<ShowWithCast>) {
        if (shows.isEmpty()) return

        jdbc.batchUpdate(
            """
            INSERT INTO show (id, name, updated_at) VALUES (?, ?, now())
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, updated_at = now()
            """.trimIndent(),
            shows, shows.size,
        ) { ps, s ->
            ps.setLong(1, s.show.id)
            ps.setString(2, s.show.name)
        }

        val people = shows.flatMap { it.cast }.distinctBy { it.id }
        jdbc.batchUpdate(
            """
            INSERT INTO person (id, name, birthday) VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, birthday = EXCLUDED.birthday
            """.trimIndent(),
            people, people.size.coerceAtLeast(1),
        ) { ps, p ->
            ps.setLong(1, p.id)
            ps.setString(2, p.name)
            ps.setDate(3, p.birthdayDate?.let(Date::valueOf))
        }

        // Cast can change upstream, so replace each show's links rather than only adding.
        jdbc.batchUpdate(
            "DELETE FROM show_cast WHERE show_id = ?",
            shows, shows.size,
        ) { ps, s -> ps.setLong(1, s.show.id) }

        val links = shows.flatMap { s -> s.cast.map { s.show.id to it.id } }.distinct()
        jdbc.batchUpdate(
            "INSERT INTO show_cast (show_id, person_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            links, links.size.coerceAtLeast(1),
        ) { ps, (showId, personId) ->
            ps.setLong(1, showId)
            ps.setLong(2, personId)
        }
    }
}
