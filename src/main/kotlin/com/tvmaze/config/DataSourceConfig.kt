package com.tvmaze.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

@Configuration
class DataSourceConfig {

    /** Writes (scraper, Flyway) go here. */
    @Bean @Primary
    fun dataSource(
        @Value("\${spring.datasource.url}") url: String,
        @Value("\${spring.datasource.username}") username: String,
        @Value("\${spring.datasource.password}") password: String,
    ): DataSource = pool("primary", url, username, password, readOnly = false)

    /** Reads (REST API) go here; falls back to the primary URL when no replica is configured. */
    @Bean
    fun replicaDataSource(
        @Value("\${replica.datasource.url}") url: String,
        @Value("\${spring.datasource.username}") username: String,
        @Value("\${spring.datasource.password}") password: String,
    ): DataSource = pool("replica", url, username, password, readOnly = true)

    private fun pool(name: String, url: String, username: String, password: String, readOnly: Boolean) =
        HikariDataSource(HikariConfig().also {
            it.poolName = name
            it.jdbcUrl = url
            it.username = username
            it.password = password
            it.isReadOnly = readOnly
        })
}
