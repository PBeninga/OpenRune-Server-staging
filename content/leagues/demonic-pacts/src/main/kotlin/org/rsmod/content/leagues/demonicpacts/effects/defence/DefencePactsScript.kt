package org.rsmod.content.leagues.demonicpacts.effects.defence

import jakarta.inject.Inject
import org.rsmod.api.script.onPlayerHit
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Runs the hit-taken pacts (Thorns, reflect, J8) and I6's prayer restore timer. */
class DefencePactsScript
@Inject
constructor(
    private val retaliation: PactRetaliation,
    private val blockHeal: PactBlockHeal,
    private val prayerRestore: PactPrayerRestore,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerHit {
            retaliation.onHitTaken(player, hit)
            blockHeal.onHitTaken(player, hit)
        }
        onPlayerSoftTimer(PactPrayerRestore.TIMER) { prayerRestore.onTimer(player) }
    }
}
