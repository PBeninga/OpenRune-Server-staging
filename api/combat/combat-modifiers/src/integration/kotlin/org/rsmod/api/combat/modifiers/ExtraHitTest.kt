package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc

/** Extra hits are tagged with their [HitSource], never crit and never chain. */
class ExtraHitTest {
    @Test
    fun GameTestState.`extra hits are tagged, never crit and never chain`() {
        val hooks = TestHooks()
        hooks.provider.attack = AttackModifiers(criticalChancePercent = 100.0)
        hooks.listener.extraHits = {
            listOf(ExtraHit.reroll(HitSource.DoubleStrike), ExtraHit.fixed(HitSource.Cleave, 5))
        }
        runModifierTest(hooks.registry) { deps ->
            setUpStrongPlayer()
            val npc = spawnTarget(hitpoints = 250)

            random.next = 0 // Base accuracy roll: hit.
            random.then = 3 // Base damage roll.
            random.then = 0 // Double strike accuracy roll: hit.
            random.then = 2 // Double strike damage roll.
            deps.pipeline.withAttack(player, npc, CombatStyle.Melee) {
                val damage = deps.manager.rollMeleeDamage(player, npc, meleeAttack())
                deps.manager.queueMeleeHit(player, npc, damage)
            }
            advance(2)

            // `onHitRolled` only fired for the base hit: extra hits never chain.
            val rolled = hooks.listener.rolled.single()
            assertEquals(HitSource.Base, rolled.context.source)
            assertTrue(rolled.critical)
            assertEquals(3, rolled.rolledDamage)
            assertEquals(12, rolled.damage)

            // Every hit is reported with its tag; the extra hits did not crit.
            val dealt = hooks.listener.dealt.map { it.source to it.damage }
            val expected =
                listOf(HitSource.Base to 12, HitSource.DoubleStrike to 2, HitSource.Cleave to 5)
            assertEquals(expected, dealt)
            assertEquals(250 - 12 - 2 - 5, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`re-rolled extra hits roll accuracy separately`() {
        val hooks = TestHooks()
        hooks.listener.extraHits = { listOf(ExtraHit.reroll(HitSource.Echo)) }
        runModifierTest(hooks.registry) { deps ->
            setUpStrongPlayer()
            val npc = spawnTarget(hitpoints = 250)

            random.next = 0 // Base accuracy roll: hit.
            random.then = 4 // Base damage roll.
            random.then = 9_999 // Echo accuracy roll: miss.
            deps.pipeline.withAttack(player, npc, CombatStyle.Melee) {
                val damage = deps.manager.rollMeleeDamage(player, npc, meleeAttack())
                deps.manager.queueMeleeHit(player, npc, damage)
            }
            advance(2)

            val dealt = hooks.listener.dealt.map { it.source to it.damage }
            assertEquals(listOf(HitSource.Base to 4, HitSource.Echo to 0), dealt)
            // The re-roll saw the extra-hit source in its attack context.
            assertTrue(hooks.provider.attackContexts.any { it.source == HitSource.Echo })
        }
    }

    @Test
    fun GameTestState.`fixed extra hits can target another npc and land later`() {
        val hooks = TestHooks()
        lateinit var second: Npc
        hooks.listener.extraHits = {
            listOf(ExtraHit.fixed(HitSource.Chain, 6, target = second, delayOffset = 2))
        }
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 100)
            second = spawnTarget(hitpoints = 100, coords = TEST_COORDS.translateZ(1))

            deps.manager.queueMagicHit(player, npc, null, 10, clientDelay = 0, hitDelay = 1)
            advance(2)
            assertEquals(90, npc.hitpoints)
            assertEquals(100, second.hitpoints)

            advance(2)
            assertEquals(94, second.hitpoints)
            val chain = hooks.listener.dealt.single { it.source == HitSource.Chain }
            assertTrue(chain.target === second)
            assertEquals(CombatStyle.Magic, chain.style)
        }
    }

    @Test
    fun GameTestState.`re-rolls are skipped without an attack scope`() {
        val hooks = TestHooks()
        hooks.listener.extraHits = { listOf(ExtraHit.reroll(HitSource.DoubleStrike)) }
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 100)

            deps.manager.queueMeleeHit(player, npc, damage = 10)
            advance(2)

            assertEquals(listOf(HitSource.Base), hooks.listener.dealt.map { it.source })
            assertEquals(90, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`lifesteal can be implemented with onHitDealt`() {
        val hooks = TestHooks()
        hooks.listener.extraHits = { listOf(ExtraHit.fixed(HitSource.Cleave, 4)) }
        val lifesteal =
            object : CombatProcListener {
                override fun onHitDealt(event: HitDealtEvent) {
                    event.attacker.statHeal(
                        "stat.hitpoints",
                        constant = event.damage / 2,
                        percent = 0,
                    )
                }
            }
        val registry = CombatModifierRegistry.of(listeners = listOf(hooks.listener, lifesteal))
        runModifierTest(registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.hitpoints", 99)
            player.setCurrentLevel("stat.hitpoints", 50)
            val npc = spawnTarget(hitpoints = 100)

            deps.manager.queueMeleeHit(player, npc, damage = 10)
            advance(2)

            // Heals half of the base hit (5) and half of the extra hit (2).
            assertEquals(57, player.hitpoints)
        }
    }

    @Test
    fun GameTestState.`full PvN attack applies modifiers and queues extra hits`() {
        val hooks = TestHooks()
        hooks.provider.attack =
            AttackModifiers(criticalChancePercent = 100.0, attackSpeedDelta = -1)
        hooks.listener.extraHits = { event ->
            if (event.context.isSpecial) emptyList() else listOf(ExtraHit.reroll(HitSource.Echo))
        }
        runModifierTest(hooks.registry, CombatTestScripts.pvnCombat) { deps ->
            setUpStrongPlayer()
            val npc = spawnTarget(hitpoints = 250)
            passCombatGracePeriod()

            random.next = 0 // Base accuracy roll: hit.
            random.then = 3 // Base damage roll.
            random.then = 0 // Echo accuracy roll: hit.
            random.then = 2 // Echo damage roll.
            player.opNpc2(npc)
            advanceUntil({ hooks.listener.dealt.size >= 2 }, timeoutTicks = 10)

            val rolled = hooks.listener.rolled.single()
            assertTrue(rolled.critical)
            assertFalse(rolled.context.isSpecial)
            val dealt = hooks.listener.dealt.map { it.source to it.damage }
            assertEquals(listOf(HitSource.Base to 12, HitSource.Echo to 2), dealt)
            assertEquals(250 - 14, npc.hitpoints)

            // The pipeline is not left with an open attack scope.
            assertNull(deps.pipeline.currentAttack(player))
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
