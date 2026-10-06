package org.rsmod.content.leagues.demonicpacts.effects.stats

import jakarta.inject.Inject
import org.rsmod.api.player.stat.StatBoostFloors
import org.rsmod.api.player.stat.StatBoosts
import org.rsmod.api.script.onPlayerInit
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.DEFENCE_BOOST
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.DEFENCE_STAT
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sets up the stat boost floors before login. The saved Defence level holds the pact boost when the
 * player's pacts were active at logout, and the login pact refresh ([PactDefenceBoostRefresh]) may
 * run before [StatBoosts]' own login setup; with the floors already in place that refresh changes
 * nothing, whatever the order.
 *
 * When the player's pacts were switched on or off while they were logged out
 * ([DemonicPacts.wasActiveAtLogout]), the saved level is out of date: the floor is first set to what
 * the saved level holds, then [StatBoosts.refresh] moves the level to the new floor, so a boost
 * that no longer applies is taken off (never below the resting level) and a restored one is added.
 */
class StatPactsScript
@Inject
constructor(private val pacts: DemonicPacts, private val boosts: StatBoosts) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerInit { initialise(player) }
    }

    private fun initialise(player: Player) {
        val active = pacts.isActive(player)
        val wasActive = pacts.wasActiveAtLogout(player)
        if (!active && !wasActive) {
            return
        }
        boosts.initialise(player)
        if (active == wasActive) {
            return
        }
        val boost = pacts.ownedEffects(player)[DEFENCE_BOOST]
        val inSavedLevel = if (wasActive) boost else 0
        val applies = if (active) boost else 0
        if (inSavedLevel == applies) {
            return
        }
        val floor = StatBoostFloors.get(player, DEFENCE_STAT)
        StatBoostFloors.set(player, DEFENCE_STAT, floor - applies + inSavedLevel)
        boosts.refresh(player)
    }
}
