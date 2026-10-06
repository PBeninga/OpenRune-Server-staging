package org.rsmod.content.leagues.demonicpacts.effects.ranged

/**
 * The effect ids (the `effect` column of `dbtable.talent_tree`) of the ranged and echo pacts, and
 * the numbers their tooltips state for effects whose cache value is only a flag (`1`).
 *
 * Where the cache value is a number (echo chances, thrown accuracy) the effect reads it from
 * [org.rsmod.content.leagues.demonicpacts.state.PactEffects], so a server that overrides a node's
 * value changes the effect too.
 */
object RangedPactEffects {
    /** B2 (25), K9 (5), K10 (5): chance to fire a ranged echo whenever ammo is Regenerated. */
    const val ECHO_ON_REGENERATE: Int = 11

    /** E2: with a crossbow, this much more chance to trigger ranged echoes. */
    const val CROSSBOW_ECHO_CHANCE: Int = 8

    /** E1: with a bow, ranged echoes never miss. */
    const val BOW_ECHOES_NEVER_MISS: Int = 9

    /** E3: with a thrown weapon, ranged echoes have this % chance to max hit. */
    const val THROWN_ECHO_MAX_HIT: Int = 10

    /** K3: echoes can trigger echoes at half the chance, up to [ECHO_CHAIN_CAP] times. */
    const val ECHO_CHAIN: Int = 52

    /** G9: hits with a two-handed melee weapon can trigger a ranged echo. */
    const val TWO_HANDED_MELEE_ECHOES: Int = 71

    /** N9: thrown weapon attacks also hit an additional nearby target. */
    const val THROWN_EXTRA_TARGET: Int = 56

    /** N4: each bow hit raises the bow's min hit by 1 (capped, halved by unprayed damage). */
    const val BOW_MIN_HIT_STACKS: Int = 59

    /** N5: each bow hit raises the bow's max hit by 1 (capped, halved by unprayed damage). */
    const val BOW_MAX_HIT_STACKS: Int = 60

    /** H6: a max hit from 3+ tiles makes the next hit of another style deal more damage. */
    const val STYLE_SWAP: Int = 76

    /** K1: ranged prayers are [RANGED_PRAYER_EFFECT_PERCENT]% more effective. */
    const val RANGED_PRAYER_EFFECT: Int = 58

    /** K2: +1 ranged strength per [HITPOINTS_PER_RANGED_STRENGTH] hitpoints off the level. */
    const val RANGED_STRENGTH_FROM_HITPOINTS: Int = 49

    /** K6: with a thrown weapon, ranged strength also gets 80% of the melee strength bonus. */
    const val THROWN_MELEE_STRENGTH: Int = 17

    /** N8: with a thrown weapon, this much more ranged attack bonus. */
    const val THROWN_ACCURACY: Int = 55

    /** K4: bows attack 1 tick faster. */
    const val BOW_SPEED: Int = 53

    /** K8: crossbows attack 2 ticks slower but deal 70% more damage. */
    const val CROSSBOW_SLOW_HEAVY: Int = 50

    /** N6: crossbow hits always max hit. */
    const val CROSSBOW_MAX_HIT: Int = 51

    /** N7: crossbow attacks roll accuracy twice. */
    const val CROSSBOW_DOUBLE_ACCURACY: Int = 82

    /** H1: a chance per attack, growing with distance, to roll max accuracy. */
    const val MAX_ACCURACY_FROM_RANGE: Int = 57

    val ids: Set<Int> =
        setOf(
            ECHO_ON_REGENERATE,
            CROSSBOW_ECHO_CHANCE,
            BOW_ECHOES_NEVER_MISS,
            THROWN_ECHO_MAX_HIT,
            ECHO_CHAIN,
            TWO_HANDED_MELEE_ECHOES,
            THROWN_EXTRA_TARGET,
            BOW_MIN_HIT_STACKS,
            BOW_MAX_HIT_STACKS,
            STYLE_SWAP,
            RANGED_PRAYER_EFFECT,
            RANGED_STRENGTH_FROM_HITPOINTS,
            THROWN_MELEE_STRENGTH,
            THROWN_ACCURACY,
            BOW_SPEED,
            CROSSBOW_SLOW_HEAVY,
            CROSSBOW_MAX_HIT,
            CROSSBOW_DOUBLE_ACCURACY,
            MAX_ACCURACY_FROM_RANGE,
        )

    const val ECHO_CHAIN_CAP: Int = 4
    const val TWO_HANDED_MELEE_ECHO_PERCENT: Double = 5.0
    const val RANGED_PRAYER_EFFECT_PERCENT: Double = 30.0
    const val HITPOINTS_PER_RANGED_STRENGTH: Int = 10
    const val THROWN_MELEE_STRENGTH_PERCENT: Int = 80
    const val BOW_SPEED_DELTA: Int = -1
    const val CROSSBOW_SLOW_DELTA: Int = 2
    const val CROSSBOW_HEAVY_DAMAGE_PERCENT: Double = 70.0
    const val MAX_ACCURACY_BASE_PERCENT: Double = 5.0
    const val MAX_ACCURACY_PER_TILE_PERCENT: Double = 5.0
    const val STYLE_SWAP_MIN_DISTANCE: Int = 3
    const val STYLE_SWAP_DAMAGE_PERCENT: Double = 25.0
    const val BOW_STACK_CAP_PERCENT: Int = 15
}
