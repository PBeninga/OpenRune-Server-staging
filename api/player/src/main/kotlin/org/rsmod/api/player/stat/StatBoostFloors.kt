package org.rsmod.api.player.stat

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/**
 * Levels added on top of a stat's **base** level to form its **resting level**: the level that
 * boost decay stops at, that regeneration and restores bring the stat back to, and that
 * [statHeal] may heal up to. Combat level and other base-level checks never see them.
 */
public object StatBoostFloors {
    private val floors = AttributeKey<MutableMap<String, Int>>(temp = true)

    /** The floor of [stat] (an RSCM name such as `"stat.defence"`), `0` if none. */
    public fun get(player: Player, stat: String): Int = player.attr[floors]?.get(stat) ?: 0

    /** Every non-zero floor of [player], keyed by stat. */
    public fun all(player: Player): Map<String, Int> = player.attr[floors]?.toMap() ?: emptyMap()

    /** Sets the floor of [stat]; `0` or less removes it. Levels are not changed. */
    public fun set(player: Player, stat: String, levels: Int) {
        if (levels <= 0) {
            val map = player.attr[floors] ?: return
            map.remove(stat)
            if (map.isEmpty()) {
                player.attr.remove(floors)
            }
            return
        }
        player.attr.getOrPut(floors) { mutableMapOf() }[stat] = levels
    }

    /** Removes every floor of [player]. Levels are not changed. */
    public fun clear(player: Player) {
        player.attr.remove(floors)
    }
}

/** The level [stat] rests at: its base level plus its [StatBoostFloors] floor. */
public fun Player.statRestingLevel(stat: String): Int =
    statBase(stat) + StatBoostFloors.get(this, stat)
