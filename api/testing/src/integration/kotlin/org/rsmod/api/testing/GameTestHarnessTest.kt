package org.rsmod.api.testing

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.db.Database
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.random.GameRandom
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.testing.random.FixedRandom
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class GameTestHarnessTest {
    @Test
    fun GameTestState.`queued rolls are returned in order`() =
        runInjectedGameTest(Deps::class) { deps ->
            random.next = 3
            random.then = 7
            assertEquals(3, deps.random.of(10))
            assertEquals(7, deps.random.of(1, 10))
        }

    @Test
    fun GameTestState.`unqueued rolls are seeded`() {
        val first = mutableListOf<Int>()
        val second = mutableListOf<Int>()
        runInjectedGameTest(Deps::class) { deps -> repeat(5) { first += deps.random.of(1000) } }
        runInjectedGameTest(Deps::class) { deps -> repeat(5) { second += deps.random.of(1000) } }
        Assertions.assertEquals(first, second)
    }

    @Test
    fun GameTestState.`advance ticks the map clock`() = runGameTest {
        val start = mapClock.cycle
        advance(3)
        assertEquals(start + 3, mapClock.cycle)
    }

    @Test
    fun GameTestState.`spawned npcs are registered`() =
        runInjectedGameTest(Deps::class) { deps ->
            val coords = CoordGrid(0, 50, 50, 10, 10)
            allocZoneCollision(coords)
            val npc = spawnNpc(coords, "npc.man")
            assertEquals(npc, deps.npcs[npc.slotId])
            assertEquals(npcType("npc.man"), npc.type)
        }

    @Test
    fun GameTestState.`unknown cheat fails the test`() = runGameTest {
        player.assertThrows<AssertionFailedError> { player.cheat("not_a_command") }
    }

    @Test
    fun GameTestState.`tests run without a database or central`() =
        runInjectedGameTest(Deps::class) { deps ->
            assertNull(deps.config.database)
            assertNull(deps.config.central)
            player.assertThrows<IllegalStateException> {
                runBlocking { deps.database.withTransaction {} }
            }
        }

    @Test
    fun GameTestState.`child modules override bindings and add set elements`() =
        runInjectedGameTest(HookDeps::class, childModule = ChildModule) { deps ->
            assertEquals(4, deps.random.of(10))
            assertTrue(DenyAll in deps.teleportHooks)
        }

    private object DenyAll : PlayerTeleportValidateHook {
        override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String =
            "Denied."
    }

    private object ChildModule : AbstractModule() {
        override fun configure() {
            bind(GameRandom::class.java).toInstance(FixedRandom(start = 4))
            Multibinder.newSetBinder(binder(), PlayerTeleportValidateHook::class.java)
                .addBinding()
                .toInstance(DenyAll)
        }
    }

    class HookDeps
    @Inject
    constructor(val random: GameRandom, val teleportHooks: Set<PlayerTeleportValidateHook>)

    class Deps
    @Inject
    constructor(
        val random: GameRandom,
        val npcs: NpcList,
        val database: Database,
        val config: ServerConfig,
    )
}
