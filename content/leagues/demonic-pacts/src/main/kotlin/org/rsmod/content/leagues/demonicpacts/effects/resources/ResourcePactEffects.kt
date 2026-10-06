package org.rsmod.content.leagues.demonicpacts.effects.resources

/**
 * The effect ids (the `effect` column of `dbtable.talent_tree`) of the Regenerate and resource
 * pacts, and the constants their tooltips state but the tree doesn't store. The value each node
 * gives is read from the tree, so a server that overrides a node's value changes the effect too.
 */
object ResourcePactEffects {
    /** AA, BA, BB, BC, F3, H2, J6: this % chance to Regenerate runes, ammo and charges. */
    const val REGENERATE: Int = 1

    /** C1: each air rune a combat spell Regenerates has this % chance to restore 1 Prayer. */
    const val AIR_RUNE_PRAYER: Int = 3

    /** C2: each water rune a combat spell Regenerates heals this much. */
    const val WATER_RUNE_HEAL: Int = 4

    /** C3: each fire rune a combat spell Regenerates adds this much to the next spell hit. */
    const val FIRE_RUNE_DAMAGE: Int = 5

    /** C4: each earth rune a combat spell Regenerates boosts Defence by this much. */
    const val EARTH_RUNE_DEFENCE: Int = 6

    /** G2: special attacks have this % chance to consume no energy. */
    const val FREE_SPECIAL: Int = 24

    /** F5: a powered staff's Regenerated charge also Regenerates this many water runes. */
    const val STAFF_WATER_RUNE: Int = 35

    /** F10: a powered staff's Regenerated charge also Regenerates this many fire runes. */
    const val STAFF_FIRE_RUNE: Int = 36

    /** B1: a combat spell's Regenerate boosts Magic by 1, up to this many levels. */
    const val REGENERATE_MAGIC_BOOST: Int = 78

    /** F6: a powered staff's Regenerated charge also Regenerates this many air runes. */
    const val STAFF_AIR_RUNE: Int = 79

    /** F9: a powered staff's Regenerated charge also Regenerates this many earth runes. */
    const val STAFF_EARTH_RUNE: Int = 80

    /** How long the B1 and C4 boosts last after their latest trigger. */
    const val BOOST_DURATION_TICKS: Int = 30

    /** The B1 boost per trigger. */
    const val MAGIC_BOOST_PER_TRIGGER: Int = 1

    /** C4's boost can't pass this % of the base Defence level. */
    const val DEFENCE_BOOST_CAP_PERCENT: Int = 20

    val ids: Set<Int> =
        setOf(
            REGENERATE,
            AIR_RUNE_PRAYER,
            WATER_RUNE_HEAL,
            FIRE_RUNE_DAMAGE,
            EARTH_RUNE_DEFENCE,
            FREE_SPECIAL,
            STAFF_WATER_RUNE,
            STAFF_FIRE_RUNE,
            REGENERATE_MAGIC_BOOST,
            STAFF_AIR_RUNE,
            STAFF_EARTH_RUNE,
        )
}
