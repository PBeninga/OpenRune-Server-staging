package org.rsmod.content.leagues.demonicpacts

import dev.openrune.ServerCacheManager
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpServerType
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Logs [player] out and a copy back in at [coords], as a save and load do: the saved (`Perm`)
 * varps, the mod level and the base and current levels of [stats] carry over, nothing else does.
 */
fun GameTestScope.relog(
    player: Player,
    coords: CoordGrid = CoordGrid(0, 50, 50, 0, 0),
    stats: List<String> = emptyList(),
): Player {
    val saved = mutableListOf<Pair<VarpServerType, Int>>()
    for (entry in player.vars) {
        val type = ServerCacheManager.getVarp(entry.key)
        if (type?.scope == VarpLifetime.Perm) {
            saved += type to entry.value
        }
    }
    val levels = stats.associateWith { player.statBase(it) to player.stat(it) }
    unregisterPlayer(player)
    val fresh = Player()
    fresh.modLevel = player.modLevel
    for ((varp, value) in saved) {
        VarPlayerIntMapSetter.set(fresh, varp, value)
    }
    for ((stat, level) in levels) {
        fresh.setBaseLevel(stat, level.first)
        fresh.setCurrentLevel(stat, level.second)
    }
    return registerPlayer(coords = coords, player = fresh)
}
