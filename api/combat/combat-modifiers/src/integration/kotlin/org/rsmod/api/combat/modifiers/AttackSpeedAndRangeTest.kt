package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.config.constants
import org.rsmod.api.testing.GameTestState

/** Attack-speed and attack-range modifiers, directly and through the real PvN combat script. */
class AttackSpeedAndRangeTest {
    @Test
    fun GameTestState.`attack speed delta changes the next attack delay with a floor`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget()
            val manager = deps.manager
            val pipeline = deps.pipeline

            fun delayFor(cycles: Int): Int {
                pipeline.withAttack(player, npc, CombatStyle.Melee) {
                    manager.setNextAttackDelay(player, cycles)
                }
                return player.actionDelay - player.currentMapClock
            }

            hooks.provider.attack = AttackModifiers(attackSpeedDelta = -2, attackSpeedFloor = 3)
            assertEquals(4, delayFor(6))
            assertEquals(3, delayFor(4))
            // A weapon already faster than the floor is not slowed down.
            assertEquals(2, delayFor(2))

            hooks.provider.attack = AttackModifiers(attackSpeedDelta = 2)
            assertEquals(6, delayFor(4))

            // Outside of an attack scope, attack delays are never modified.
            manager.setNextAttackDelay(player, 6)
            assertEquals(6, player.actionDelay - player.currentMapClock)
        }
    }

    @Test
    fun GameTestState.`attack speed applies to real PvN attacks`() {
        val hooks = TestHooks()
        hooks.provider.attack = AttackModifiers(attackSpeedDelta = -1)
        var attackDelay = -1
        hooks.listener.extraHits = { event ->
            attackDelay =
                event.context.attacker.actionDelay - event.context.attacker.currentMapClock
            emptyList()
        }
        runModifierTest(hooks.registry, CombatTestScripts.pvnCombat) {
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget()
            passCombatGracePeriod()

            player.opNpc2(npc)
            advanceUntil({ hooks.listener.rolled.isNotEmpty() }, timeoutTicks = 10)

            // Unarmed attacks use the default attack rate.
            assertEquals(constants.combat_default_attackrate - 1, attackDelay)
        }
    }

    @Test
    fun GameTestState.`attack range delta lets melee attack from further away`() {
        val hooks = TestHooks()
        hooks.provider.attack = AttackModifiers(attackRangeDelta = 2)
        runModifierTest(hooks.registry, CombatTestScripts.pvnCombat) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(coords = TEST_COORDS.translateX(3))
            assertEquals(3, deps.pipeline.modifyAttackRange(player, npc, CombatStyle.Melee, 1))
            passCombatGracePeriod()

            player.opNpc2(npc)
            advanceUntil({ hooks.listener.rolled.isNotEmpty() }, timeoutTicks = 10)

            // The player attacked without walking up to the npc.
            assertEquals(TEST_COORDS, player.coords)
        }
    }

    @Test
    fun GameTestState.`without range modifiers melee walks up to the npc`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry, CombatTestScripts.pvnCombat) {
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(coords = TEST_COORDS.translateX(3))
            passCombatGracePeriod()

            player.opNpc2(npc)
            advanceUntil({ hooks.listener.rolled.isNotEmpty() }, timeoutTicks = 10)

            assertNotEquals(TEST_COORDS, player.coords)
        }
    }
}
