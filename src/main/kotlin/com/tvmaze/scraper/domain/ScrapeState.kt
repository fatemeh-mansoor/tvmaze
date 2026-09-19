package com.tvmaze.scraper.domain

import java.time.Instant

enum class ScrapeMode { FULL, INCREMENTAL }

data class ScrapeState(
    val mode: ScrapeMode,
    val nextPage: Int,
    val lastSyncedAt: Instant?,
)
