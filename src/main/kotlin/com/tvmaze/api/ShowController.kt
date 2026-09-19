package com.tvmaze.api

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val MAX_PAGE_SIZE = 100

@RestController
class ShowController(private val shows: ShowReadRepository) {

    @GetMapping("/shows")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): List<ShowResponse> {
        if (page < 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 1")
        if (size !in 1..MAX_PAGE_SIZE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and $MAX_PAGE_SIZE")
        }
        val rows = shows.findPage(size, (page - 1) * size)
        return withCast(rows)
    }

    private fun withCast(rows: List<ShowRow>): List<ShowResponse> {
        val cast = shows.findCast(rows.map { it.id })
        return rows.map { ShowResponse(it.id, it.name, cast[it.id].orEmpty()) }
    }
}
