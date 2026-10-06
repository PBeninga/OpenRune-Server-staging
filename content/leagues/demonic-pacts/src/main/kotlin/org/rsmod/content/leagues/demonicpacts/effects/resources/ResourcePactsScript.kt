package org.rsmod.content.leagues.demonicpacts.effects.resources

import jakarta.inject.Inject
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Ends the B1 and C4 boosts when their timers fire. */
class ResourcePactsScript @Inject constructor(private val boosts: PactRegenerateBoosts) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (boost in PactRegenerateBoost.entries) {
            onPlayerSoftTimer(boost.timer) { boosts.end(player, boost) }
        }
    }
}
