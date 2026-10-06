package org.rsmod.api.combat.modifiers

import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState

class AttackScopeSpellTest {
    @Test
    fun GameTestState.`an attack scope's spell is named by every context of the attack`() {
        val effect = SpellSpeed()
        runModifierTest(CombatModifierRegistry.of(providers = listOf(effect))) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 50, coords = TEST_COORDS.translateX(1))
            val spell = objType("obj.01_wind_strike")

            deps.pipeline.withAttack(player, npc, CombatStyle.Magic, spell) {
                assertEquals(spell, deps.pipeline.currentAttack(player)?.spell)
                deps.manager.setNextAttackDelay(player, 5)
            }
            assertEquals(4, player.actionDelay - player.currentMapClock)
            assertEquals(listOf(spell), effect.spells)

            deps.pipeline.withAttack(player, npc, CombatStyle.Magic) {
                deps.manager.setNextAttackDelay(player, 5)
            }
            assertEquals(5, player.actionDelay - player.currentMapClock)
            assertNull(effect.spells.last())
        }
    }

    /** Speeds up spell casts by a tick, and records the spell of every context it is asked for. */
    private class SpellSpeed : CombatModifierProvider {
        val spells = mutableListOf<ItemServerType?>()

        override fun attackModifiers(context: AttackContext): AttackModifiers {
            spells += context.spell
            return if (context.spell != null) {
                AttackModifiers(attackSpeedDelta = -1)
            } else {
                AttackModifiers.NONE
            }
        }
    }
}
