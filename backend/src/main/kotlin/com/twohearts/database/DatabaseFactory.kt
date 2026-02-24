package com.twohearts.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

private val logger = KotlinLogging.logger {}

object DatabaseFactory {

    fun init(url: String, user: String, password: String) {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 20
            minimumIdle = 5
            connectionTimeout = 30_000
            idleTimeout = 600_000
            maxLifetime = 1_800_000
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        })

        // Run Flyway migrations
        logger.info { "Running database migrations..." }
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .validateMigrationNaming(true)
            .load()
            .migrate()
        logger.info { "Migrations complete" }

        // Connect Exposed
        Database.connect(dataSource)
        logger.info { "Exposed ORM connected" }
    }
}
