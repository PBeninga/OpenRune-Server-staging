package org.rsmod.content.leagues.demonicpacts.effects.ranged

import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ModifierMath
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.leagues.demonicpacts.PactsActiveFor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.hit.HitType

/**
 * The ranged stat pacts N4, N5, H6, K1, K2, K6, N8, K4, K8, N6, N7 and H1. In PvP the speed and
 * stat pacts apply; H6 and the bow stacks, which procs prime and build, don't.
 */
class RangedStatPactsTest {
    @Test
    fun GameTestState.`N4 and N5 stack min and max hit on bow hits, capped at 15%`() =
        rangedPactTest {
            own("N4", "N5")
            bowAndArrows()
            val npc = target()
            val baseMaxHit = rangedMaxHit(npc)
            val cap = baseMaxHit * 15 / 100
            assertTrue(cap >= 3) { "Max hit $baseMaxHit is too low to test the cap." }

            repeat(cap + 2) {
                queueRolls(HIT, 1)
                rangedAttack(npc)
            }

            assertEquals(cap, deps.bowStacks.stacks(scope.player))
            val modifiers = modifiers(npc)
            assertEquals(1 + cap, modifiers.minHitFlat)
            assertEquals(cap, modifiers.maxHitFlat)
            assertEquals(baseMaxHit + cap, rangedMaxHit(npc))
        }

    @Test
    fun GameTestState.`N4 alone raises only the min hit`() = rangedPactTest {
        own("N4")
        bowAndArrows()
        val npc = target()
        queueRolls(HIT, 1, HIT, 1)
        rangedAttack(npc)
        rangedAttack(npc)

        val modifiers = modifiers(npc)
        assertEquals(3, modifiers.minHitFlat)
        assertEquals(0, modifiers.maxHitFlat)
    }

    @Test
    fun GameTestState.`N5 stacks only on hits, and halve on damage that wasn't prayed against`() =
        rangedPactTest {
            own("N5")
            bowAndArrows()
            val npc = target()
            queueRolls(MISS)
            rangedAttack(npc)
            assertEquals(0, deps.bowStacks.stacks(scope.player))

            repeat(3) {
                queueRolls(HIT, 1)
                rangedAttack(npc)
            }
            assertEquals(3, deps.bowStacks.stacks(scope.player))

            with(scope) { player.setVarBit("varbit.prayer_protectfrommelee", 1) }
            npcHit(npc, HitType.Melee, damage = 5)
            assertEquals(3, deps.bowStacks.stacks(scope.player))

            npcHit(npc, HitType.Ranged, damage = 5)
            assertEquals(1, deps.bowStacks.stacks(scope.player))
            npcHit(npc, HitType.Typeless, damage = 1)
            assertEquals(0, deps.bowStacks.stacks(scope.player))
        }

    @Test
    fun GameTestState.`bow stacks don't work with the Eclipse atlatl`() = rangedPactTest {
        own("N4", "N5", "K4")
        wield("obj.eclipse_atlatl")
        quiver("obj.atlatl_dart")
        val npc = target()
        queueRolls(HIT, 1)
        rangedAttack(npc)
        assertEquals(0, deps.bowStacks.stacks(scope.player))
        assertEquals(5, attackDelay(npc, 5))
    }

    @Test
    fun GameTestState.`H6 primes a max hit from 3 tiles for the next hit of another style`() =
        rangedPactTest {
            own("H6")
            bowAndArrows()
            val npc = target(tiles = 3)
            val maxHit = rangedMaxHit(npc)

            queueRolls(HIT, maxHit)
            rangedAttack(npc)
            assertEquals(25.0, modifiers(npc, CombatStyle.Melee).maxHitPercent)
            assertEquals(25.0, modifiers(npc, CombatStyle.Magic).maxHitPercent)
            assertEquals(0.0, modifiers(npc).maxHitPercent)

            queueRolls(HIT, 1)
            rangedAttack(npc)
            assertEquals(25.0, modifiers(npc, CombatStyle.Melee).maxHitPercent)

            queueRolls(HIT, 1)
            meleeAttack(npc)
            assertEquals(0.0, modifiers(npc, CombatStyle.Melee).maxHitPercent)
        }

    @Test
    fun GameTestState.`H6 needs the max hit and 3 tiles of distance`() = rangedPactTest {
        own("H6")
        bowAndArrows()
        val close = target(tiles = 2)
        queueRolls(HIT, rangedMaxHit(close))
        rangedAttack(close)
        assertEquals(0.0, modifiers(close, CombatStyle.Melee).maxHitPercent)

        val far = target(tiles = 4)
        queueRolls(HIT, rangedMaxHit(far) - 1)
        rangedAttack(far)
        assertEquals(0.0, modifiers(far, CombatStyle.Melee).maxHitPercent)
    }

    @Test
    fun GameTestState.`K1 makes ranged prayers 30% more effective`() = rangedPactTest {
        own("K1")
        bowAndArrows()
        val npc = target()
        assertEquals(30.0, modifiers(npc).prayerEffectPercent)
        assertEquals(0.0, modifiers(npc, CombatStyle.Melee).prayerEffectPercent)
    }

    @Test
    fun GameTestState.`K2 adds 1 ranged strength per 10 hitpoints off the level`() =
        rangedPactTest {
            own("K2")
            bowAndArrows()
            val npc = target()
            assertEquals(0, modifiers(npc).rangedStrengthFlat)
            with(scope) { player.setCurrentLevel("stat.hitpoints", 64) }
            assertEquals(3, modifiers(npc).rangedStrengthFlat)
            with(scope) { player.setCurrentLevel("stat.hitpoints", 120) }
            assertEquals(2, modifiers(npc).rangedStrengthFlat)
            assertEquals(0, modifiers(npc, CombatStyle.Melee).rangedStrengthFlat)
        }

    @Test
    fun GameTestState.`K6 adds 80% of melee strength to thrown weapons`() = rangedPactTest {
        own("K6")
        wield("obj.rune_dart", count = 100)
        wear(Wearpos.Torso, "obj.bandos_chestplate")
        val npc = target()
        val meleeStrength = deps.wornBonuses.strengthBonus(scope.player)
        assertTrue(meleeStrength > 0)
        assertEquals(meleeStrength * 80 / 100, modifiers(npc).rangedStrengthFlat)
        wield("obj.eclipse_atlatl")
        val atlatlStrength = deps.wornBonuses.strengthBonus(scope.player)
        assertEquals(atlatlStrength * 80 / 100, modifiers(npc).rangedStrengthFlat)
        bowAndArrows()
        assertEquals(0, modifiers(npc).rangedStrengthFlat)
    }

    @Test
    fun GameTestState.`N8 adds 60 ranged attack bonus to thrown weapons`() = rangedPactTest {
        own("N8")
        wield("obj.rune_dart", count = 100)
        val npc = target()
        assertEquals(60, modifiers(npc).attackBonusFlat)
        wield("obj.eclipse_atlatl")
        assertEquals(60, modifiers(npc).attackBonusFlat)
        bowAndArrows()
        assertEquals(0, modifiers(npc).attackBonusFlat)
    }

    @Test
    fun GameTestState.`K4 makes bows attack 1 tick faster`() = rangedPactTest {
        own("K4")
        bowAndArrows()
        val npc = target()
        assertEquals(4, attackDelay(npc, 5))
        wield("obj.xbows_crossbow_runite")
        assertEquals(5, attackDelay(npc, 5))
    }

    @Test
    fun GameTestState.`K8 makes crossbows 2 ticks slower with 70% more damage`() =
        rangedPactTest {
            wield("obj.xbows_crossbow_runite")
            quiver("obj.xbows_crossbow_bolts_runite")
            val npc = target()
            val baseMaxHit = rangedMaxHit(npc)

            own("K8")
            assertEquals(8, attackDelay(npc, 6))
            assertEquals(ModifierMath.modifyMaxHit(baseMaxHit, 70.0, 0), rangedMaxHit(npc))
            bowAndArrows()
            assertEquals(6, attackDelay(npc, 6))
        }

    @Test
    fun GameTestState.`N6 makes crossbow hits always max hit`() = rangedPactTest {
        own("N6")
        wield("obj.xbows_crossbow_runite")
        quiver("obj.xbows_crossbow_bolts_runite")
        val npc = target()
        val maxHit = rangedMaxHit(npc)
        assertEquals(100.0, modifiers(npc).maxHitChancePercent)

        queueRolls(HIT)
        rangedAttack(npc)
        assertEquals(TARGET_HP - maxHit, npc.hitpoints)
    }

    @Test
    fun GameTestState.`N7 makes crossbows roll accuracy twice`() = rangedPactTest {
        own("N7")
        wield("obj.xbows_crossbow_runite")
        val npc = target()
        assertEquals(1, modifiers(npc).extraAccuracyRolls)
        bowAndArrows()
        assertEquals(0, modifiers(npc).extraAccuracyRolls)
    }

    @Test
    fun GameTestState.`H1 gives every style 5% max accuracy plus 5% per tile`() = rangedPactTest {
        own("H1")
        bowAndArrows()
        val adjacent = target(tiles = 1)
        val far = target(tiles = 5)
        assertEquals(10.0, modifiers(adjacent, CombatStyle.Melee).maxAccuracyRollChancePercent)
        assertEquals(30.0, modifiers(far).maxAccuracyRollChancePercent)
        assertEquals(30.0, modifiers(far, CombatStyle.Magic).maxAccuracyRollChancePercent)
    }

    @Test
    fun GameTestState.`in PvP the speed and stat pacts apply, H6 and the bow stacks don't`() =
        rangedPactTest {
            own("K4", "K1", "H1", "H6", "N4", "N5")
            bowAndArrows()
            val npc = target(tiles = 3)
            val opponent = opponent(tiles = 5)
            assertEquals(4, attackDelay(opponent, 5))
            assertEquals(30.0, modifiers(opponent).prayerEffectPercent)
            assertEquals(30.0, modifiers(opponent).maxAccuracyRollChancePercent)

            queueRolls(HIT, rangedMaxHit(npc))
            rangedAttack(npc)
            assertEquals(1, deps.bowStacks.stacks(scope.player))
            assertEquals(25.0, modifiers(npc, CombatStyle.Melee).maxHitPercent)
            assertEquals(1, modifiers(npc).maxHitFlat)

            assertEquals(0.0, modifiers(opponent, CombatStyle.Melee).maxHitPercent)
            assertEquals(0, modifiers(opponent).maxHitFlat)
            assertEquals(0, modifiers(opponent).minHitFlat)
        }

    @Test
    fun GameTestState.`no ranged pact applies once the player's pacts are inactive`() {
        val activation = PactsActiveFor()
        rangedPactTest(activation) {
            activation.players += scope.player
            own("B2", "K1", "K4", "N7", "N8", "H1", "N4", "N5")
            wield("obj.xbows_crossbow_runite")
            val npc = target()
            assertTrue(modifiers(npc) != AttackModifiers.NONE)

            activation.players.clear()
            assertEquals(AttackModifiers.NONE, modifiers(npc))
        }
    }

    @Test
    fun GameTestState.`the batch implements exactly the effects of its 21 nodes`() =
        rangedPactTest {
            val effects = NODES.map { deps.tree[it].effect.id }.toSet()
            assertEquals(RangedPactEffects.ids, effects)
        }

    /** A magic shortbow with rune arrows and a boosted Ranged level: a max hit of 23 (cap 3). */
    private fun RangedPactsTestScope.bowAndArrows() {
        wield("obj.magic_shortbow")
        quiver("obj.rune_arrow")
        with(scope) { player.setCurrentLevel("stat.ranged", BOOSTED_RANGED) }
    }

    private fun RangedPactsTestScope.attackDelay(target: PathingEntity, cycles: Int): Int =
        deps.pipeline.withAttack(scope.player, target, CombatStyle.Ranged) {
            deps.pipeline.modifyAttackDelay(scope.player, cycles)
        }

    private fun RangedPactsTestScope.npcHit(npc: Npc, type: HitType, damage: Int) {
        scope.player.queueHit(npc, 1, type, damage, deps.playerHitModifier)
        scope.advance(SETTLE_TICKS)
    }

    private companion object {
        const val BOOSTED_RANGED = 118

        val NODES: List<String> =
            listOf(
                "B2", "K9", "K10", "E1", "E2", "E3", "K3", "G9", "N9", "N4", "N5", "H6", "K1",
                "K2", "K6", "N8", "K4", "K8", "N6", "N7", "H1",
            )
    }
}
