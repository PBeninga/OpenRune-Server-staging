package org.rsmod.content.leagues.demonicpacts.effects.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ModifierMath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.leagues.demonicpacts.PactsActiveFor
import org.rsmod.content.leagues.demonicpacts.PactsActiveWhile
import org.rsmod.content.leagues.demonicpacts.effects.OPPONENT_HITPOINTS
import org.rsmod.content.leagues.demonicpacts.effects.registerOpponent
import org.rsmod.content.leagues.demonicpacts.state.PactState
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The flat stat pacts: accuracy, style damage, prayer penetration and Defence (54 nodes). All of
 * them apply in PvP too.
 */
class StatPactsTest {
    @Test
    fun GameTestState.`each accuracy node gives its accuracy in every style`() = statPactTest {
        val npc = target()
        for ((node, percent) in ACCURACY_NODES) {
            own(node)
            for (style in CombatStyle.entries) {
                assertEquals(
                    AttackModifiers(accuracyPercent = percent.toDouble()),
                    modifiers(npc, style),
                ) {
                    "$node $style"
                }
            }
        }
    }

    @Test
    fun GameTestState.`accuracy nodes stack additively`() = statPactTest {
        val npc = target()
        own("CA", "CB", "CC", "DB")
        assertEquals(100.0, modifiers(npc, CombatStyle.Melee).accuracyPercent)
        own(*ACCURACY_NODES.keys.toTypedArray())
        assertEquals(300.0, modifiers(npc, CombatStyle.Magic).accuracyPercent)
    }

    @Test
    fun GameTestState.`each melee damage node gives 1% melee damage and 10% accuracy`() =
        statPactTest {
            val npc = target()
            for (node in MELEE_NODES) {
                own(node)
                assertEquals(
                    AttackModifiers(accuracyPercent = 10.0, maxHitPercent = 1.0),
                    modifiers(npc, CombatStyle.Melee),
                ) {
                    node
                }
                assertEquals(
                    AttackModifiers(accuracyPercent = 10.0),
                    modifiers(npc, CombatStyle.Ranged),
                ) {
                    node
                }
                assertEquals(
                    AttackModifiers(accuracyPercent = 10.0),
                    modifiers(npc, CombatStyle.Magic),
                ) {
                    node
                }
            }
        }

    @Test
    fun GameTestState.`the 8 melee damage nodes raise the melee max hit by 8%`() = statPactTest {
        wield("obj.abyssal_whip")
        val npc = target()
        val baseMaxHit = meleeMaxHit(npc)
        own(*MELEE_NODES.toTypedArray())
        assertEquals(80.0, modifiers(npc, CombatStyle.Melee).accuracyPercent)
        assertEquals(ModifierMath.modifyMaxHit(baseMaxHit, 8.0, 0), meleeMaxHit(npc))
    }

    @Test
    fun GameTestState.`each ranged damage node gives 1% ranged damage and 10% accuracy`() =
        statPactTest {
            val npc = target()
            for (node in RANGED_NODES) {
                own(node)
                assertEquals(
                    AttackModifiers(accuracyPercent = 10.0, maxHitPercent = 1.0),
                    modifiers(npc, CombatStyle.Ranged),
                ) {
                    node
                }
                assertEquals(
                    AttackModifiers(accuracyPercent = 10.0),
                    modifiers(npc, CombatStyle.Melee),
                ) {
                    node
                }
                assertEquals(
                    AttackModifiers(accuracyPercent = 10.0),
                    modifiers(npc, CombatStyle.Magic),
                ) {
                    node
                }
            }
        }

    @Test
    fun GameTestState.`the 10 ranged damage nodes raise the ranged max hit by 10%`() =
        statPactTest {
            wield("obj.magic_shortbow")
            quiver("obj.rune_arrow")
            val npc = target()
            val baseMaxHit = rangedMaxHit(npc)
            own(*RANGED_NODES.toTypedArray())
            assertEquals(100.0, modifiers(npc, CombatStyle.Ranged).accuracyPercent)
            assertEquals(ModifierMath.modifyMaxHit(baseMaxHit, 10.0, 0), rangedMaxHit(npc))
        }

    @Test
    fun GameTestState.`each magic damage node gives 1% magic damage and 10% accuracy`() =
        statPactTest {
            val npc = target()
            for (node in MAGIC_NODES) {
                own(node)
                assertEquals(
                    AttackModifiers(accuracyPercent = 10.0, magicDamagePercent = 1.0),
                    modifiers(npc, CombatStyle.Magic),
                ) {
                    node
                }
                assertEquals(
                    AttackModifiers(accuracyPercent = 10.0),
                    modifiers(npc, CombatStyle.Melee),
                ) {
                    node
                }
                assertEquals(
                    AttackModifiers(accuracyPercent = 10.0),
                    modifiers(npc, CombatStyle.Ranged),
                ) {
                    node
                }
            }
        }

    @Test
    fun GameTestState.`Tumeken's shadow triples the magic damage nodes`() = statPactTest {
        val npc = target()
        wield("obj.tots_charged")
        val staffBase = staffMaxHit(npc, STAFF_BASE)
        wield("obj.tumekens_shadow")
        val shadowBase = staffMaxHit(npc, STAFF_BASE)

        own(*MAGIC_NODES.toTypedArray())
        assertEquals(80.0, modifiers(npc, CombatStyle.Magic).accuracyPercent)
        assertEquals(shadowBase + STAFF_BASE * 8 * 3 / 100, staffMaxHit(npc, STAFF_BASE))
        wield("obj.tots_charged")
        assertEquals(staffBase + STAFF_BASE * 8 / 100, staffMaxHit(npc, STAFF_BASE))
    }

    @Test
    fun GameTestState.`all 26 style damage nodes give 260% accuracy in every style`() =
        statPactTest {
            val npc = target()
            own(*(MELEE_NODES + RANGED_NODES + MAGIC_NODES).toTypedArray())
            for (style in CombatStyle.entries) {
                assertEquals(260.0, modifiers(npc, style).accuracyPercent) { "$style" }
            }
            assertEquals(8.0, modifiers(npc, CombatStyle.Melee).maxHitPercent)
            assertEquals(10.0, modifiers(npc, CombatStyle.Ranged).maxHitPercent)
            assertEquals(8.0, modifiers(npc, CombatStyle.Magic).magicDamagePercent)
        }

    @Test
    fun GameTestState.`each prayer penetration node gives 25% in every style`() = statPactTest {
        val npc = target()
        for (node in PENETRATION_NODES) {
            own(node)
            for (style in CombatStyle.entries) {
                assertEquals(
                    AttackModifiers(prayerPenetrationPercent = 25.0),
                    modifiers(npc, style),
                ) {
                    "$node $style"
                }
            }
        }
    }

    @Test
    fun GameTestState.`prayer penetration stacks up to 100%`() = statPactTest {
        val npc = target()
        own("H7", "H10")
        assertEquals(50.0, modifiers(npc, CombatStyle.Melee).prayerPenetrationPercent)
        own(*PENETRATION_NODES.toTypedArray())
        assertEquals(100.0, modifiers(npc, CombatStyle.Ranged).prayerPenetrationPercent)
    }

    @Test
    fun GameTestState.`prayer penetration lets hits through a protection prayer`() =
        statPactTest {
            val npc = target()
            protection.percent = 100.0
            hit(npc, 20)
            assertEquals(STAT_TARGET_HP, npc.hitpoints)

            own("H7", "H10")
            hit(npc, 20)
            assertEquals(STAT_TARGET_HP - 10, npc.hitpoints)

            own(*PENETRATION_NODES.toTypedArray())
            hit(npc, 20)
            assertEquals(STAT_TARGET_HP - 30, npc.hitpoints)
        }

    @Test
    fun GameTestState.`each Defence node boosts Defence without changing the base level`() =
        statPactTest {
            for ((node, levels) in DEFENCE_NODES) {
                own(node)
                assertEquals(99 + levels, player.stat(DEFENCE)) { node }
                assertEquals(99, player.statBase(DEFENCE)) { node }
            }
            own("none")
            assertEquals(99, player.stat(DEFENCE))
        }

    @Test
    fun GameTestState.`the Defence nodes stack to 60, survive a restore and keep combat level`() =
        statPactTest {
            val combatLevel = player.combatLevel
            own(*DEFENCE_NODES.keys.toTypedArray())
            assertEquals(159, player.stat(DEFENCE))
            assertEquals(combatLevel, player.combatLevel)

            with(scope) { player.setCurrentLevel(DEFENCE, 70) }
            player.statRestore(DEFENCE)
            assertEquals(159, player.stat(DEFENCE))
            assertEquals(99, player.statBase(DEFENCE))

            own("DA")
            assertEquals(104, player.stat(DEFENCE))
        }

    @Test
    fun GameTestState.`a relog keeps the Defence boost without adding it again`() =
        statPactTest {
            assertRelogKeepsBoost()
        }

    @Test
    fun GameTestState.`a relog keeps the Defence boost whatever the login order`() =
        statPactTest(scripts = STAT_PACT_SCRIPTS.reversed()) { assertRelogKeepsBoost() }

    @Test
    fun GameTestState.`the Defence boost comes off at login once pacts are off, and back on`() {
        val activation = PactsActiveWhile()
        statPactTest(activation) { assertBoostFollowsActivation(activation) }
    }

    @Test
    fun GameTestState.`the Defence boost follows activation whatever the login order`() {
        val activation = PactsActiveWhile()
        statPactTest(activation, STAT_PACT_SCRIPTS.reversed()) {
            assertBoostFollowsActivation(activation)
        }
    }

    @Test
    fun GameTestState.`taking the Defence boost off at login keeps a drain`() {
        val activation = PactsActiveWhile()
        statPactTest(activation) {
            own("F4", "DA")
            with(scope) { player.setCurrentLevel(DEFENCE, 90) }
            activation.active = false
            assertEquals(90, relog(player).stat(DEFENCE))
        }
    }

    @Test
    fun GameTestState.`a refresh after pacts turn off takes the Defence boost off`() {
        val activation = PactsActiveWhile()
        statPactTest(activation) {
            own("F4", "DA")
            assertEquals(119, player.stat(DEFENCE))
            activation.active = false
            deps.pacts.refresh(player)
            assertEquals(99, player.stat(DEFENCE))
            activation.active = true
            deps.pacts.refresh(player)
            assertEquals(119, player.stat(DEFENCE))
        }
    }

    @Test
    fun GameTestState.`in PvP the accuracy, damage, penetration and Defence pacts apply`() =
        statPactTest {
            val opponent = scope.registerOpponent(STAT_TEST_COORDS.translateX(2))
            wield("obj.abyssal_whip")
            val baseMaxHit = pvpMeleeMaxHit(opponent)
            own("CA", *MELEE_NODES.toTypedArray(), "H7", "H10", "DA")
            assertEquals(
                AttackModifiers(
                    accuracyPercent = 105.0,
                    maxHitPercent = 8.0,
                    prayerPenetrationPercent = 50.0,
                ),
                modifiers(opponent, CombatStyle.Melee),
            )
            assertEquals(ModifierMath.modifyMaxHit(baseMaxHit, 8.0, 0), pvpMeleeMaxHit(opponent))
            assertEquals(104, player.stat(DEFENCE))

            // 50% penetration ignores half of the 40% that Protect from Melee blocks of a player's
            // hit.
            with(scope) { opponent.setVarBit("varbit.prayer_protectfrommelee", 1) }
            deps.pipeline.withAttack(player, opponent, CombatStyle.Melee) {
                deps.manager.queueMeleeHit(player, opponent, damage = 20)
            }
            scope.advance(SETTLE_TICKS)
            assertEquals(OPPONENT_HITPOINTS - 16, opponent.hitpoints)
        }

    @Test
    fun GameTestState.`no stat pact applies once the player's pacts are inactive`() {
        val activation = PactsActiveFor()
        statPactTest(activation) {
            activation.players += player
            own("CA", "GA", "HA", "FA", "H7", "DA")
            val npc = target()
            assertEquals(104, player.stat(DEFENCE))

            activation.players.clear()
            for (style in CombatStyle.entries) {
                assertEquals(AttackModifiers.NONE, modifiers(npc, style)) { "$style" }
            }
        }
    }

    @Test
    fun GameTestState.`the batch implements exactly the effects of its 54 nodes`() =
        statPactTest {
            val nodes =
                ACCURACY_NODES.keys +
                    MELEE_NODES +
                    RANGED_NODES +
                    MAGIC_NODES +
                    PENETRATION_NODES +
                    DEFENCE_NODES.keys
            assertEquals(54, nodes.size)
            assertEquals(StatPactEffects.ids, nodes.map { deps.tree[it].effect.id }.toSet())
        }

    private fun StatPactsTestScope.assertRelogKeepsBoost() {
        own("F4", "DA")
        assertEquals(119, player.stat(DEFENCE))

        val relogged = relog(player)
        assertEquals(119, relogged.stat(DEFENCE))
        assertEquals(99, relogged.statBase(DEFENCE))
        with(scope) { relogged.setCurrentLevel(DEFENCE, 90) }
        relogged.statRestore(DEFENCE)
        assertEquals(119, relogged.stat(DEFENCE))
    }

    private fun StatPactsTestScope.assertBoostFollowsActivation(activation: PactsActiveWhile) {
        own("F4", "DA")
        assertEquals(119, player.stat(DEFENCE))

        activation.active = false
        val off = relog(player)
        assertEquals(99, off.stat(DEFENCE))
        assertEquals(99, off.statBase(DEFENCE))
        assertEquals(0, off.vars[PactState.LEAGUE_TYPE])
        val stillOff = relog(off)
        assertEquals(99, stillOff.stat(DEFENCE))

        activation.active = true
        val on = relog(stillOff)
        assertEquals(119, on.stat(DEFENCE))
        with(scope) { on.setCurrentLevel(DEFENCE, 90) }
        on.statRestore(DEFENCE)
        assertEquals(119, on.stat(DEFENCE))
        assertEquals(119, relog(on).stat(DEFENCE))
    }

    private fun StatPactsTestScope.pvpMeleeMaxHit(opponent: Player): Int =
        deps.manager.calculateMeleeMaxHit(
            player,
            opponent,
            MeleeAttackType.Slash,
            MeleeAttackStyle.Aggressive,
            1.0,
        )

    private fun StatPactsTestScope.hit(npc: Npc, damage: Int) {
        deps.pipeline.withAttack(player, npc, CombatStyle.Melee) {
            deps.manager.queueMeleeHit(player, npc, damage)
        }
        scope.advance(SETTLE_TICKS)
    }

    private companion object {
        const val DEFENCE = "stat.defence"
        const val STAFF_BASE = 100
        const val SETTLE_TICKS = 3

        val ACCURACY_NODES: Map<String, Int> =
            mapOf(
                "CA" to 25,
                "CB" to 25,
                "CC" to 25,
                "DB" to 25,
                "DC" to 25,
                "EB" to 25,
                "F1" to 50,
                "G3" to 50,
                "H3" to 50,
            )

        val MELEE_NODES: List<String> = listOf("GA", "GB", "GC", "GD", "JA", "JB", "JC", "JD")

        val RANGED_NODES: List<String> =
            listOf("HA", "HB", "HC", "HD", "KA", "KB", "KC", "N1", "N2", "N3")

        val MAGIC_NODES: List<String> = listOf("FA", "FB", "FC", "FD", "IA", "IB", "IC", "ID")

        val PENETRATION_NODES: List<String> =
            listOf("H7", "H10", "I5", "I7", "J5", "J7", "J9", "K5", "K7")

        val DEFENCE_NODES: Map<String, Int> =
            mapOf(
                "DA" to 5,
                "EA" to 5,
                "EC" to 5,
                "F11" to 5,
                "F12" to 5,
                "G11" to 5,
                "G12" to 5,
                "H5" to 5,
                "H8" to 5,
                "F4" to 15,
            )
    }
}
