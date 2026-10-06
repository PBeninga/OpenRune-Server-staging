package org.rsmod.content.leagues.demonicpacts.effects.defence

import org.rsmod.api.player.stat.OverhealProvider
import org.rsmod.content.leagues.demonicpacts.state.PactEffectsListener
import org.rsmod.plugin.module.PluginModule

/** Binds the defence, overheal and prayer pacts into the generic hooks they use. */
class DefencePactsModule : PluginModule() {
    override fun bind() {
        newSetBinding<PactThornsListener>()
        addSetBinding<OverhealProvider>(PactOverheal::class.java)
        addSetBinding<PactEffectsListener>(PactPrayerRestore::class.java)
    }
}
