package org.rsmod.content.leagues.demonicpacts.effects.magic

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.annotations.InternalApi
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitType

/**
 * L3's burn: each fire spell hit adds a burn stack to the npc (at most [MAX_STACKS]), and every
 * [INTERVAL] ticks the npc takes 1 typeless damage per stack, [DAMAGE_TICKS] times after the last
 * stack was added.
 *
 * Burns belong to the player who cast them: a soft timer, [TIMER], runs on that player while any
 * of their burns is active, and the burns are runtime state on the player (cleared on logout). A
 * burn ends early when its npc dies or despawns.
 */
@Singleton
class PactBurns
@Inject
constructor(private val npcList: NpcList, private val npcHitModifier: NpcHitModifier) {
    /** The burn stacks [player] has on [npc]. */
    fun stacks(player: Player, npc: Npc): Int = burns(player)?.get(npc.uid.packed)?.stacks ?: 0

    fun apply(player: Player, npc: Npc) {
        val burns = player.attr[BURNS] ?: HashMap<Int, Burn>().also { player.attr[BURNS] = it }
        val burn = burns.getOrPut(npc.uid.packed) { Burn() }
        burn.stacks = (burn.stacks + 1).coerceAtMost(MAX_STACKS)
        burn.ticksLeft = DAMAGE_TICKS
        if (!isRunning(player)) {
            player.softTimer(TIMER, INTERVAL)
        }
    }

    fun onTimer(player: Player) {
        val burns = burns(player)
        if (burns == null) {
            player.clearSoftTimer(TIMER)
            return
        }
        val iterator = burns.entries.iterator()
        while (iterator.hasNext()) {
            val (uid, burn) = iterator.next()
            val npc = NpcUid(uid).resolve(npcList)
            if (npc == null || npc.hitpoints <= 0) {
                iterator.remove()
                continue
            }
            npc.queueHit(player, HIT_DELAY, HitType.Typeless, burn.stacks, npcHitModifier)
            burn.ticksLeft--
            if (burn.ticksLeft <= 0) {
                iterator.remove()
            }
        }
        if (burns.isEmpty()) {
            player.attr.remove(BURNS)
            player.clearSoftTimer(TIMER)
        }
    }

    private fun burns(player: Player): MutableMap<Int, Burn>? = player.attr[BURNS]

    @OptIn(InternalApi::class)
    private fun isRunning(player: Player): Boolean =
        player.softTimerMap[TIMER.asRSCM(RSCMType.TIMER).toShort()] != null

    private class Burn(var stacks: Int = 0, var ticksLeft: Int = 0)

    companion object {
        const val TIMER: String = "timer.demonic_pacts_burn"
        const val MAX_STACKS: Int = 5
        const val INTERVAL: Int = 4
        const val DAMAGE_TICKS: Int = 5

        private const val HIT_DELAY = 1

        private val BURNS: AttributeKey<MutableMap<Int, Burn>> = AttributeKey()
    }
}
