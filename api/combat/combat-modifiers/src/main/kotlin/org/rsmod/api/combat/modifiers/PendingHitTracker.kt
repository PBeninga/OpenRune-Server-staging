package org.rsmod.api.combat.modifiers

import java.util.WeakHashMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.Hit

/**
 * Remembers the [HitSource] of every hit the pipeline queued on an npc, until the hit lands.
 *
 * Hits are matched by identity first. Npc hit processing can replace a hit with a copy (for example
 * when its damage is capped to the npc's remaining hitpoints), so a structural match that ignores
 * the hitmark is used as a fallback. Entries whose hit should have landed long ago (the npc died or
 * its queue was cleared) are dropped.
 */
internal class PendingHitTracker {
    private val pending = WeakHashMap<Npc, ArrayDeque<PendingHit>>()

    fun add(npc: Npc, pendingHit: PendingHit) {
        val queue = pending.getOrPut(npc) { ArrayDeque() }
        if (queue.size >= MAX_PENDING_PER_NPC) {
            queue.removeFirst()
        }
        queue.addLast(pendingHit)
    }

    fun take(npc: Npc, hit: Hit, currentCycle: Int): PendingHit? {
        val queue = pending[npc] ?: return null
        queue.removeAll { it.impactCycle + STALE_GRACE_CYCLES < currentCycle }

        var index = queue.indexOfFirst { it.hit === hit }
        if (index == -1) {
            index = queue.indexOfFirst { it.hit.matches(hit) }
        }

        val match = if (index == -1) null else queue.removeAt(index)
        if (queue.isEmpty()) {
            pending.remove(npc)
        }
        return match
    }

    fun clear() {
        pending.clear()
    }

    private fun Hit.matches(other: Hit): Boolean = copy(hitmark = other.hitmark) == other

    private companion object {
        private const val STALE_GRACE_CYCLES = 10
        private const val MAX_PENDING_PER_NPC = 256
    }
}

/** A hit queued by the pipeline, waiting to land. */
internal class PendingHit(
    val hit: Hit,
    val source: HitSource,
    val style: CombatStyle?,
    val impactCycle: Int,
)
