package org.rsmod.api.combat.modifiers

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.asMechanic
import org.rsmod.api.player.hit.modify
import org.rsmod.api.random.GameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/**
 * The NvP damage-received half of [CombatModifierPipeline] that [CombatModifierPlayerHitModifier] runs after
 * the standard player hit modifier. The decorator wiring itself and retaliation (which queues real
 * npc hits) are covered by the integration tests.
 */
class DamageReceivedPipelineTest {
    private val npcList = NpcList()
    private val npc = spawnNpc(slot = 5)
    private val player = Player()

    @Test
    fun `nothing registered leaves npc hits untouched and consumes no randomness`() {
        val pipeline = pipeline(CombatModifierRegistry.EMPTY, random = NoRolls)
        val builder = npcHit(damage = 10)

        pipeline.receiveHit(builder, player, rolledDamage = 10)

        assertEquals(10, builder.damage)
        assertFalse(pipeline.isActive)
    }

    @Test
    fun `provider and listener damage reduction are summed`() {
        val hooks = Hooks()
        hooks.provider.defence = DefenceModifiers(damageReductionPercent = 30.0)
        hooks.listener.response = { DamageReceivedResponse(reductionPercent = 20.0) }
        val pipeline = pipeline(hooks.registry, random = NoRolls)
        val builder = npcHit(damage = 10)

        pipeline.receiveHit(builder, player, rolledDamage = 12)

        assertEquals(5, builder.damage)
        val event = hooks.listener.received.single()
        assertEquals(12, event.rolledDamage)
        assertEquals(10, event.incomingDamage)
        assertTrue(event.context.isMitigable)
        assertSame(npc, event.context.attacker)
        assertSame(player, event.context.defender)
    }

    @Test
    fun `damage reduction rounds down`() {
        val hooks = Hooks()
        hooks.provider.defence = DefenceModifiers(damageReductionPercent = 8.0)
        val pipeline = pipeline(hooks.registry, random = NoRolls)
        val builder = npcHit(damage = 37)

        pipeline.receiveHit(builder, player, rolledDamage = 37)

        // 37 × 0.92 = 34.04.
        assertEquals(34, builder.damage)
    }

    @Test
    fun `a listener dodge takes no damage`() {
        val hooks = Hooks()
        hooks.listener.response = { DamageReceivedResponse(dodge = true) }
        val pipeline = pipeline(hooks.registry, random = NoRolls)
        val builder = npcHit(damage = 25)

        pipeline.receiveHit(builder, player, rolledDamage = 25)

        assertEquals(0, builder.damage)
    }

    @Test
    fun `dodge chance is rolled in basis points with the game random`() {
        val hooks = Hooks()
        hooks.provider.defence = DefenceModifiers(dodgeChancePercent = 5.0)
        val rolls = ScriptedRolls(499, 500)
        val pipeline = pipeline(hooks.registry, rolls)

        val dodged = npcHit(damage = 10)
        pipeline.receiveHit(dodged, player, rolledDamage = 10)
        val landed = npcHit(damage = 7)
        pipeline.receiveHit(landed, player, rolledDamage = 7)

        assertEquals(0, dodged.damage)
        assertEquals(7, landed.damage)
        assertEquals(listOf(ModifierMath.CHANCE_SCALE, ModifierMath.CHANCE_SCALE), rolls.bounds)
    }

    @Test
    fun `typeless and mechanic hits bypass dodge and damage reduction`() {
        val hooks = Hooks()
        hooks.provider.defence =
            DefenceModifiers(damageReductionPercent = 50.0, dodgeChancePercent = 100.0)
        hooks.listener.response = { DamageReceivedResponse(dodge = true, reductionPercent = 50.0) }
        val pipeline = pipeline(hooks.registry, random = NoRolls)

        val mechanic = npcHit(damage = 10)
        mechanic.isMechanic = true
        pipeline.receiveHit(mechanic, player, rolledDamage = 10)
        val typeless = npcHit(damage = 6, type = HitType.Typeless)
        pipeline.receiveHit(typeless, player, rolledDamage = 6)

        assertEquals(10, mechanic.damage)
        assertEquals(6, typeless.damage)
        val contexts = hooks.listener.received.map { it.context }
        assertEquals(listOf(true, false), contexts.map { it.isMechanic })
        assertTrue(contexts.none { it.isMitigable })
        assertTrue(hooks.provider.defenceContexts.isEmpty())
    }

    @Test
    fun `asMechanic flags the hit before the wrapped modifier runs`() {
        var seenMechanic: Boolean? = null
        val inner = PlayerHitModifier { seenMechanic = isMechanic }
        val builder = npcHit(damage = 3)

        inner.asMechanic().modify(builder, player)

        assertEquals(true, seenMechanic)
        assertTrue(builder.isMechanic)
    }

    @Test
    fun `hits that do not come from an npc are ignored`() {
        val hooks = Hooks()
        hooks.provider.defence = DefenceModifiers(damageReductionPercent = 50.0)
        val pipeline = pipeline(hooks.registry, random = NoRolls)
        val unsourced = hitBuilder(damage = 10, sourceUid = null, fromNpc = false)

        pipeline.receiveHit(unsourced, player, rolledDamage = 10)

        assertEquals(10, unsourced.damage)
        assertTrue(hooks.listener.received.isEmpty())
    }

    @Test
    fun `a hit from an npc that is gone still applies, without an attacker`() {
        val hooks = Hooks()
        hooks.provider.defence = DefenceModifiers(damageReductionPercent = 50.0)
        val pipeline = pipeline(hooks.registry, random = NoRolls)
        val gone = NpcUid(slot = 9, type = 1)
        val builder = hitBuilder(damage = 10, sourceUid = gone.packed, fromNpc = true)

        pipeline.receiveHit(builder, player, rolledDamage = 10)

        assertEquals(5, builder.damage)
        assertNull(hooks.listener.received.single().context.attacker)
    }

    private fun pipeline(registry: CombatModifierRegistry, random: GameRandom) =
        CombatModifierPipeline(
            registry = registry,
            random = random,
            mapClock = MapClock(),
            playerList = PlayerList(),
            npcList = npcList,
            npcHitModifier = NpcHitModifier {},
        )

    @OptIn(InternalApi::class)
    private fun spawnNpc(slot: Int): Npc {
        val type = NpcServerType(id = 1, name = "Man", size = 1, hitpoints = 50)
        val npc = Npc(type, CoordGrid(0, 50, 50, 22, 18))
        npc.slotId = slot
        npc.assignUid()
        npcList[slot] = npc
        return npc
    }

    private fun npcHit(damage: Int, type: HitType = HitType.Melee): HitBuilder =
        hitBuilder(damage, sourceUid = npc.uid.packed, fromNpc = true, type = type)

    private fun hitBuilder(
        damage: Int,
        sourceUid: Int?,
        fromNpc: Boolean,
        type: HitType = HitType.Melee,
    ): HitBuilder =
        HitBuilder(
            type = type,
            damage = damage,
            sourceUid = sourceUid,
            sourceSlot = if (fromNpc) npc.slotId else null,
            isFromNpc = fromNpc,
            isFromPlayer = false,
            clientDelay = 0,
            righthandType = null,
            secondaryType = null,
            targetHitmark = 0,
            sourceHitmark = 0,
            publicHitmark = null,
            zeroDamageHitmarkLit = null,
            zeroDamageHitmarkTint = null,
            maxDamageHitmarkLit = null,
            targetMaxDamageThreshold = Int.MAX_VALUE,
            sourceMaxDamageThreshold = Int.MAX_VALUE,
        )

    private class Hooks {
        val provider = RecordingProvider()
        val listener = RecordingListener()
        val registry = CombatModifierRegistry.of(listOf(provider), listOf(listener))
    }

    private class RecordingProvider : CombatModifierProvider {
        var defence: DefenceModifiers = DefenceModifiers.NONE
        val defenceContexts = mutableListOf<DefenceContext>()

        override fun defenceModifiers(context: DefenceContext): DefenceModifiers {
            defenceContexts += context
            return defence
        }
    }

    private class RecordingListener : CombatProcListener {
        var response: (DamageReceivedEvent) -> DamageReceivedResponse = {
            DamageReceivedResponse.NONE
        }
        val received = mutableListOf<DamageReceivedEvent>()

        override fun onDamageReceived(event: DamageReceivedEvent): DamageReceivedResponse {
            received += event
            return response(event)
        }
    }

    /** Fails the test if any random value is drawn. */
    private object NoRolls : GameRandom {
        override fun of(maxExclusive: Int): Int = error("Unexpected random roll.")

        override fun of(minInclusive: Int, maxInclusive: Int): Int =
            error("Unexpected random roll.")

        override fun randomDouble(): Double = error("Unexpected random roll.")
    }

    /** Returns [values] in order and records the bound of each `of(maxExclusive)` call. */
    private class ScriptedRolls(vararg values: Int) : GameRandom {
        val bounds = mutableListOf<Int>()
        private val values = ArrayDeque(values.toList())

        override fun of(maxExclusive: Int): Int {
            bounds += maxExclusive
            return values.removeFirst()
        }

        override fun of(minInclusive: Int, maxInclusive: Int): Int = values.removeFirst()

        override fun randomDouble(): Double = error("Unexpected random roll.")
    }
}
