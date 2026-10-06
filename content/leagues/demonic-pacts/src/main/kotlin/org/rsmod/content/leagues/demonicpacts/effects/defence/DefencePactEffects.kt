package org.rsmod.content.leagues.demonicpacts.effects.defence

/**
 * The effect ids (the `effect` column of `dbtable.talent_tree`) of the defence, overheal and prayer
 * pacts. The value each node gives is read from the tree, so a server that overrides a node's value
 * changes the effect too.
 */
object DefencePactEffects {
    /** F2, F13, H9: pact healing overheals up to this % of base Hitpoints. Doesn't stack. */
    const val OVERHEAL: Int = 18

    /** D1: with a shield equipped, every hitsplat from an npc hits it back for this much. */
    const val THORNS: Int = 19

    /** G1: with a shield equipped, a 0.1% chance per Defence level to reflect a hit's damage. */
    const val SHIELD_REFLECT: Int = 23

    /** I6: restores prayer while no protection prayer is active. */
    const val PRAYER_RESTORE: Int = 61

    /** J1: Thorns and reflect deal this % of the total defence bonuses as extra damage. */
    const val RETALIATION_DEFENCE_SCALING: Int = 64

    /** J8: a 0 from an attack, with a two-handed weapon or an off-hand, heals 2 and restores 2. */
    const val BLOCK_HEAL: Int = 75

    /** M1: Thorns hits a second time for half its damage. */
    const val THORNS_DOUBLE_HIT: Int = 84

    val ids: Set<Int> =
        setOf(
            OVERHEAL,
            THORNS,
            SHIELD_REFLECT,
            PRAYER_RESTORE,
            RETALIATION_DEFENCE_SCALING,
            BLOCK_HEAL,
            THORNS_DOUBLE_HIT,
        )
}
