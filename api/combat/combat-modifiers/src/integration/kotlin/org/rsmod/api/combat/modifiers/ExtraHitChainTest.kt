package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc

/** Capped chaining of extra hits (pacts K3, D3) and extra targets (pacts N9, L3). */
class ExtraHitChainTest {
    @Test
    fun GameTestState.`extra hits chain up to their cap and only into their own source`() {
        val hooks = TestHooks()
        hooks.provider.attack = AttackModifiers(chainCaps = mapOf(HitSource.Echo to 2))
        hooks.listener.extraHits = {
            listOf(ExtraHit.fixed(HitSource.Echo, 1), ExtraHit.fixed(HitSource.Cleave, 7))
        }
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 250)

            deps.pipeline.withAttack(player, npc, CombatStyle.Ranged) {
                deps.manager.queueRangedHit(player, npc, null, 5, clientDelay = 0, hitDelay = 1)
            }
            advance(2)

            val rolled = hooks.listener.rolled
            assertEquals(listOf(0, 1, 2), rolled.map { it.chainDepth })
            val sources = listOf(HitSource.Base, HitSource.Echo, HitSource.Echo)
            assertEquals(sources, rolled.map { it.source })
            assertTrue(rolled.drop(1).all { it.isChained && !it.critical })

            // The base hit cleaves once; chained events only queue echoes: three in all.
            val dealt = hooks.listener.dealt.map { it.source }
            assertEquals(1, dealt.count { it == HitSource.Base })
            assertEquals(1, dealt.count { it == HitSource.Cleave })
            assertEquals(3, dealt.count { it == HitSource.Echo })
            assertEquals(250 - 5 - 7 - 3, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`without a chain cap extra hits never chain`() {
        val hooks = TestHooks()
        hooks.listener.extraHits = { listOf(ExtraHit.fixed(HitSource.Echo, 1)) }
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 250)
            deps.manager.queueMeleeHit(player, npc, damage = 2)
            advance(2)
            assertEquals(listOf(0), hooks.listener.rolled.map { it.chainDepth })
            assertEquals(250 - 2 - 1, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`a retargeted extra hit makes a full attack roll on another npc`() {
        val hooks = TestHooks()
        lateinit var second: Npc
        hooks.listener.extraHits = { listOf(ExtraHit.retarget(HitSource.Chain, second)) }
        runModifierTest(hooks.registry) { deps ->
            setUpStrongPlayer()
            val npc = spawnTarget(hitpoints = 250)
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
            val dealt = hooks.listener.dealt.map { Triple(it.source, it.target, it.damage) }
            val expected =
                listOf(Triple(HitSource.Base, npc, 4), Triple(HitSource.Chain, second, 6))
            assertEquals(expected, dealt)
            // The re-roll was made against the second npc, tagged with the extra-hit source.
            assertTrue(
                hooks.provider.attackContexts.any {
                    it.target === second && it.source == HitSource.Chain
                }
            )
        }
    }

    @Test
    fun GameTestState.`a retargeted extra hit is skipped when the attack made no rolls`() {
        val hooks = TestHooks()
        lateinit var second: Npc
        hooks.listener.extraHits = { listOf(ExtraHit.retarget(HitSource.Chain, second)) }
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 250)
            second = spawnTarget(hitpoints = 100, coords = TEST_COORDS.translateZ(1))
            deps.pipeline.withAttack(player, npc, CombatStyle.Melee) {
                deps.manager.queueMeleeHit(player, npc, damage = 3)
            }
            advance(2)
            assertEquals(100, second.hitpoints)
            assertEquals(listOf(HitSource.Base), hooks.listener.dealt.map { it.source })
        }
    }

    private fun GameTestScope.setUpStrongPlayer() {
        player.placeAt(TEST_COORDS)
        setLevel("stat.attack", 99)
        setLevel("stat.strength", 99)
    }

    private fun meleeAttack(): CombatAttack.Melee =
        CombatAttack.Melee(
            weapon = null,
            type = MeleeAttackType.Crush,
            style = MeleeAttackStyle.Accurate,
            stance = CombatStance.Stance1,
        )
}
