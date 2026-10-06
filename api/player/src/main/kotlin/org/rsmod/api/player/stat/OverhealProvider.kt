package org.rsmod.api.player.stat

import org.rsmod.game.entity.Player

/**
 * Lets [PlayerHealing] raise hitpoints above their resting level ("overheal").
 *
 * Register implementations with a Guice `Multibinder` (`addSetBinding<OverhealProvider>(...)` in a
 * `PluginModule`). When several providers answer, the **highest** percentage wins: overheal caps do
 * not stack.
 */
public interface OverhealProvider {
    /**
     * Returns how far above its resting level [player]'s hitpoints may be healed by [source], as a
     * percentage of the base hitpoints level (`30.0` = up to +30%). `0.0` for no overheal.
     */
    public fun overhealPercent(player: Player, source: PlayerHealSource): Double
}
