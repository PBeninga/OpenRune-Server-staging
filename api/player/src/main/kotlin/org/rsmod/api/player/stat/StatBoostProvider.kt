package org.rsmod.api.player.stat

import org.rsmod.game.entity.Player

/**
 * Contributes **permanent** stat boosts: levels added to a stat's resting level that never decay
 * and come back after restores, death and drains.
 *
 * Register implementations with a Guice `Multibinder` (`addSetBinding<StatBoostProvider>(...)`).
 * Boosts from all providers are summed. Call [StatBoosts.refresh] whenever an answer may have
 * changed (for example after the player unlocks or loses a boost).
 */
public interface StatBoostProvider {
    /**
     * Returns [player]'s permanent boosts, as levels keyed by stat RSCM name (for example
     * `"stat.defence" to 15`). Empty for none.
     */
    public fun permanentBoosts(player: Player): Map<String, Int>
}
