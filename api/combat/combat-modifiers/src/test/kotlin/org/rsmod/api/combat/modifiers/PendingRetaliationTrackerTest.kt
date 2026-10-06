package org.rsmod.api.combat.modifiers

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.map.CoordGrid

class PendingRetaliationTrackerTest {
    private val player = Player()
    private val attacker = npc()
    private val thorns = listOf(RetaliationHit(HitSource.Thorns, flatDamage = 3))

    @Test
    fun `a landed hit takes the entry with the same attacker, type and damage`() {
        val tracker = PendingRetaliationTracker()
        val first = pending(damage = 4)
        val second = pending(damage = 9)
        tracker.add(player, first)
        tracker.add(player, second)

        assertSame(second, tracker.take(player, attacker, hit(damage = 9), currentCycle = 1))
        assertSame(first, tracker.take(player, attacker, hit(damage = 4), currentCycle = 1))
        assertTrue(tracker.isEmpty)
    }

    @Test
    fun `a landed hit falls back to the oldest entry of the same attacker and type`() {
        val tracker = PendingRetaliationTracker()
        val first = pending(damage = 4)
        tracker.add(player, first)
        tracker.add(player, pending(damage = 9))

        // A wrapping hit modifier changed the damage after the pipeline one ran.
        assertSame(first, tracker.take(player, attacker, hit(damage = 2), currentCycle = 1))
        assertFalse(tracker.isEmpty)
    }

    @Test
    fun `hits from another attacker or of another type do not match`() {
        val tracker = PendingRetaliationTracker()
        tracker.add(player, pending(damage = 4))

        assertNull(tracker.take(player, npc(), hit(damage = 4), currentCycle = 1))
        assertNull(tracker.take(player, attacker, hit(4, HitType.Magic), currentCycle = 1))
        assertNull(tracker.take(Player(), attacker, hit(damage = 4), currentCycle = 1))
    }

    @Test
    fun `entries for hits that never landed expire`() {
        val tracker = PendingRetaliationTracker()
        tracker.add(player, pending(damage = 4, cycle = 0))

        assertNull(tracker.take(player, attacker, hit(damage = 4), currentCycle = 51))
        assertTrue(tracker.isEmpty)
    }

    private fun pending(damage: Int, cycle: Int = 0): PendingRetaliation =
        PendingRetaliation(attacker, HitType.Melee, damage, thorns, cycle)

    private fun hit(damage: Int, type: HitType = HitType.Melee): Hit {
        val hitmark = Hitmark.fromNpcSource(0, 0, null, damage, delay = 0, slotId = 1)
        return Hit(type, hitmark, sourceUid = 1, righthandObj = null, secondaryObj = null)
    }

    private fun npc(): Npc {
        val type = NpcServerType(id = 1, name = "Man", size = 1, hitpoints = 50)
        return Npc(type, CoordGrid(0, 50, 50, 22, 18))
    }
}
