package com.tvmaze.scraper.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("scrape")
data class ScrapeProperties(
    val enabled: Boolean = true,
    val baseUrl: String = "https://api.tvmaze.com",
    val requestDelayMs: Long = 600,
    val maxRetries: Int = 5,
    val incrementalIntervalMs: Long = 3_600_000,
)
