package org.rsmod.api.stats.plugin

import jakarta.inject.Inject
import org.rsmod.api.player.stat.StatBoosts
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Sets up [StatBoosts] floors on login and ends each timed boost when its timer fires. */
public class StatBoostScript @Inject constructor(private val boosts: StatBoosts) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { boosts.initialise(player) }
        for (boost in boosts.timedBoosts) {
            onPlayerSoftTimer(boost.timer) { boosts.endTimedBoost(player, boost) }
        }
    }
}
