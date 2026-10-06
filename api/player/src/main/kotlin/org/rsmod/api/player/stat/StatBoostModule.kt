package org.rsmod.api.player.stat

import org.rsmod.plugin.module.PluginModule

/**
 * Declares the [StatBoostProvider], [TimedStatBoost] and [OverhealProvider] set bindings and binds
 * [StatBoosts] and [PlayerHealing] as singletons.
 */
public class StatBoostModule : PluginModule() {
    override fun bind() {
        newSetBinding<StatBoostProvider>()
        newSetBinding<TimedStatBoost>()
        newSetBinding<OverhealProvider>()
        bindInstance<StatBoosts>()
        bindInstance<PlayerHealing>()
    }
}
