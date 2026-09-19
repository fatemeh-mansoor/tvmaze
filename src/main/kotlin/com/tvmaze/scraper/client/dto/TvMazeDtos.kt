package com.tvmaze.scraper.client.dto

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class ShowDto(val id: Long, val name: String)

data class CastCreditDto(val person: PersonDto)

data class PersonDto(
    val id: Long,
    val name: String,
    val birthday: String? = null,
) {
    val birthdayDate: LocalDate?
        get() = try {
            birthday?.let(LocalDate::parse)
        } catch (e: DateTimeParseException) {
            null
        }
}

data class ShowWithCast(val show: ShowDto, val cast: List<PersonDto>)
