package org.rsmod.content.leagues.demonicpacts.state

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.bits
import jakarta.inject.Inject
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.game.entity.Player

/**
 * A player's pact state, stored in the Demonic Pacts League's own vars so that the cache's tree
 * interface reads it as it is:
 * - the owned nodes: bit `i % 32` of varp `combat_mastery_perm_{i / 32}` for node index `i`;
 * - `talent_points_earned`, `talent_points_spent` and `talent_resets_available`, which the server
 *   derives (see [DemonicPacts.refresh]) and the client only reads;
 * - and three server-only counters: the points and resets granted through [DemonicPacts], and the
 *   resets used.
 *
 * This class only reads and writes vars. The rules live in [DemonicPacts].
 */
class PactState @Inject constructor(private val tree: PactTree) {
    private val ownedVarps: List<String> =
        List((tree.size + Int.SIZE_BITS - 1) / Int.SIZE_BITS) { "$OWNED_VARP_PREFIX$it" }

    /** The nodes [player] owns, in index order. */
    fun owned(player: Player): List<PactNode> = tree.nodes.filter { isOwned(player, it) }

    fun isOwned(player: Player, node: PactNode): Boolean {
        val bits = player.vars[ownedVarps[node.varpIndex]]
        return bits and (1 shl node.bitInVarp) != 0
    }

    /** Replaces the owned nodes with exactly [nodes]. */
    fun setOwned(player: Player, nodes: Collection<PactNode>) {
        val words = IntArray(ownedVarps.size)
        for (node in nodes) {
            words[node.varpIndex] = words[node.varpIndex] or (1 shl node.bitInVarp)
        }
        for ((index, varp) in ownedVarps.withIndex()) {
            setIfChanged(player, varp, words[index])
        }
    }

    fun pointsEarned(player: Player): Int = player.vars[POINTS_EARNED]

    fun pointsSpent(player: Player): Int = player.vars[POINTS_SPENT]

    fun resetsAvailable(player: Player): Int = player.vars[RESETS_AVAILABLE]

    fun pointsGranted(player: Player): Int = player.vars[POINTS_GRANTED]

    fun resetsGranted(player: Player): Int = player.vars[RESETS_GRANTED]

    fun resetsUsed(player: Player): Int = player.vars[RESETS_USED]

    fun setPointsGranted(player: Player, points: Int) {
        setIfChanged(player, POINTS_GRANTED, points.coerceAtLeast(0))
    }

    fun setResetsGranted(player: Player, resets: Int) {
        setIfChanged(player, RESETS_GRANTED, resets.coerceAtLeast(0))
    }

    fun setResetsUsed(player: Player, resets: Int) {
        setIfChanged(player, RESETS_USED, resets.coerceAtLeast(0))
    }

    /**
     * Sets the vars the tree interface shows. Each value is clamped to what its var can hold, so
     * `talent_resets_available` shows at most [MAX_RESETS_SHOWN].
     */
    fun publish(player: Player, earned: Int, spent: Int, resets: Int) {
        setIfChanged(player, POINTS_EARNED, earned.coerceAtLeast(0))
        setIfChanged(player, POINTS_SPENT, spent.coerceAtLeast(0))
        setIfChanged(player, RESETS_AVAILABLE, resets.coerceIn(0, MAX_RESETS_SHOWN))
    }

    /** Sets `league_type` to the Demonic Pacts League, Leagues VI. */
    fun setLeagueType(player: Player) {
        setIfChanged(player, LEAGUE_TYPE, LEAGUES_VI)
    }

    /** Whether `league_type` is the value [setLeagueType] sets. */
    fun isLeagueTypeSet(player: Player): Boolean = player.vars[LEAGUE_TYPE] == LEAGUES_VI

    /**
     * Clears what the client reads besides the owned nodes: the shown points and resets, and
     * `league_type` if [setLeagueType] set it. The owned nodes and the server-only counters are
     * kept, so the next refresh of an active player shows them again.
     */
    fun clearShown(player: Player) {
        publish(player, earned = 0, spent = 0, resets = 0)
        if (isLeagueTypeSet(player)) {
            setIfChanged(player, LEAGUE_TYPE, 0)
        }
    }

    /** Sends the client every var it reads, for a login whose var sync may not have gone out yet. */
    fun resync(player: Player) {
        for (varp in ownedVarps) {
            player.resyncVar(varp)
        }
        player.resyncVar(POINTS_EARNED)
        player.resyncVar(POINTS_SPENT)
        player.resyncVar(RESETS_AVAILABLE)
        player.resyncVar(LEAGUE_TYPE)
    }

    private fun setIfChanged(player: Player, varp: String, value: Int) {
        if (player.vars[varp] != value) {
            VarPlayerIntMapSetter.set(player, varp, value)
        }
    }

    companion object {
        const val OWNED_VARP_PREFIX: String = "varp.combat_mastery_perm_"
        const val POINTS_EARNED: String = "varp.talent_points_earned"
        const val POINTS_SPENT: String = "varp.talent_points_spent"
        const val RESETS_AVAILABLE: String = "varbit.talent_resets_available"
        const val POINTS_GRANTED: String = "varp.demonic_pacts_points_granted"
        const val RESETS_GRANTED: String = "varp.demonic_pacts_resets_granted"
        const val RESETS_USED: String = "varp.demonic_pacts_resets_used"
        const val LEAGUE_TYPE: String = "varbit.league_type"
        const val LEAGUES_VI: Int = 6

        val MAX_RESETS_SHOWN: Int by lazy { maxValue(RESETS_AVAILABLE) }

        /** The largest value [varbit] can hold. */
        fun maxValue(varbit: String): Int {
            val type =
                ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))
                    ?: error("Missing $varbit in the cache.")
            val width = type.bits.last - type.bits.first + 1
            return if (width >= Int.SIZE_BITS - 1) Int.MAX_VALUE else (1 shl width) - 1
        }
    }
}
