package org.rsmod.api.combat.formulas

import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.google.inject.Scopes
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.accuracy.AccuracyRollModifier
import org.rsmod.api.combat.formulas.accuracy.melee.PvNMeleeAccuracy
import org.rsmod.api.combat.formulas.maxhit.MaxHitModifier
import org.rsmod.api.combat.formulas.maxhit.melee.PvNMeleeMaxHit
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.player.righthand
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/** [AccuracyRollModifier] and [MaxHitModifier]: callers can adjust the formulas' inputs. */
class FormulaModifierTest {
    @Test
    fun GameTestState.`no modifier leaves accuracy and max hit unchanged`() =
        runFormulaTest { deps ->
            setUp()
            val npc = Npc(test_npcs.abyssal_demon)
            val chance = deps.hitChance(player, npc, AccuracyRollModifier.NONE)
            val maxHit = deps.maxHit(player, npc, MaxHitModifier.NONE)
            assertEquals(deps.hitChance(player, npc, null), chance)
            assertEquals(deps.maxHit(player, npc, null), maxHit)
        }

    @Test
    fun GameTestState.`roll modifiers change the hit chance`() =
        runFormulaTest { deps ->
            setUp()
            val npc = Npc(test_npcs.abyssal_demon)
            val base = deps.hitChance(player, npc, AccuracyRollModifier.NONE)
            val doubledAttack =
                object : AccuracyRollModifier {
                    override fun modifyAttackRoll(attackRoll: Int): Int = attackRoll * 2
                }
            val noDefence =
                object : AccuracyRollModifier {
                    override fun modifyDefenceRoll(defenceRoll: Int): Int = 0
                }
            val fixedChance =
                object : AccuracyRollModifier {
                    override fun modifyHitChance(
                        hitChance: Int,
                        attackRoll: Int,
                        defenceRoll: Int,
                    ): Int = 1234
                }
            assertTrue(deps.hitChance(player, npc, doubledAttack) > base)
            assertTrue(deps.hitChance(player, npc, noDefence) > base)
            assertEquals(1234, deps.hitChance(player, npc, fixedChance))
        }

    @Test
    fun GameTestState.`a strength bonus term raises the max hit`() =
        runFormulaTest { deps ->
            setUp()
            val npc = Npc(test_npcs.abyssal_demon)
            val base = deps.maxHit(player, npc, MaxHitModifier.NONE)
            val stronger =
                object : MaxHitModifier {
                    override fun modifyStrengthBonus(strengthBonus: Int): Int =
                        strengthBonus + 100
                }
            assertTrue(deps.maxHit(player, npc, stronger) > base)
        }

    private fun GameTestState.runFormulaTest(testBody: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            TestModule,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            testBody = testBody,
        )

    private object TestModule : AbstractModule() {
        override fun configure() {
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    private fun GameTestScope.setUp() {
        for (stat in listOf("stat.attack", "stat.strength", "stat.hitpoints")) {
            player.setBaseLevel(stat, 99)
            player.setCurrentLevel(stat, 99)
        }
        player.righthand = InvObj("obj.abyssal_whip")
    }

    class Deps
    @Inject
    constructor(private val accuracy: PvNMeleeAccuracy, private val maxHits: PvNMeleeMaxHit) {
        fun hitChance(
            player: Player,
            npc: Npc,
            modifier: AccuracyRollModifier?,
        ): Int =
            if (modifier == null) {
                accuracy.getHitChance(player, npc, SLASH, ACCURATE, SLASH, 1.0)
            } else {
                accuracy.getHitChance(player, npc, SLASH, ACCURATE, SLASH, 1.0, modifier)
            }

        fun maxHit(
            player: Player,
            npc: Npc,
            modifier: MaxHitModifier?,
        ): Int =
            if (modifier == null) {
                maxHits.getMaxHit(player, npc, SLASH, ACCURATE, 1.0)
            } else {
                maxHits.getMaxHit(player, npc, SLASH, ACCURATE, 1.0, maxHitModifier = modifier)
            }
    }

    private companion object {
        private val SLASH = MeleeAttackType.Slash
        private val ACCURATE = MeleeAttackStyle.Accurate
    }
}
