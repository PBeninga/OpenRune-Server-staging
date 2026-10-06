package org.rsmod.content.leagues.demonicpacts.effects.melee

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.stat.PlayerHealing
import org.rsmod.api.player.stat.strengthLvl
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.BLINDBAG
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.BLINDBAG_CHAIN_CAP
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.BLINDBAG_CHANCE
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.BLINDBAG_CHANCE_PER_WEAPON
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.BLINDBAG_DAMAGE
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.BLINDBAG_WEAPON_CAP
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.DISTANCE_MAX_HIT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.DISTANCE_MAX_HIT_TILES
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.DISTANCE_MIN_HIT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.HALBERD_ATTACK_DELAY
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.LEVEL_STRENGTH
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.LEVEL_STRENGTH_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.LIGHT_WEAPON_SPEED
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.LIGHT_WEAPON_SPEED_DELTA
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.LONG_RANGE
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.LONG_RANGE_THRESHOLD
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.LONG_RANGE_TILES
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.OFF_HAND_BONUSES
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.OFF_HAND_MAGIC_DAMAGE_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.OFF_HAND_MELEE_STRENGTH
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.OFF_HAND_RANGED_STRENGTH
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.OVERHEAL_COST
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.OVERHEAL_MIN_HIT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.PRAYER_STRENGTH
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.PRAYER_STRENGTH_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.TWO_HANDED_RANGE
import org.rsmod.content.leagues.demonicpacts.effects.tilesTo
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactEffects
import org.rsmod.game.entity.Player

/**
 * The stat side of the melee pacts, for a player's attack on an npc:
 * - **Any style:** H4, with an off-hand worn, gives +5 melee strength, +5 ranged strength or +2%
 *   magic damage to the attack's style.
 * - **Distance** (to the nearest tile of the target, 1 when adjacent): B3 raises the min hit by
 *   its value per tile; J4 raises the max hit by its value % plus its value % per 3 tiles.
 * - **G8:** +5 min hit while the player has 5 overhealed hitpoints ([OverhealStrike] spends them).
 * - **Strength:** G6 adds 20% of the Strength level with a weapon under 1kg or one-handed; G7 adds
 *   50% of the worn prayer bonus.
 * - **Speed and range:** J2 attacks 1 tick faster with a weapon under 1kg; D4 multiplies a
 *   two-handed weapon's range by its value (2); M4 then makes a range of 4 or more 7, and makes
 *   halberds attack every 5 ticks at most.
 * - **Blindbag** ([Blindbag]): D3's chance with a heavy melee weapon, plus J3's 2% per unique heavy
 *   melee weapon in the inventory (5 at most). While a Blindbag attack rolls, the worn weapon's
 *   attack bonus (for the attack's type) and strength bonus are swapped for the bag weapon's (its
 *   best of stab, slash and crush), and M3 adds 2% max hit per unique heavy weapon (5 at most).
 *
 * The other weapon checks of a Blindbag attack (G6, J2) look at the bag weapon.
 */
@Singleton
class MeleePactModifiers
@Inject
constructor(
    private val pacts: DemonicPacts,
    private val wornBonuses: WornBonuses,
    private val healing: PlayerHealing,
    private val attackTypes: AttackTypes,
    private val blindbag: Blindbag,
) : CombatModifierProvider {
    override fun attackModifiers(context: AttackContext): AttackModifiers {
        val effects = pacts.effects(context.attacker)
        if (effects.isEmpty) {
            return AttackModifiers.NONE
        }
        var modifiers = offHand(context, effects)
        if (context.style != CombatStyle.Melee) {
            return modifiers
        }
        val weapon = weapon(context)
        val classes = weapon?.let(WeaponClasses::of) ?: emptySet()
        modifiers += distanceMaxHit(context, effects)
        modifiers += minHit(context, effects)
        modifiers += strength(context.attacker, effects, classes)
        if (context.source == HitSource.Base) {
            modifiers += speedAndRange(weapon, effects, classes)
        }
        modifiers += blindbagChance(context, effects, classes)
        if (context.source == HitSource.Blindbag) {
            modifiers += blindbagDamage(context, effects)
            if (weapon != null) {
                modifiers += blindbagWeapon(context, weapon)
            }
        }
        return modifiers
    }

    private fun weapon(context: AttackContext): ItemServerType? =
        if (context.source == HitSource.Blindbag) {
            blindbag.weapon(context.attacker)
        } else {
            context.weapon
        }

    private fun offHand(context: AttackContext, effects: PactEffects): AttackModifiers {
        if (OFF_HAND_BONUSES !in effects) {
            return AttackModifiers.NONE
        }
        if (WeaponClass.OffHand !in WeaponClasses.worn(context.attacker)) {
            return AttackModifiers.NONE
        }
        return when (context.style) {
            CombatStyle.Melee -> AttackModifiers(meleeStrengthFlat = OFF_HAND_MELEE_STRENGTH)
            CombatStyle.Ranged -> AttackModifiers(rangedStrengthFlat = OFF_HAND_RANGED_STRENGTH)
            CombatStyle.Magic -> AttackModifiers(magicDamagePercent = OFF_HAND_MAGIC_DAMAGE_PERCENT)
        }
    }

    private fun distanceMaxHit(context: AttackContext, effects: PactEffects): AttackModifiers {
        val percent = effects[DISTANCE_MAX_HIT]
        if (percent == 0) {
            return AttackModifiers.NONE
        }
        val tiles = context.attacker.tilesTo(context.target)
        val steps = 1 + tiles / DISTANCE_MAX_HIT_TILES
        return AttackModifiers(maxHitPercent = (percent * steps).toDouble())
    }

    /**
     * B3's min hit per tile and G8's +5 (for the attack's own hits, while 5 hitpoints are
     * overhealed), added to the engine's min hit of 1.
     */
    private fun minHit(context: AttackContext, effects: PactEffects): AttackModifiers {
        val player = context.attacker
        var increase = effects[DISTANCE_MIN_HIT] * player.tilesTo(context.target).coerceAtLeast(1)
        if (OVERHEAL_MIN_HIT in effects && context.source == HitSource.Base) {
            if (healing.overhealed(player) >= OVERHEAL_COST) {
                increase += OVERHEAL_COST
            }
        }
        if (increase <= 0) {
            return AttackModifiers.NONE
        }
        return AttackModifiers(minHitFlat = 1 + increase)
    }

    private fun strength(
        player: Player,
        effects: PactEffects,
        classes: Set<WeaponClass>,
    ): AttackModifiers {
        var strength = 0
        if (LEVEL_STRENGTH in effects && WeaponClass.Melee in classes) {
            if (WeaponClass.Light in classes || WeaponClass.OneHanded in classes) {
                strength += player.strengthLvl * LEVEL_STRENGTH_PERCENT / 100
            }
        }
        if (PRAYER_STRENGTH in effects) {
            val prayer = wornBonuses.prayerBonus(player).coerceAtLeast(0)
            strength += prayer * PRAYER_STRENGTH_PERCENT / 100
        }
        if (strength == 0) {
            return AttackModifiers.NONE
        }
        return AttackModifiers(meleeStrengthFlat = strength)
    }

    private fun speedAndRange(
        weapon: ItemServerType?,
        effects: PactEffects,
        classes: Set<WeaponClass>,
    ): AttackModifiers {
        if (weapon == null || WeaponClass.Melee !in classes) {
            return AttackModifiers.NONE
        }
        var speed = 0
        if (LIGHT_WEAPON_SPEED in effects && WeaponClass.Light in classes) {
            speed += LIGHT_WEAPON_SPEED_DELTA
        }
        val attackRate = weapon.param(params.attackrate)
        if (LONG_RANGE in effects && WeaponClass.Halberd in classes) {
            if (attackRate > HALBERD_ATTACK_DELAY) {
                speed += HALBERD_ATTACK_DELAY - attackRate
            }
        }
        val baseRange = weapon.param(params.attackrange)
        var range = baseRange
        if (TWO_HANDED_RANGE in effects && WeaponClass.TwoHanded in classes) {
            range *= effects[TWO_HANDED_RANGE]
        }
        if (LONG_RANGE in effects && range >= LONG_RANGE_THRESHOLD) {
            range = LONG_RANGE_TILES
        }
        return AttackModifiers(attackSpeedDelta = speed, attackRangeDelta = range - baseRange)
    }

    private fun blindbagChance(
        context: AttackContext,
        effects: PactEffects,
        classes: Set<WeaponClass>,
    ): AttackModifiers {
        if (BLINDBAG !in effects || context.isSpecial) {
            return AttackModifiers.NONE
        }
        if (WeaponClass.Melee !in classes || WeaponClass.Heavy !in classes) {
            return AttackModifiers.NONE
        }
        val weapons = Blindbag.heavyMeleeWeapons(context.attacker).size
        if (weapons == 0) {
            return AttackModifiers.NONE
        }
        val bonus =
            if (BLINDBAG_CHANCE in effects) {
                weapons.coerceAtMost(BLINDBAG_WEAPON_CAP) * BLINDBAG_CHANCE_PER_WEAPON
            } else {
                0
            }
        return AttackModifiers(
            procChancePercent = mapOf(HitSource.Blindbag to (effects[BLINDBAG] + bonus).toDouble()),
            chainCaps = mapOf(HitSource.Blindbag to BLINDBAG_CHAIN_CAP),
        )
    }

    private fun blindbagDamage(context: AttackContext, effects: PactEffects): AttackModifiers {
        val percentPerWeapon = effects[BLINDBAG_DAMAGE]
        if (percentPerWeapon == 0) {
            return AttackModifiers.NONE
        }
        val weapons = Blindbag.heavyMeleeWeapons(context.attacker).size
        val percent = percentPerWeapon * weapons.coerceAtMost(BLINDBAG_WEAPON_CAP)
        return AttackModifiers(maxHitPercent = percent.toDouble())
    }

    private fun blindbagWeapon(context: AttackContext, bagWeapon: ItemServerType): AttackModifiers {
        val worn = context.weapon
        val type = MeleeAttackType.from(attackTypes.get(context.attacker))
        val wornAttack = worn?.let { attackBonus(it, type) } ?: 0
        val wornStrength = worn?.param(params.melee_strength) ?: 0
        return AttackModifiers(
            attackBonusFlat = bestAttackBonus(bagWeapon) - wornAttack,
            meleeStrengthFlat = bagWeapon.param(params.melee_strength) - wornStrength,
        )
    }

    private fun attackBonus(weapon: ItemServerType, type: MeleeAttackType?): Int =
        when (type) {
            MeleeAttackType.Stab -> weapon.param(params.attack_stab)
            MeleeAttackType.Slash -> weapon.param(params.attack_slash)
            MeleeAttackType.Crush -> weapon.param(params.attack_crush)
            null -> 0
        }

    private fun bestAttackBonus(weapon: ItemServerType): Int =
        maxOf(
            weapon.param(params.attack_stab),
            weapon.param(params.attack_slash),
            weapon.param(params.attack_crush),
        )
}
