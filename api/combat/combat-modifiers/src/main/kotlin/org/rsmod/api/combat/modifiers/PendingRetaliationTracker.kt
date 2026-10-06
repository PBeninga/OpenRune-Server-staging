package org.rsmod.api.combat.modifiers

import java.util.WeakHashMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType

/**
 * Remembers the retaliation owed for npc hits on a player, from when the hit is modified until it
 * lands.
 *
 * A landed hit is matched to the oldest entry with the same attacker, type and damage, falling back
 * to the same attacker and type (a hit modifier wrapping this one may change the damage
 * after it). Entries for hits that never land (the queue was cleared, the hit was invalidated) are
 * dropped once they are older than any hit delay.
 */
internal class PendingRetaliationTracker {
    private val pending = WeakHashMap<Player, ArrayDeque<PendingRetaliation>>()

    val isEmpty: Boolean
        get() = pending.isEmpty()

    fun add(player: Player, retaliation: PendingRetaliation) {
        val queue = pending.getOrPut(player) { ArrayDeque() }
        if (queue.size >= MAX_PENDING_PER_PLAYER) {
            queue.removeFirst()
        }
        queue.addLast(retaliation)
    }

    fun take(player: Player, attacker: Npc, hit: Hit, currentCycle: Int): PendingRetaliation? {
        val queue = pending[player] ?: return null
        queue.removeAll { it.modifiedCycle + STALE_CYCLES < currentCycle }

        var index = queue.indexOfFirst { it.matches(attacker, hit) && it.damage == hit.damage }
        if (index == -1) {
            index = queue.indexOfFirst { it.matches(attacker, hit) }
        }

        val match = if (index == -1) null else queue.removeAt(index)
        if (queue.isEmpty()) {
            pending.remove(player)
        }
        return match
    }

    private fun PendingRetaliation.matches(attacker: Npc, hit: Hit): Boolean =
        this.attacker === attacker && type == hit.type

    private companion object {
        private const val STALE_CYCLES = 50
        private const val MAX_PENDING_PER_PLAYER = 64
    }
}

/** Retaliation owed for an npc's hit on a player, waiting for the hit to land. */
internal class PendingRetaliation(
    val attacker: Npc,
    val type: HitType,
    val damage: Int,
    val retaliations: List<RetaliationHit>,
    val modifiedCycle: Int,
)
