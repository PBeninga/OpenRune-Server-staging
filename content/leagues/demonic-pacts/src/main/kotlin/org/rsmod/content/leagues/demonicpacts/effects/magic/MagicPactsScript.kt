package org.rsmod.content.leagues.demonicpacts.effects.magic

import jakarta.inject.Inject
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Runs L3's burn damage on the caster's soft timer. */
class MagicPactsScript @Inject constructor(private val burns: PactBurns) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerSoftTimer(PactBurns.TIMER) { burns.onTimer(player) }
    }
}
