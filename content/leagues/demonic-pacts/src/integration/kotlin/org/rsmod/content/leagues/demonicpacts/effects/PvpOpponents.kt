package org.rsmod.content.leagues.demonicpacts.effects

import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

const val OPPONENT_HITPOINTS: Int = 99

/** Registers a second player at [coords] with 99 Hitpoints, for the PvP cases. */
fun GameTestScope.registerOpponent(coords: CoordGrid): Player {
    allocZoneCollision(coords)
    val opponent = registerPlayer(coords)
    opponent.setBaseLevel("stat.hitpoints", OPPONENT_HITPOINTS)
    opponent.setCurrentLevel("stat.hitpoints", OPPONENT_HITPOINTS)
    return opponent
}
