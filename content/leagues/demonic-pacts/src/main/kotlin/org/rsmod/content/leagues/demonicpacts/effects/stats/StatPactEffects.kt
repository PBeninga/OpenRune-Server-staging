package org.rsmod.content.leagues.demonicpacts.effects.stats

/**
 * The effect ids (the `effect` column of `dbtable.talent_tree`) of the flat stat pacts, and the
 * numbers their tooltips state that the cache doesn't hold.
 *
 * Every value the cache holds (accuracy, damage, penetration and Defence levels) is read from
 * [org.rsmod.content.leagues.demonicpacts.state.PactEffects], so a server that overrides a node's
 * value changes the effect too.
 */
object StatPactEffects {
    /** FA–FD, IA–ID: +1% magic damage (Tumeken's shadow multiplies it) and +10% accuracy. */
    const val MAGIC_DAMAGE: Int = 7

    /** CA–CC, DB, DC, EB (25), F1, G3, H3 (50): +% accuracy in all combat styles. */
    const val ALL_STYLE_ACCURACY: Int = 12

    /** DA, EA, EC, F11, F12, G11, G12, H5, H8 (5), F4 (15): permanent Defence level boost. */
    const val DEFENCE_BOOST: Int = 13

    /** GA–GD, JA–JD: +1% melee damage and +10% accuracy. */
    const val MELEE_DAMAGE: Int = 14

    /** HA–HD, KA–KC, N1–N3: +1% ranged damage and +10% accuracy. */
    const val RANGED_DAMAGE: Int = 15

    /** H7, H10, I5, I7, J5, J7, J9, K5, K7: +25% prayer penetration, up to [PENETRATION_CAP]. */
    const val PRAYER_PENETRATION: Int = 40

    val ids: Set<Int> =
        setOf(
            MAGIC_DAMAGE,
            ALL_STYLE_ACCURACY,
            DEFENCE_BOOST,
            MELEE_DAMAGE,
            RANGED_DAMAGE,
            PRAYER_PENETRATION,
        )

    /** The style damage effects, whose every node also gives [STYLE_DAMAGE_ACCURACY_PERCENT]. */
    val styleDamage: Set<Int> = setOf(MAGIC_DAMAGE, MELEE_DAMAGE, RANGED_DAMAGE)

    const val STYLE_DAMAGE_ACCURACY_PERCENT: Int = 10
    const val PENETRATION_CAP: Int = 100
    const val DEFENCE_STAT: String = "stat.defence"
}
