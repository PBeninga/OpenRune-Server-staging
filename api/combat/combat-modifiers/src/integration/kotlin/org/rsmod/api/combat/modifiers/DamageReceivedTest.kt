package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.player.hit.modifier.asMechanic
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType

/**
 * NvP damage-received hooks: dodge, damage reduction and retaliation, through OpenRune's
 * `PlayerHitModifier` binding as decorated by [CombatModifierPlayerHitModifier].
 */
class DamageReceivedTest {
    @Test
    fun GameTestState.`the player hit modifier binding is the pipeline decorator`() {
        runModifierTest(TestHooks().registry) { deps ->
            assertTrue(deps.playerHitModifier is CombatModifierPlayerHitModifier)
        }
    }

    @Test
    fun GameTestState.`damage reduction reduces npc hits`() {
        val hooks = TestHooks()
        hooks.provider.defence = DefenceModifiers(damageReductionPercent = 30.0)
        hooks.listener.damageResponse = { DamageReceivedResponse(reductionPercent = 20.0) }
        runModifierTest(hooks.registry) { deps ->
            val npc = setUpDefender()

            queueNpcHit(deps, npc, damage = 10)
            advance(2)

            // 30% (provider) + 20% (listener) = 50% of 10.
            assertEquals(99 - 5, player.hitpoints)
            val event = hooks.listener.received.single()
            assertEquals(10, event.rolledDamage)
            assertEquals(10, event.incomingDamage)
            assertTrue(event.context.isMitigable)
            assertTrue(event.context.attacker === npc)
        }
    }

    @Test
    fun GameTestState.`a dodge takes no damage`() {
        val hooks = TestHooks()
        hooks.listener.damageResponse = { DamageReceivedResponse(dodge = true) }
        runModifierTest(hooks.registry) { deps ->
            val npc = setUpDefender()

            queueNpcHit(deps, npc, damage = 25)
            advance(2)

            assertEquals(99, player.hitpoints)
        }
    }

    @Test
    fun GameTestState.`dodge chance is rolled with the game random`() {
        val hooks = TestHooks()
        hooks.provider.defence = DefenceModifiers(dodgeChancePercent = 5.0)
        runModifierTest(hooks.registry) { deps ->
            val npc = setUpDefender()

            // 5% = 500 basis points: a roll of 499 dodges, 500 does not.
            random.next = 499
            queueNpcHit(deps, npc, damage = 10)
            random.next = 500
            queueNpcHit(deps, npc, damage = 7)
            advance(2)

            assertEquals(99 - 7, player.hitpoints)
        }
    }

    @Test
    fun GameTestState.`protection prayers apply before pipeline damage reduction`() {
        val hooks = TestHooks()
        hooks.provider.defence = DefenceModifiers(damageReductionPercent = 50.0)
        runModifierTest(hooks.registry) { deps ->
            val npc = setUpDefender()
            player.setVarBit("varbit.prayer_protectfrommelee", 1)

            queueNpcHit(deps, npc, damage = 10)
            advance(2)

            assertEquals(99, player.hitpoints)
            val event = hooks.listener.received.single()
            assertEquals(10, event.rolledDamage)
            assertEquals(0, event.incomingDamage)
        }
    }

    @Test
    fun GameTestState.`typeless and mechanic damage bypass dodge and damage reduction`() {
        val hooks = TestHooks()
        hooks.provider.defence =
            DefenceModifiers(damageReductionPercent = 50.0, dodgeChancePercent = 100.0)
        hooks.listener.damageResponse = {
            DamageReceivedResponse(
                dodge = true,
                retaliation = listOf(RetaliationHit(HitSource.Thorns, flatDamage = 3)),
            )
        }
        runModifierTest(hooks.registry) { deps ->
            val npc = setUpDefender()

            queueNpcHit(deps, npc, damage = 10, mechanic = true)
            queueNpcHit(deps, npc, damage = 6, type = HitType.Typeless)
            advanceUntil({ npc.hitpoints < 100 - 3 }, timeoutTicks = 5)

            assertEquals(99 - 16, player.hitpoints)
            assertFalse(hooks.listener.received.any { it.context.isMitigable })
            // Thorns still trigger on unmitigable hits.
            assertEquals(100 - 6, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`thorns and recoil hit the attacker once the hit lands, and are tagged`() {
        val hooks = TestHooks()
        hooks.provider.defence = DefenceModifiers(damageReductionPercent = 50.0)
        hooks.listener.damageResponse = {
            DamageReceivedResponse(
                retaliation =
                    listOf(
                        RetaliationHit(HitSource.Thorns, flatDamage = 3, hitType = HitType.Melee),
                        RetaliationHit(HitSource.Recoil, percentOfDamageTaken = 10.0),
                    )
            )
        }
        runModifierTest(hooks.registry) { deps ->
            val npc = setUpDefender()

            // 30 incoming, 15 taken after reduction: recoil deals 10% of 15 = 1.
            queueNpcHit(deps, npc, damage = 30)
            // Thorns trigger on every hitsplat, including 0s; a recoil of 0 is not queued.
            queueNpcHit(deps, npc, damage = 0)

            // Retaliation is queued when the player's hits land, and hits the npc a tick later.
            advanceUntil({ player.hitpoints < 99 }, timeoutTicks = 5)
            assertEquals(99 - 15, player.hitpoints)
            advanceUntil({ npc.hitpoints <= 100 - 1 - 3 - 3 }, timeoutTicks = 3)

            assertEquals(100 - 1 - 3 - 3, npc.hitpoints)
            val dealt = hooks.listener.dealt.map { it.source to it.damage }
            val expected =
                listOf(HitSource.Thorns to 3, HitSource.Recoil to 1, HitSource.Thorns to 3)
            assertEquals(expected, dealt)
            assertTrue(hooks.listener.dealt.all { it.style == null && it.attacker === player })
        }
    }

    @Test
    fun GameTestState.`hits that resolve on impact are modified when they land`() {
        val hooks = TestHooks()
        hooks.provider.defence = DefenceModifiers(damageReductionPercent = 50.0)
        hooks.listener.damageResponse = {
            DamageReceivedResponse(retaliation = listOf(RetaliationHit(HitSource.Thorns, 2)))
        }
        runModifierTest(hooks.registry) { deps ->
            val npc = setUpDefender()

            player.queueImpactHit(npc, 3, HitType.Magic, 20, deps.playerHitModifier)
            advance(1)
            // The modifier (and the pipeline hooks) only run when the hit lands.
            assertTrue(hooks.listener.received.isEmpty())

            advanceUntil({ player.hitpoints < 99 }, timeoutTicks = 5)
            assertEquals(99 - 10, player.hitpoints)
            assertEquals(1, hooks.listener.received.size)

            advanceUntil({ npc.hitpoints < 100 }, timeoutTicks = 3)
            assertEquals(100 - 2, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`no retaliation for a hit that never lands`() {
        val hooks = TestHooks()
        hooks.listener.damageResponse = {
            DamageReceivedResponse(retaliation = listOf(RetaliationHit(HitSource.Thorns, 3)))
        }
        runModifierTest(hooks.registry) { deps ->
            val npc = setUpDefender()

            queueNpcHit(deps, npc, damage = 5, delay = 3)
            player.clearQueue("queue.hit")
            advance(5)

            assertEquals(99, player.hitpoints)
            assertEquals(100, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`real npc attacks use the damage received hooks`() {
        val hooks = TestHooks()
        hooks.listener.damageResponse = {
            DamageReceivedResponse(
                dodge = true,
                retaliation = listOf(RetaliationHit(HitSource.Thorns, flatDamage = 3)),
            )
        }
        runModifierTest(hooks.registry, CombatTestScripts.nvpCombat) {
            val npc = setUpDefender()
            passCombatGracePeriod()

            random.next = 0 // Npc accuracy roll: hit.
            random.then = 1 // Npc damage roll.
            npc.opPlayer2(player)
            advanceUntil({ npc.hitpoints < 100 }, timeoutTicks = 10)

            val event = hooks.listener.received.single()
            assertEquals(1, event.incomingDamage)
            assertTrue(event.context.attacker === npc)
            assertEquals(99, player.hitpoints)
            assertEquals(100 - 3, npc.hitpoints)
        }
    }

    private fun GameTestScope.setUpDefender(): Npc {
        player.placeAt(TEST_COORDS)
        setLevel("stat.hitpoints", 99)
        return spawnTarget(hitpoints = 100)
    }

    private fun GameTestScope.queueNpcHit(
        deps: ModifierTestDeps,
        npc: Npc,
        damage: Int,
        type: HitType = HitType.Melee,
        mechanic: Boolean = false,
        delay: Int = 1,
    ) {
        val modifier =
            if (mechanic) deps.playerHitModifier.asMechanic() else deps.playerHitModifier
        player.queueHit(npc, delay, type, damage, modifier = modifier)
    }
}
