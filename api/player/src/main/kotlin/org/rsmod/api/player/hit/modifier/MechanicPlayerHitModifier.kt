package org.rsmod.api.player.hit.modifier

import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder

/** Applies [delegate] to a hit flagged as mechanic damage ([HitBuilder.isMechanic]). */
public class MechanicPlayerHitModifier(private val delegate: PlayerHitModifier) :
    PlayerHitModifier {
    override fun HitBuilder.modify(target: Player) {
        isMechanic = true
        with(delegate) { modify(target) }
    }
}

/** Returns a modifier that applies this one to hits flagged as mechanic damage. */
public fun PlayerHitModifier.asMechanic(): PlayerHitModifier = MechanicPlayerHitModifier(this)
