package org.rsmod.api.combat.formulas.maxhit

/** Adjusts the inputs of a PvN or PvP max hit formula. */
public interface MaxHitModifier {
    /** Returns the melee or ranged strength bonus to use instead of [strengthBonus]. */
    public fun modifyStrengthBonus(strengthBonus: Int): Int = strengthBonus

    /**
     * Returns the worn magic damage bonus, in tenths of a percent (`100` = +10%), to use instead
     * of [magicDamageBonus].
     */
    public fun modifyMagicDamageBonus(magicDamageBonus: Int): Int = magicDamageBonus

    /**
     * Returns the strength prayer multiplier (for example `1.23` for Piety) to use for the
     * attacker's effective level instead of [prayerBonus].
     */
    public fun modifyPrayerBonus(prayerBonus: Double): Double = prayerBonus

    /**
     * Returns the prayer magic damage bonus, in tenths of a percent (`40` = Augury's +4%), to use
     * instead of [prayerDamageBonus].
     */
    public fun modifyMagicPrayerDamageBonus(prayerDamageBonus: Int): Int = prayerDamageBonus

    public companion object {
        /** Leaves every term unchanged. */
        public val NONE: MaxHitModifier = object : MaxHitModifier {}
    }
}
