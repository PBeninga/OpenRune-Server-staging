package org.rsmod.content.leagues.demonicpacts.effects.stats

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.ALL_STYLE_ACCURACY
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.MAGIC_DAMAGE
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.MELEE_DAMAGE
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.PENETRATION_CAP
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.PRAYER_PENETRATION
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.RANGED_DAMAGE
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.STYLE_DAMAGE_ACCURACY_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.styleDamage
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactEffects

/**
 * The flat stat pacts, for a player's attack on an npc:
 * - **Accuracy:** the accuracy nodes' total, plus 10% for every style damage node owned, in every
 *   style.
 * - **Style damage:** the melee and ranged nodes add to the max hit of their style. The magic nodes
 *   add to the magic damage bonus, which Tumeken's shadow multiplies like the worn one.
 * - **Prayer penetration:** the nodes' total, up to 100%, in every style.
 *
 * The Defence nodes are a stat boost ([PactDefenceBoost]).
 */
@Singleton
class StatPactModifiers @Inject constructor(private val pacts: DemonicPacts) :
    CombatModifierProvider {
    override fun attackModifiers(context: AttackContext): AttackModifiers {
        val effects = pacts.effects(context.attacker)
        if (effects.isEmpty) {
            return AttackModifiers.NONE
        }
        val accuracy = effects[ALL_STYLE_ACCURACY] + styleDamageAccuracy(effects)
        val penetration = effects[PRAYER_PENETRATION].coerceAtMost(PENETRATION_CAP)
        val maxHit =
            when (context.style) {
                CombatStyle.Melee -> effects[MELEE_DAMAGE]
                CombatStyle.Ranged -> effects[RANGED_DAMAGE]
                CombatStyle.Magic -> 0
            }
        val magicDamage = if (context.style == CombatStyle.Magic) effects[MAGIC_DAMAGE] else 0
        if (accuracy == 0 && penetration == 0 && maxHit == 0 && magicDamage == 0) {
            return AttackModifiers.NONE
        }
        return AttackModifiers(
            accuracyPercent = accuracy.toDouble(),
            maxHitPercent = maxHit.toDouble(),
            magicDamagePercent = magicDamage.toDouble(),
            prayerPenetrationPercent = penetration.toDouble(),
        )
    }

    private fun styleDamageAccuracy(effects: PactEffects): Int =
        STYLE_DAMAGE_ACCURACY_PERCENT * styleDamage.sumOf(effects::nodeCount)
}
