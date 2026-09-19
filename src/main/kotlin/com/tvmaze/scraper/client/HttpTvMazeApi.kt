package com.tvmaze.scraper.client

import com.tvmaze.scraper.client.dto.CastCreditDto
import com.tvmaze.scraper.client.dto.PersonDto
import com.tvmaze.scraper.client.dto.ShowDto
import com.tvmaze.scraper.config.ScrapeProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class HttpTvMazeApi(
    private val props: ScrapeProperties,
    builder: RestClient.Builder,
) : TvMazeApi {

    private val log = LoggerFactory.getLogger(javaClass)
    private val client = builder.baseUrl(props.baseUrl).build()

    override fun getShowsPage(page: Int): List<ShowDto>? =
        get("/shows?page=$page") { it.body<List<ShowDto>>() }

    override fun getShow(id: Long): ShowDto? =
        get("/shows/$id") { it.body<ShowDto>() }

    override fun getCast(showId: Long): List<PersonDto> =
        get("/shows/$showId/cast") { it.body(object : ParameterizedTypeReference<List<CastCreditDto>>() {}) }
            ?.map { it.person }
            .orEmpty()

    override fun getUpdates(since: String): Map<Long, Long> =
        get("/updates/shows?since=$since") { it.body(object : ParameterizedTypeReference<Map<Long, Long>>() {}) }
            ?: emptyMap()

    private fun <T> get(uri: String, read: (RestClient.ResponseSpec) -> T?): T? {
        var attempt = 0
        while (true) {
            Thread.sleep(props.requestDelayMs)
            try {
                return read(client.get().uri(uri).retrieve())
            } catch (e: HttpClientErrorException) {
                when (e.statusCode) {
                    HttpStatus.NOT_FOUND -> return null
                    HttpStatus.TOO_MANY_REQUESTS -> backoff(uri, ++attempt, e.responseHeaders?.getFirst("Retry-After"), e)
                    else -> throw e
                }
            } catch (e: HttpServerErrorException) {
                backoff(uri, ++attempt, null, e)
            } catch (e: ResourceAccessException) {
                backoff(uri, ++attempt, null, e)
            }
        }
    }

    private fun backoff(uri: String, attempt: Int, retryAfter: String?, cause: Exception) {
        if (attempt > props.maxRetries) throw cause
        val waitMs = retryAfter?.toLongOrNull()?.times(1000) ?: (1000L shl (attempt - 1)).coerceAtMost(30_000)
        log.warn("GET {} failed ({}), retry {}/{} in {} ms", uri, cause.message, attempt, props.maxRetries, waitMs)
        Thread.sleep(waitMs)
    }
}
