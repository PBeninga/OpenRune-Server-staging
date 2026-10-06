package org.rsmod.api.testing.util

import org.rsmod.api.server.config.ServerConfig

/**
 * The [ServerConfig] every game test runs with. Tests never read `game.yml`, so a developer's local
 * config (database, Central link, drop-rate multiplier) cannot change test results, and no
 * PostgreSQL or Central connection is configured.
 */
internal object TestServerConfig {
    /** The cache revision in `.data/cache`. Keep in step with `game.example.yml`. */
    const val REVISION: Int = 241

    fun create(): ServerConfig =
        ServerConfig(
            name = "test-suite",
            gamePort = 43594,
            revision = REVISION,
            environment = "TEST",
            world = 255,
            database = null,
            central = null,
        )
}
