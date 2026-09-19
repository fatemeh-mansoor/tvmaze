package com.tvmaze.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = ["scrape.enabled=false"])
class ShowApiTest {

    companion object {
        @Container @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16")
    }

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun seed() {
        jdbc.update("DELETE FROM show_cast")
        jdbc.update("DELETE FROM show")
        jdbc.update("DELETE FROM person")
        jdbc.update("INSERT INTO show (id, name) VALUES (1, 'Game of Thrones'), (4, 'Big Bang Theory'), (7, 'No Cast')")
        jdbc.update(
            """
            INSERT INTO person (id, name, birthday) VALUES
              (7, 'Mike Vogel', '1979-07-17'), (9, 'Dean Norris', '1963-04-08'),
              (6, 'Michael Emerson', '1950-01-01'), (20, 'Unknown', NULL),
              (21, 'Twin B', '1979-07-17'), (10, 'Twin A', '1979-07-17')
            """.trimIndent(),
        )
        jdbc.update(
            "INSERT INTO show_cast (show_id, person_id) VALUES (1,7),(1,9),(1,20),(1,21),(1,10),(4,6)",
        )
    }

    @Test
    fun `list returns shows by id with cast newest birthday first, unknown last, ties by id`() {
        mvc.get("/shows").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].id") { value(1) }
            jsonPath("$[0].name") { value("Game of Thrones") }
            jsonPath("$[0].cast[0].id") { value(7) }
            jsonPath("$[0].cast[1].id") { value(10) }
            jsonPath("$[0].cast[2].id") { value(21) }
            jsonPath("$[0].cast[3].id") { value(9) }
            jsonPath("$[0].cast[4].id") { value(20) }
            jsonPath("$[0].cast[0].birthday") { value("1979-07-17") }
            jsonPath("$[0].cast[4].birthday") { value(null) }
            jsonPath("$[1].cast[0].name") { value("Michael Emerson") }
            jsonPath("$[2].id") { value(7) }
            jsonPath("$[2].cast.length()") { value(0) }
        }
    }

    @Test
    fun `pagination slices by show id and an empty page is an empty array`() {
        mvc.get("/shows?page=2&size=2").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(7) }
        }
        mvc.get("/shows?page=3&size=2").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `invalid paging params are rejected`() {
        for (q in listOf("page=0", "page=-1", "size=0", "size=101", "page=abc", "size=abc")) {
            mvc.get("/shows?$q").andExpect { status { isBadRequest() } }
        }
    }
}
