package org.rsmod.content.leagues.demonicpacts.effects.ranged

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.STYLE_SWAP
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.STYLE_SWAP_MIN_DISTANCE
import org.rsmod.content.leagues.demonicpacts.effects.tilesTo
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.entity.Player

/**
 * H6: a base hit that rolls the max hit from at least 3 tiles away primes the style it was dealt
 * with; the next base hit of any other style deals +25% damage ([boostedAgainst], a max-hit bonus)
 * and uses it up. Hits of the primed style leave it primed. It lasts until used, logout or a pact
 * reset.
 */
@Singleton
class StyleSwap @Inject constructor(private val pacts: DemonicPacts) : CombatProcListener {
    /** `true` if [player]'s next hit with [style] gets H6's damage bonus. */
    fun boostedAgainst(player: Player, style: CombatStyle): Boolean {
        val primed = player.attr[PRIMED] ?: return false
        return primed != style
    }

    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        if (event.source != HitSource.Base) {
            return emptyList()
        }
        val context = event.context
        val player = context.attacker
        if (boostedAgainst(player, context.style)) {
            player.attr.remove(PRIMED)
        }
        if (STYLE_SWAP !in pacts.effects(player)) {
            return emptyList()
        }
        val maxHit = event.maxHit > 0 && event.rolledDamage >= event.maxHit
        if (maxHit && player.tilesTo(context.target) >= STYLE_SWAP_MIN_DISTANCE) {
            player.attr[PRIMED] = context.style
        }
        return emptyList()
    }

    companion object {
        private val PRIMED: AttributeKey<CombatStyle> = AttributeKey()

        /** Forgets [player]'s state, as logging out does. */
        internal fun clear(player: Player) {
            player.attr.remove(PRIMED)
        }
    }
}
