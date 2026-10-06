package org.rsmod.api.combat.formulas.accuracy

/**
 * Adjusts the final attack and defence rolls of a PvN or PvP accuracy check, after every upstream
 * modifier (special attack multipliers, slayer and salve boosts, brimstone, ...) has been applied
 * and before the hit chance is computed.
 */
public interface AccuracyRollModifier {
    /**
     * Returns the equipment attack bonus of the attack's style to use instead of [attackBonus],
     * before the base attack roll is computed from it.
     */
    public fun modifyAttackBonus(attackBonus: Int): Int = attackBonus

    /** Returns the attack roll to use instead of [attackRoll]. */
    public fun modifyAttackRoll(attackRoll: Int): Int = attackRoll

    /** Returns the defence roll to use instead of [defenceRoll]. */
    public fun modifyDefenceRoll(defenceRoll: Int): Int = defenceRoll

    /**
     * Returns the offensive prayer multiplier (for example `1.2` for Rigour's accuracy) to use for
     * the attacker's effective level instead of [prayerBonus].
     */
    public fun modifyPrayerBonus(prayerBonus: Double): Double = prayerBonus

    /**
     * Returns the final hit chance (`0..10,000`) to roll against instead of [hitChance], given the
     * final [attackRoll] and [defenceRoll] it was computed from.
     */
    public fun modifyHitChance(hitChance: Int, attackRoll: Int, defenceRoll: Int): Int = hitChance

    public companion object {
        /** Leaves both rolls unchanged. */
        public val NONE: AccuracyRollModifier = object : AccuracyRollModifier {}
    }
}
