package com.tvmaze.api

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import javax.sql.DataSource

data class ShowRow(val id: Long, val name: String)

@Repository
class ShowReadRepository(@Qualifier("replicaDataSource") replica: DataSource) {

    private val jdbc = NamedParameterJdbcTemplate(replica)

    fun findPage(limit: Int, offset: Int): List<ShowRow> = jdbc.query(
        "SELECT id, name FROM show ORDER BY id LIMIT :limit OFFSET :offset",
        MapSqlParameterSource("limit", limit).addValue("offset", offset),
    ) { rs, _ -> ShowRow(rs.getLong("id"), rs.getString("name")) }

    /** Cast for all given shows in one query: birthday newest first, unknown birthdays last, id as tiebreak. */
    fun findCast(showIds: Collection<Long>): Map<Long, List<CastMemberResponse>> {
        if (showIds.isEmpty()) return emptyMap()
        val rows = jdbc.query(
            """
            SELECT sc.show_id, p.id, p.name, p.birthday
            FROM show_cast sc JOIN person p ON p.id = sc.person_id
            WHERE sc.show_id IN (:ids)
            ORDER BY sc.show_id, p.birthday DESC NULLS LAST, p.id
            """.trimIndent(),
            MapSqlParameterSource("ids", showIds),
        ) { rs, _ ->
            rs.getLong("show_id") to CastMemberResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDate("birthday")?.toLocalDate(),
            )
        }
        return rows.groupBy({ it.first }, { it.second })
    }
}
