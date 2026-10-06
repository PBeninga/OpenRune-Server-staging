package org.rsmod.content.leagues.demonicpacts.effects.defence

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Told each time Thorns (D1) hits [Npc] back for a player, for effects that also trigger "on
 * Thorns" (G5 and G10 heal). A set (Guice multibinder) declared in [DefencePactsModule].
 */
fun interface PactThornsListener {
    fun onThorns(player: Player, attacker: Npc)
}
