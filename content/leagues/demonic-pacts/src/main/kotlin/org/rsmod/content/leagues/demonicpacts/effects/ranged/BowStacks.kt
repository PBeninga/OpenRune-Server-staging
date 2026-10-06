package org.rsmod.content.leagues.demonicpacts.effects.ranged

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.DamageReceivedEvent
import org.rsmod.api.combat.modifiers.DamageReceivedResponse
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.BOW_MAX_HIT_STACKS
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.BOW_MIN_HIT_STACKS
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.BOW_STACK_CAP_PERCENT
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactEffects
import org.rsmod.game.entity.Player

/**
 * The bow stacks of N4 (min hit) and N5 (max hit). Both nodes read one counter:
 * - every base bow hit that lands on an npc adds a stack, up to 15% of the max hit without N5's
 *   own bonus (so a 40 max hit stops at 6);
 * - every npc hit that still deals damage after protection prayers halves it (rounded down): a hit
 *   the player didn't pray against, couldn't (typeless or prayer-piercing damage), or that got
 *   through.
 *
 * Stacks last until logout or a pact reset.
 */
@Singleton
class BowStacks @Inject constructor(private val pacts: DemonicPacts) : CombatProcListener {
    fun stacks(player: Player): Int = player.attr[STACKS] ?: 0

    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (event.source != HitSource.Base || context.style != CombatStyle.Ranged) {
            return emptyList()
        }
        if (event.rolledDamage <= 0) {
            return emptyList()
        }
        val effects = pacts.effects(context.attacker)
        if (!hasStacks(effects)) {
            return emptyList()
        }
        val weapon = context.weapon ?: return emptyList()
        if (WeaponClass.Bow !in WeaponClasses.of(weapon)) {
            return emptyList()
        }
        val player = context.attacker
        val current = stacks(player)
        val baseMaxHit = event.maxHit - current * effects[BOW_MAX_HIT_STACKS]
        val cap = baseMaxHit * BOW_STACK_CAP_PERCENT / 100
        player.attr[STACKS] = (current + 1).coerceAtMost(cap).coerceAtLeast(0)
        return emptyList()
    }

    override fun onDamageReceived(event: DamageReceivedEvent): DamageReceivedResponse {
        val player = event.context.defender
        val current = stacks(player)
        if (current > 0 && event.incomingDamage > 0) {
            player.attr[STACKS] = current / 2
        }
        return DamageReceivedResponse.NONE
    }

    private fun hasStacks(effects: PactEffects): Boolean =
        BOW_MIN_HIT_STACKS in effects || BOW_MAX_HIT_STACKS in effects

    companion object {
        private val STACKS: AttributeKey<Int> = AttributeKey()

        /** Forgets [player]'s state, as logging out does. */
        internal fun clear(player: Player) {
            player.attr.remove(STACKS)
        }
    }
}
