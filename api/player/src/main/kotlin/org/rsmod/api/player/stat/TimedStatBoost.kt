package org.rsmod.api.player.stat

/**
 * A timed, capped stat boost, such as +1 Magic per trigger for 30 ticks, at most +10. See
 * [StatBoosts.addTimedBoost].
 *
 * Declare each boost as a subclass with a no-argument constructor and register it with a Guice
 * `Multibinder` (`addSetBinding<TimedStatBoost>(MagicRegenerateBoost::class.java)`), so that
 * `StatBoostScript` ends it when its timer fires.
 *
 * @property stat The boosted stat, for example `"stat.magic"`.
 * @property timer The soft timer that ends the boost. Owned by this boost alone.
 * @property amountVar The temporary varp or varbit (`"varp.x"` or `"varbit.x"`) holding the
 *   current boost in levels. Owned by this boost alone; it is cleared on login.
 * @property durationTicks How long the boost lasts after its latest trigger.
 */
public abstract class TimedStatBoost(
    public val stat: String,
    public val timer: String,
    public val amountVar: String,
    public val durationTicks: Int,
) {
    init {
        require(durationTicks > 0) { "`durationTicks` must be positive: $durationTicks" }
    }

    override fun toString(): String =
        "TimedStatBoost(" +
            "stat=$stat, " +
            "timer=$timer, " +
            "amountVar=$amountVar, " +
            "durationTicks=$durationTicks" +
            ")"
}
