package org.rsmod.content.leagues.demonicpacts.effects.magic

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.magic.ElementalWeakness
import org.rsmod.api.combat.commons.magic.SpellElement
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.defenceLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.AIR_DAMAGE_PER_PRAYER
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.AIR_MAX_HIT_PER_PRAYER_BONUS
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.EARTH_DAMAGE_PER_DEFENCE
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.FIRE_BURN_DAMAGE_MULTIPLIER
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.FIRE_BURN_HITPOINTS_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.FIRE_HITPOINTS_FOR_DAMAGE
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.ONE_HANDED_STAFF_MAX_HIT
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.POWERED_STAFF_SPEED
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.POWERED_STAFF_SPEED_FLOOR
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.SPELL_SPEED
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.SPELL_SPEED_FLOOR
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.WATER_DAMAGE_HIGH_HITPOINTS
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.WATER_DAMAGE_PERCENT
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactEffects
import org.rsmod.game.entity.Player

/**
 * The stat side of the magic pacts, for a player's attack on an npc or, for their PvP stats, on a
 * player.
 *
 * Elemental spells, by the element they count as ([PactSpellElements]):
 * - **Air:** I1 adds 7% damage per active prayer; L1 gives a 1% chance to max hit per prayer bonus,
 *   doubled against an npc weak to air.
 * - **Water:** I2 adds up to 20% damage, scaled by current over base Hitpoints (capped at 100%).
 * - **Fire:** I3 adds twice the Hitpoints the cast burns ([fireBurn]) to every hit that lands; the
 *   burn itself is taken by [MagicPactProcs], so I3 gives nothing in PvP, where procs don't run.
 * - **Earth:** L4 adds 1 damage per 12 current Defence levels to every hit that lands.
 *
 * "Damage added to a hit" raises both the min and the max hit, so a landed hit rolls in
 * `bonus..max + bonus`.
 *
 * Attack speed: F7 makes spellbook spells 2 ticks faster (not below 2); F8 makes powered staves'
 * built-in spells 3 ticks faster (not below 1), and one-handed powered staves lose 8 max hit.
 */
@Singleton
class MagicPactModifiers
@Inject
constructor(private val pacts: DemonicPacts, private val wornBonuses: WornBonuses) :
    CombatModifierProvider {
    override fun attackModifiers(context: AttackContext): AttackModifiers {
        if (context.style != CombatStyle.Magic) {
            return AttackModifiers.NONE
        }
        val effects = pacts.effects(context.attacker)
        if (effects.isEmpty) {
            return AttackModifiers.NONE
        }
        if (context.spell == null) {
            return poweredStaff(context, effects)
        }
        return spellSpeed(effects) + element(context, effects)
    }

    /** The Hitpoints I3 burns on a fire spell cast by [player]: never enough to kill. */
    fun fireBurn(player: Player): Int {
        val cap = player.baseHitpointsLvl * FIRE_BURN_HITPOINTS_PERCENT / 100
        return minOf(cap, player.hitpoints - 1).coerceAtLeast(0)
    }

    private fun spellSpeed(effects: PactEffects): AttackModifiers {
        val ticks = effects[SPELL_SPEED]
        if (ticks <= 0) {
            return AttackModifiers.NONE
        }
        return AttackModifiers(attackSpeedDelta = -ticks, attackSpeedFloor = SPELL_SPEED_FLOOR)
    }

    private fun poweredStaff(context: AttackContext, effects: PactEffects): AttackModifiers {
        val ticks = effects[POWERED_STAFF_SPEED]
        if (ticks <= 0) {
            return AttackModifiers.NONE
        }
        val classes = context.weapon?.let(WeaponClasses::of) ?: return AttackModifiers.NONE
        if (WeaponClass.PoweredStaff !in classes) {
            return AttackModifiers.NONE
        }
        val maxHit = if (WeaponClass.OneHanded in classes) ONE_HANDED_STAFF_MAX_HIT else 0
        return AttackModifiers(
            attackSpeedDelta = -ticks,
            attackSpeedFloor = POWERED_STAFF_SPEED_FLOOR,
            maxHitFlat = maxHit,
        )
    }

    private fun element(context: AttackContext, effects: PactEffects): AttackModifiers {
        val player = context.attacker
        return when (PactSpellElements.of(context.spell, effects)) {
            SpellElement.Air -> air(context, effects)
            SpellElement.Water -> water(player, effects)
            SpellElement.Fire ->
                if (context.isPvp) AttackModifiers.NONE else addedDamage(fire(player, effects))
            SpellElement.Earth -> addedDamage(earth(player, effects))
            else -> AttackModifiers.NONE
        }
    }

    private fun air(context: AttackContext, effects: PactEffects): AttackModifiers {
        val player = context.attacker
        val damagePercent = effects[AIR_DAMAGE_PER_PRAYER] * activePrayers(player)
        var maxHitChance = 0.0
        val perPrayerBonus = effects[AIR_MAX_HIT_PER_PRAYER_BONUS]
        if (perPrayerBonus > 0) {
            val prayerBonus = wornBonuses.prayerBonus(player).coerceAtLeast(0)
            val npc = context.targetNpc
            val weak = npc != null && ElementalWeakness.isWeakTo(npc.type, SpellElement.Air)
            val multiplier = if (weak) WEAKNESS_MULTIPLIER else 1
            maxHitChance = (perPrayerBonus * prayerBonus * multiplier).toDouble()
        }
        if (damagePercent == 0 && maxHitChance == 0.0) {
            return AttackModifiers.NONE
        }
        return AttackModifiers(
            maxHitPercent = damagePercent.toDouble(),
            maxHitChancePercent = maxHitChance,
        )
    }

    private fun water(player: Player, effects: PactEffects): AttackModifiers {
        if (WATER_DAMAGE_HIGH_HITPOINTS !in effects) {
            return AttackModifiers.NONE
        }
        val base = player.baseHitpointsLvl
        if (base <= 0) {
            return AttackModifiers.NONE
        }
        val fraction = minOf(player.hitpoints, base).toDouble() / base
        return AttackModifiers(maxHitPercent = WATER_DAMAGE_PERCENT * fraction)
    }

    private fun fire(player: Player, effects: PactEffects): Int {
        if (FIRE_HITPOINTS_FOR_DAMAGE !in effects) {
            return 0
        }
        return fireBurn(player) * FIRE_BURN_DAMAGE_MULTIPLIER
    }

    private fun earth(player: Player, effects: PactEffects): Int {
        val levelsPerDamage = effects[EARTH_DAMAGE_PER_DEFENCE]
        if (levelsPerDamage <= 0) {
            return 0
        }
        return player.defenceLvl / levelsPerDamage
    }

    private fun addedDamage(damage: Int): AttackModifiers {
        if (damage <= 0) {
            return AttackModifiers.NONE
        }
        return AttackModifiers(maxHitFlat = damage, minHitFlat = damage)
    }

    private fun activePrayers(player: Player): Int =
        Integer.bitCount(player.vars[ACTIVE_PRAYERS])

    private companion object {
        const val ACTIVE_PRAYERS = "varbit.prayer_allactive"
        const val WEAKNESS_MULTIPLIER = 2
    }
}
