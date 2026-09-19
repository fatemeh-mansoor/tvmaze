package com.tvmaze.scraper.client

import com.tvmaze.scraper.client.dto.PersonDto
import com.tvmaze.scraper.client.dto.ShowDto

interface TvMazeApi {
    /** One page (up to 250) of shows, without cast; null when the page does not exist. */
    fun getShowsPage(page: Int): List<ShowDto>?

    fun getShow(id: Long): ShowDto?

    /** Cast of a show (people only); empty when the show has none or does not exist. */
    fun getCast(showId: Long): List<PersonDto>

    /** Show id -> last update time (epoch seconds). [since] is "day", "week" or "month". */
    fun getUpdates(since: String): Map<Long, Long>
}
