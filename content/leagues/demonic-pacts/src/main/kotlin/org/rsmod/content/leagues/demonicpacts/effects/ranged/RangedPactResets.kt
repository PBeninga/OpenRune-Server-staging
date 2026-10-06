package org.rsmod.content.leagues.demonicpacts.effects.ranged

import org.rsmod.content.leagues.demonicpacts.state.PactResetListener
import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.game.entity.Player

/** Clears the bow stacks and H6's primed style when a player resets their pacts. */
class RangedPactResets : PactResetListener {
    override fun onReset(player: Player, removed: List<PactNode>) {
        BowStacks.clear(player)
        StyleSwap.clear(player)
    }
}
