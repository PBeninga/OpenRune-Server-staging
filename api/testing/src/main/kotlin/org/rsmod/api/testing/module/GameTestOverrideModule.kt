package org.rsmod.api.testing.module

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import org.rsmod.api.db.Database
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.random.CoreRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.realm.Realm
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.testing.random.FixedRandom
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.testing.util.TestRealmConfig
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Replaces the server bindings that would make a game test slow, shared or non-deterministic. It
 * overrides the server's module graph (`Modules.override`), so everything else is bound exactly as
 * in production.
 */
internal class GameTestOverrideModule(
    private val collision: CollisionFlagMap?,
    private val serverConfig: ServerConfig,
) : AbstractModule() {
    override fun configure() {
        if (collision != null) {
            // A per-test copy of the map's collision. Zones are copied by reference, so a test that
            // adds collision (locs, npcs) to an allocated zone mutates the shared map, as in rsmod.
            bind(CollisionFlagMap::class.java).toInstance(collision)

            // Map locs are not registered: each test starts with an empty loc registry and places
            // the locs it needs (`placeMapLoc`).
            bind(LocZoneStorage::class.java).`in`(Scopes.SINGLETON)
        }

        bind(GameExceptionHandler::class.java).toInstance(GameExceptionHandler { t, _ -> throw t })

        bind(GameRandom::class.java)
            .annotatedWith(CoreRandom::class.java)
            .toInstance(FixedRandom(start = 0))

        val random = GameTestScope.VariableGameRandom()
        bind(GameRandom::class.java).toInstance(random.impl)
        bind(GameTestScope.VariableGameRandom::class.java).toInstance(random)

        bind(ServerConfig::class.java).toInstance(serverConfig)
        bind(Realm::class.java).toInstance(createRealm())
        bind(Database::class.java).toInstance(ThrowDatabase)
    }

    private fun createRealm(): Realm {
        val realm = Realm(name = "test-suite")
        realm.updateConfig(TestRealmConfig.create())
        return realm
    }

    private object ThrowDatabase : Database {
        override suspend fun <T> withTransaction(block: (DatabaseConnection) -> T): T =
            error(
                "No database is available in game tests. If a test needs one, bind a test " +
                    "`Database` with `runInjectedGameTest(..., childModule = ...)`."
            )
    }
}
