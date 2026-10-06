package org.rsmod.content.leagues.demonicpacts.effects.melee

import org.rsmod.api.combat.modifiers.ModifierMath

/**
 * The effect ids (the `effect` column of `dbtable.talent_tree`) of the melee pacts, and the numbers
 * their tooltips state for effects whose cache value is only a flag (`1`).
 *
 * Where the cache value is a number (Blindbag chance, min and max hit per tile, healing chance,
 * special energy per hit, range multiplier) the effect reads it from
 * [org.rsmod.content.leagues.demonicpacts.state.PactEffects], so a server that overrides a node's
 * value changes the effect too.
 */
object MeleePactEffects {
    /** D2: melee weapons under 1kg always hit again with 40% of the max hit. */
    const val LIGHT_WEAPON_DOUBLE_HIT: Int = 43

    /** D3: this % chance per attack with a heavy melee weapon to trigger a Blindbag attack. */
    const val BLINDBAG: Int = 44

    /** J3: +2% Blindbag chance per unique heavy melee weapon in the inventory (5 at most). */
    const val BLINDBAG_CHANCE: Int = 72

    /** M3: +this % Blindbag max hit per unique heavy melee weapon in the inventory (5 at most). */
    const val BLINDBAG_DAMAGE: Int = 68

    /** G4: an attack from at least 2 tiles away, any style, restores 2% special energy. */
    const val DISTANCE_SPECIAL_ENERGY: Int = 62

    /** M2: each melee hit that deals damage restores this % special energy. */
    const val HIT_SPECIAL_ENERGY: Int = 66

    /** G8: a melee attack spends 5 overhealed hitpoints, if there are 5, for +5 min hit. */
    const val OVERHEAL_MIN_HIT: Int = 67

    /** G5, G10: this % chance per melee attack or Thorns to heal 1 per tile to the target. */
    const val DISTANCE_HEALING: Int = 26

    /** G6: melee weapons under 1kg or one-handed add 20% of the Strength level to strength. */
    const val LEVEL_STRENGTH: Int = 77

    /** G7: melee strength gets 50% of the worn prayer bonus. */
    const val PRAYER_STRENGTH: Int = 73

    /** B3: melee min hit +this, and +this again for each tile past the first. */
    const val DISTANCE_MIN_HIT: Int = 25

    /** J4: melee max hit +this %, and +this % again per 3 tiles to the target. */
    const val DISTANCE_MAX_HIT: Int = 70

    /** D4: two-handed melee weapons multiply their attack range by this. */
    const val TWO_HANDED_RANGE: Int = 20

    /** M4: a melee range of 4 or more becomes 7; halberds attack every 5 ticks at most. */
    const val LONG_RANGE: Int = 69

    /** J2: melee weapons under 1kg attack 1 tick faster. */
    const val LIGHT_WEAPON_SPEED: Int = 65

    /** H4: with an off-hand: +5 melee strength, +5 ranged strength and +2% magic damage. */
    const val OFF_HAND_BONUSES: Int = 16

    val ids: Set<Int> =
        setOf(
            LIGHT_WEAPON_DOUBLE_HIT,
            BLINDBAG,
            BLINDBAG_CHANCE,
            BLINDBAG_DAMAGE,
            DISTANCE_SPECIAL_ENERGY,
            HIT_SPECIAL_ENERGY,
            OVERHEAL_MIN_HIT,
            DISTANCE_HEALING,
            LEVEL_STRENGTH,
            PRAYER_STRENGTH,
            DISTANCE_MIN_HIT,
            DISTANCE_MAX_HIT,
            TWO_HANDED_RANGE,
            LONG_RANGE,
            LIGHT_WEAPON_SPEED,
            OFF_HAND_BONUSES,
        )

    const val DOUBLE_HIT_DAMAGE_PERCENT: Double = 40.0
    const val BLINDBAG_CHANCE_PER_WEAPON: Int = 2
    const val BLINDBAG_WEAPON_CAP: Int = 5
    const val BLINDBAG_CHAIN_CAP: Int = ModifierMath.MAX_CHAIN_DEPTH
    const val DISTANCE_SPECIAL_MIN_TILES: Int = 2
    const val DISTANCE_SPECIAL_ENERGY_PERCENT: Int = 2
    const val OVERHEAL_COST: Int = 5
    const val LEVEL_STRENGTH_PERCENT: Int = 20
    const val PRAYER_STRENGTH_PERCENT: Int = 50
    const val DISTANCE_MAX_HIT_TILES: Int = 3
    const val LONG_RANGE_THRESHOLD: Int = 4
    const val LONG_RANGE_TILES: Int = 7
    const val HALBERD_ATTACK_DELAY: Int = 5
    const val LIGHT_WEAPON_SPEED_DELTA: Int = -1
    const val OFF_HAND_MELEE_STRENGTH: Int = 5
    const val OFF_HAND_RANGED_STRENGTH: Int = 5
    const val OFF_HAND_MAGIC_DAMAGE_PERCENT: Double = 2.0

    /** Special energy is stored in tenths of a percent (`sa_energy` 1000 = 100%). */
    const val ENERGY_PER_PERCENT: Int = 10
}
