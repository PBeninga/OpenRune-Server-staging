package org.rsmod.api.stats.plugin

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.config.constants
import org.rsmod.api.player.stat.OverhealProvider
import org.rsmod.api.player.stat.PlayerHealSource
import org.rsmod.api.player.stat.PlayerHealing
import org.rsmod.api.player.stat.StatBoostProvider
import org.rsmod.api.player.stat.StatBoosts
import org.rsmod.api.player.stat.TimedStatBoost
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/** Overheal ([PlayerHealing]) and permanent and timed stat boosts ([StatBoosts]). */
class StatBoostAndHealingTest {
    @Test
    fun GameTestState.`overheal goes up to the highest provider cap and decays`() {
        val hooks = TestStatHooks(overheal = listOf(30.0, 20.0))
        runStatTest(hooks) { deps ->
            setLevel("stat.hitpoints", 100)
            player.setCurrentLevel("stat.hitpoints", 50)
            val healing = deps.healing

            // Standard healing stops at the base level.
            assertEquals(50, healing.heal(player, 500, PlayerHealSource.Standard))
            assertEquals(100, player.hitpoints)

            // Pact healing overheals by the highest cap (30%), not the sum.
            assertEquals(130, healing.healCap(player, PlayerHealSource.Pact))
            assertEquals(30, healing.heal(player, 500, PlayerHealSource.Pact))
            assertEquals(130, player.hitpoints)
            assertEquals(0, healing.heal(player, 5, PlayerHealSource.Pact))
            assertEquals(30, healing.overhealed(player))

            // Food never lowers overheal (and no longer throws above the base level).
            player.statHeal("stat.hitpoints", 10, 0)
            assertEquals(130, player.hitpoints)

            // Overheal can be spent, but only if there is enough of it.
            assertTrue(healing.consumeOverheal(player, 5))
            assertEquals(125, player.hitpoints)
            assertFalse(healing.consumeOverheal(player, 26))

            // Overhealed hitpoints decay like a boosted stat.
            val interval = constants.stat_boost_restore_interval
            player.softTimer("timer.stat_boost_restore", interval)
            advance(interval)
            assertEquals(124, player.hitpoints)
        }
    }

    @Test
    fun GameTestState.`without overheal providers healing never overheals`() {
        runStatTest(TestStatHooks()) { deps ->
            setLevel("stat.hitpoints", 99)
            player.setCurrentLevel("stat.hitpoints", 90)
            assertEquals(9, deps.healing.heal(player, 50, PlayerHealSource.Pact))
            assertEquals(99, player.hitpoints)
        }
    }

    @Test
    fun GameTestState.`permanent boosts raise the resting level and survive restores`() {
        val hooks = TestStatHooks()
        runStatTest(hooks) { deps ->
            setLevel("stat.defence", 99)
            hooks.permanent = mapOf("stat.defence" to 15)
            deps.boosts.refresh(player)
            assertEquals(114, player.stat("stat.defence"))
            assertEquals(99, player.statBase("stat.defence"))

            // A restore (or death) brings the stat back to the boosted level.
            player.setCurrentLevel("stat.defence", 80)
            player.statRestore("stat.defence")
            assertEquals(114, player.stat("stat.defence"))
            player.setCurrentLevel("stat.defence", 105)
            player.statHeal("stat.defence", 50, 0)
            assertEquals(114, player.stat("stat.defence"))

            // Boosts above it decay down to it, and drains regenerate up to it.
            val decay = constants.stat_boost_restore_interval
            player.setCurrentLevel("stat.defence", 115)
            player.softTimer("timer.stat_boost_restore", decay)
            advance(decay)
            assertEquals(114, player.stat("stat.defence"))
            advance(decay)
            assertEquals(114, player.stat("stat.defence"))
            player.clearSoftTimer("timer.stat_boost_restore")

            val regen = constants.stat_regen_interval
            player.setCurrentLevel("stat.defence", 112)
            player.softTimer("timer.stat_regen", regen)
            advance(regen * 3)
            assertEquals(114, player.stat("stat.defence"))
            player.clearSoftTimer("timer.stat_regen")

            // Removing the boost lowers the stat, but keeps a drain.
            hooks.permanent = emptyMap()
            deps.boosts.refresh(player)
            assertEquals(99, player.stat("stat.defence"))
            hooks.permanent = mapOf("stat.defence" to 5)
            deps.boosts.refresh(player)
            player.setCurrentLevel("stat.defence", 90)
            hooks.permanent = emptyMap()
            deps.boosts.refresh(player)
            assertEquals(90, player.stat("stat.defence"))
        }
    }

    @Test
    fun GameTestState.`login sets up boost floors without boosting the saved level again`() {
        val hooks = TestStatHooks()
        hooks.permanent = mapOf("stat.defence" to 15)
        runStatTest(hooks) { deps ->
            setLevel("stat.defence", 99)
            player.setCurrentLevel("stat.defence", 114)
            deps.boosts.initialise(player)
            assertEquals(114, player.stat("stat.defence"))
            player.setCurrentLevel("stat.defence", 99)
            player.statRestore("stat.defence")
            assertEquals(114, player.stat("stat.defence"))
        }
    }

    @Test
    fun GameTestState.`timed boosts stack to their cap, reset their duration and expire`() {
        val hooks = TestStatHooks()
        runStatTest(hooks) { deps ->
            setLevel("stat.magic", 99)
            val boost = deps.boosts.timedBoosts.single()
            repeat(12) { deps.boosts.addTimedBoost(player, boost, 1, cap = 10) }
            assertEquals(109, player.stat("stat.magic"))
            assertEquals(10, player.vars[boost.amountVar])

            advance(20)
            deps.boosts.addTimedBoost(player, boost, 1, cap = 10)
            advance(TEST_DURATION - 1)
            assertEquals(109, player.stat("stat.magic"))
            advance(2)
            assertEquals(99, player.stat("stat.magic"))
            assertEquals(0, player.vars[boost.amountVar])
        }
    }

    private fun GameTestState.runStatTest(
        hooks: TestStatHooks,
        testBody: GameTestScope.(StatTestDeps) -> Unit,
    ) {
        runInjectedGameTest(
            StatTestDeps::class,
            hooks.module,
            StatRegenScript::class,
            StatBoostScript::class,
        ) { deps ->
            player.placeAt(TEST_COORDS)
            testBody(deps)
        }
    }

    class StatTestDeps
    @Inject
    constructor(val boosts: StatBoosts, val healing: PlayerHealing)

    class TestMagicBoost :
        TimedStatBoost(
            stat = "stat.magic",
            timer = "timer.heart_cooldown",
            amountVar = "varp.generic_temp_state_65516",
            durationTicks = TEST_DURATION,
        )

    private class TestStatHooks(overheal: List<Double> = emptyList()) {
        var permanent: Map<String, Int> = emptyMap()

        private val boostProvider =
            object : StatBoostProvider {
                override fun permanentBoosts(player: Player): Map<String, Int> = permanent
            }

        private val overhealProviders =
            overheal.map { percent ->
                object : OverhealProvider {
                    override fun overhealPercent(
                        player: Player,
                        source: PlayerHealSource,
                    ): Double = percent
                }
            }

        val module =
            object : AbstractModule() {
                override fun configure() {
                    Multibinder.newSetBinder(binder(), StatBoostProvider::class.java)
                        .addBinding()
                        .toInstance(boostProvider)
                    Multibinder.newSetBinder(binder(), TimedStatBoost::class.java)
                        .addBinding()
                        .to(TestMagicBoost::class.java)
                    val overheals = Multibinder.newSetBinder(binder(), OverhealProvider::class.java)
                    for (provider in overhealProviders) {
                        overheals.addBinding().toInstance(provider)
                    }
                }
            }
    }

    private fun GameTestScope.setLevel(stat: String, level: Int) {
        player.setBaseLevel(stat, level)
        player.setCurrentLevel(stat, level)
    }

    private companion object {
        private const val TEST_DURATION = 30
        private val TEST_COORDS = CoordGrid(0, 50, 50, 22, 18)
    }
}
