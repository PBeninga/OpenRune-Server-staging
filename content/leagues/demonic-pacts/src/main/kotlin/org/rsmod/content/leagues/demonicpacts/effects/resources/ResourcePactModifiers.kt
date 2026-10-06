package org.rsmod.content.leagues.demonicpacts.effects.resources

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.modifiers.ResourceContext
import org.rsmod.api.combat.modifiers.ResourceModifiers
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.FIRE_RUNE_DAMAGE
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.REGENERATE
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.entity.Player

/**
 * The stat side of the Regenerate and resource pacts:
 * - **Regenerate** (AA, BA–BC, F3, H2, J6): the summed chance that runes, ammo or charges about to
 *   be spent are Regenerated instead. The pipeline rolls it once per rune stack, shot or charge use
 *   and caps it at 100%. It works for every spell cast with runes, teleports included.
 * - **C3:** the fire damage stored by [addFireDamage] is added to the next magic hit that lands
 *   (it raises both the min and the max hit); [ResourcePactProcs] clears it once that hit is
 *   rolled. That proc doesn't run in PvP, so neither does C3.
 */
@Singleton
class ResourcePactModifiers @Inject constructor(private val pacts: DemonicPacts) :
    CombatModifierProvider {
    override fun resourceModifiers(context: ResourceContext): ResourceModifiers {
        val chance = pacts.effects(context.player)[REGENERATE]
        if (chance <= 0) {
            return ResourceModifiers.NONE
        }
        return ResourceModifiers(regenerateChancePercent = chance.toDouble())
    }

    override fun attackModifiers(context: AttackContext): AttackModifiers {
        if (context.style != CombatStyle.Magic || context.source != HitSource.Base) {
            return AttackModifiers.NONE
        }
        if (context.isPvp) {
            return AttackModifiers.NONE
        }
        val damage = fireDamage(context.attacker)
        if (damage <= 0 || FIRE_RUNE_DAMAGE !in pacts.effects(context.attacker)) {
            return AttackModifiers.NONE
        }
        return AttackModifiers(maxHitFlat = damage, minHitFlat = damage)
    }

    /** The C3 damage [player]'s next magic hit gets. */
    fun fireDamage(player: Player): Int = player.attr[FIRE_DAMAGE] ?: 0

    fun addFireDamage(player: Player, damage: Int) {
        if (damage > 0) {
            player.attr[FIRE_DAMAGE] = fireDamage(player) + damage
        }
    }

    fun clearFireDamage(player: Player) {
        player.attr.remove(FIRE_DAMAGE)
    }

    private companion object {
        val FIRE_DAMAGE: AttributeKey<Int> = AttributeKey(temp = true)
    }
}
