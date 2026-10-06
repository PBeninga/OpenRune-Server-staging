package org.rsmod.api.player.stat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Permanent ([StatBoostProvider]) and timed ([TimedStatBoost]) stat boosts.
 *
 * Both raise a stat's **resting level** ([StatBoostFloors]): boosted stats decay down to it,
 * drained stats regenerate up to it, and restores ([statRestore], [statHeal], death) bring the
 * stat back to it. The base level, and so the combat level, never changes. Potion boosts still cap
 * relative to the base level.
 *
 * When a floor rises, the current level rises by the same amount; when it falls, the current level
 * drops by at most the same amount and never below the new resting level, so drains are kept.
 */
@Singleton
public class StatBoosts
@Inject
constructor(providers: Set<StatBoostProvider>, timedBoosts: Set<TimedStatBoost>) {
    private val providers = providers.toList()

    /** Every registered timed boost. */
    public val timedBoosts: List<TimedStatBoost> = timedBoosts.toList()

    /** The summed permanent boosts of [player], keyed by stat. */
    public fun permanentBoosts(player: Player): Map<String, Int> {
        if (providers.isEmpty()) {
            return emptyMap()
        }
        val sum = LinkedHashMap<String, Int>()
        for (provider in providers) {
            for ((stat, levels) in provider.permanentBoosts(player)) {
                sum[stat] = (sum[stat] ?: 0) + levels
            }
        }
        return sum
    }

    /** The current amount of [boost] on [player], in levels. */
    public fun timedBoost(player: Player, boost: TimedStatBoost): Int = player.vars[boost.amountVar]

    /**
     * Adds [amount] levels to [boost] without passing [cap] levels (see [timedBoost]) and restarts
     * its timer, so that every trigger resets the duration, even at the cap.
     *
     * @return The boost's new amount.
     */
    public fun addTimedBoost(player: Player, boost: TimedStatBoost, amount: Int, cap: Int): Int {
        val current = timedBoost(player, boost)
        val boosted = timedBoost(current, amount, cap)
        if (boosted != current) {
            VarPlayerIntMapSetter.set(player, boost.amountVar, boosted)
        }
        player.softTimer(boost.timer, boost.durationTicks)
        refresh(player)
        return boosted
    }

    /** Ends [boost] on [player]: clears its amount and timer and lowers the stat. */
    public fun endTimedBoost(player: Player, boost: TimedStatBoost) {
        player.clearSoftTimer(boost.timer)
        if (timedBoost(player, boost) != 0) {
            VarPlayerIntMapSetter.set(player, boost.amountVar, 0)
        }
        refresh(player)
    }

    /**
     * Recomputes [player]'s resting-level floors from every provider and timed boost and applies
     * each change to the current level. Call it after anything that may change a provider's
     * answer.
     */
    public fun refresh(player: Player) {
        val target = targetFloors(player)
        val stats = LinkedHashSet(target.keys).apply { addAll(StatBoostFloors.all(player).keys) }
        for (stat in stats) {
            val previous = StatBoostFloors.get(player, stat)
            val floor = target[stat] ?: 0
            if (floor == previous) {
                continue
            }
            StatBoostFloors.set(player, stat, floor)
            applyFloorChange(player, stat, floor - previous, floor)
        }
    }

    /**
     * Sets up [player]'s floors on login without changing levels (saved levels already include the
     * boosts they had on logout). Timed boosts do not survive a logout and are cleared.
     */
    public fun initialise(player: Player) {
        for (boost in timedBoosts) {
            if (timedBoost(player, boost) != 0) {
                VarPlayerIntMapSetter.set(player, boost.amountVar, 0)
            }
        }
        StatBoostFloors.clear(player)
        for ((stat, floor) in targetFloors(player)) {
            StatBoostFloors.set(player, stat, floor)
        }
    }

    private fun targetFloors(player: Player): Map<String, Int> {
        val floors = LinkedHashMap(permanentBoosts(player))
        for (boost in timedBoosts) {
            val levels = timedBoost(player, boost)
            if (levels > 0) {
                floors[boost.stat] = (floors[boost.stat] ?: 0) + levels
            }
        }
        floors.replaceAll { _, levels -> max(0, levels) }
        return floors
    }

    private fun applyFloorChange(player: Player, stat: String, delta: Int, floor: Int) {
        if (delta > 0) {
            player.statAdd(stat, delta, 0)
            return
        }
        val excess = player.stat(stat) - (player.statBase(stat) + floor)
        val drop = min(-delta, excess)
        if (drop > 0) {
            player.statSub(stat, drop, 0)
        }
    }

    public companion object {
        /**
         * Adds [amount] to a timed boost of [current] levels without passing [cap]. A boost already
         * above the cap is kept (never lowered), and a negative amount is ignored.
         */
        public fun timedBoost(current: Int, amount: Int, cap: Int): Int =
            max(current, min(current + max(0, amount), cap))
    }
}
