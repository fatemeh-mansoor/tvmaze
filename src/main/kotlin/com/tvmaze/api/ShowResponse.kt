package com.tvmaze.api

import java.time.LocalDate

data class ShowResponse(val id: Long, val name: String, val cast: List<CastMemberResponse>)

data class CastMemberResponse(val id: Long, val name: String, val birthday: LocalDate?)
