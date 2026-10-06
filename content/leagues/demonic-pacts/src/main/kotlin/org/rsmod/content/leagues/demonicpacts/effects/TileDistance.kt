package org.rsmod.content.leagues.demonicpacts.effects

import kotlin.math.max
import org.rsmod.game.entity.PathingEntity

/**
 * Tiles between two entities, counted to the nearest tile of each footprint (Chebyshev): `1` when
 * they are adjacent, so a large npc reads the same distance from every side.
 */
internal fun PathingEntity.tilesTo(other: PathingEntity): Int {
    val dx = axisGap(coords.x, size, other.coords.x, other.size)
    val dz = axisGap(coords.z, size, other.coords.z, other.size)
    return max(dx, dz)
}

private fun axisGap(start: Int, size: Int, otherStart: Int, otherSize: Int): Int {
    val end = start + size - 1
    val otherEnd = otherStart + otherSize - 1
    return max(0, max(otherStart - end, start - otherEnd))
}
