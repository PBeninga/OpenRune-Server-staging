package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.Npc

class ExtraHitHookTest {
    @Test
    fun GameTestState.`an extra hit chains up to its cap for the player who has the effect`() {
        val effect = PlayerEffect()
        effect.attack = AttackModifiers(chainCaps = mapOf(HitSource.Echo to 2))
        effect.extraHits = { listOf(ExtraHit.fixed(HitSource.Echo, 1)) }
        runModifierTest(effect.registry()) { deps ->
            player.placeAt(TEST_COORDS)
            effect.owners += player
            val npc = spawnTarget(hitpoints = 250, coords = TEST_COORDS.translateX(1))

            deps.pipeline.withAttack(player, npc, CombatStyle.Ranged) {
                deps.manager.queueRangedHit(player, npc, null, 5, clientDelay = 0, hitDelay = 1)
            }
            advance(2)

            assertEquals(listOf(0, 1, 2), effect.rolled.map { it.chainDepth })
            val dealt = effect.dealt.map { it.source }
            assertEquals(1, dealt.count { it == HitSource.Base })
            assertEquals(3, dealt.count { it == HitSource.Echo })
            assertEquals(250 - 5 - 3, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`players without the effect get no extra hits`() {
        val effect = PlayerEffect()
        effect.extraHits = { listOf(ExtraHit.fixed(HitSource.Echo, 1)) }
        runModifierTest(effect.registry()) { deps ->
            player.placeAt(TEST_COORDS)
            val other = registerPlayer(TEST_COORDS.translateZ(2))
            effect.owners += other
            val npc = spawnTarget(hitpoints = 250, coords = TEST_COORDS.translateX(1))

            deps.manager.queueMeleeHit(player, npc, damage = 2)
            advance(2)

            assertEquals(listOf(HitSource.Base), effect.dealt.map { it.source })
            assertEquals(250 - 2, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`a retargeted extra hit rolls the attack again against another npc`() {
        val effect = PlayerEffect()
        lateinit var second: Npc
        effect.extraHits = { listOf(ExtraHit.retarget(HitSource.Chain, second)) }
        runModifierTest(effect.registry()) { deps ->
            player.placeAt(TEST_COORDS)
            effect.owners += player
            setLevel(player, "stat.attack", 99)
            setLevel(player, "stat.strength", 99)
            val npc = spawnTarget(hitpoints = 250, coords = TEST_COORDS.translateX(1))
            second = spawnTarget(hitpoints = 100, coords = TEST_COORDS.translateZ(1))

            random.next = 0 // Base accuracy roll: hit.
            random.then = 4 // Base damage roll.
            random.then = 0 // Retarget accuracy roll: hit.
            random.then = 6 // Retarget damage roll.
            deps.pipeline.withAttack(player, npc, CombatStyle.Melee) {
                val damage = deps.manager.rollMeleeDamage(player, npc, meleeAttack())
                deps.manager.queueMeleeHit(player, npc, damage)
            }
            advance(2)

            assertEquals(250 - 4, npc.hitpoints)
            assertEquals(100 - 6, second.hitpoints)
            val dealt = effect.dealt.map { Triple(it.source, it.target, it.damage) }
            val expected =
                listOf(Triple(HitSource.Base, npc, 4), Triple(HitSource.Chain, second, 6))
            assertEquals(expected, dealt)
        }
    }

    @Test
    fun GameTestState.`a scaled re-roll deals its share of the rolled damage, at least 1`() {
        val effect = PlayerEffect()
        effect.extraHits = { listOf(ExtraHit.reroll(HitSource.DoubleStrike, damagePercent = 40.0)) }
        runModifierTest(effect.registry()) { deps ->
            player.placeAt(TEST_COORDS)
            effect.owners += player
            setLevel(player, "stat.attack", 99)
            setLevel(player, "stat.strength", 99)
            val npc = spawnTarget(hitpoints = 250, coords = TEST_COORDS.translateX(1))

            for (rerolled in listOf(7, 1)) {
                random.next = 0 // Base accuracy roll: hit.
                random.then = 4 // Base damage roll.
                random.then = 0 // Re-roll accuracy roll: hit.
                random.then = rerolled // Re-roll damage roll.
                deps.pipeline.withAttack(player, npc, CombatStyle.Melee) {
                    val damage = deps.manager.rollMeleeDamage(player, npc, meleeAttack())
                    deps.manager.queueMeleeHit(player, npc, damage)
                }
                advance(2)
            }

            val doubles = effect.dealt.filter { it.source == HitSource.DoubleStrike }
            assertEquals(listOf(2, 1), doubles.map { it.damage })
            assertEquals(250 - 4 - 2 - 4 - 1, npc.hitpoints)
        }
    }

    private fun meleeAttack(): CombatAttack.Melee =
        CombatAttack.Melee(
            weapon = null,
            type = MeleeAttackType.Crush,
            style = MeleeAttackStyle.Accurate,
            stance = CombatStance.Stance1,
        )
}
