package org.rsmod.content.leagues.demonicpacts.state

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.game.entity.Player

/**
 * The Demonic Pacts rules for one player: points, commits, resets and the effect snapshot. Content
 * calls this, never [PactState] directly.
 *
 * - **Points earned** are the sum of every [PactPointSource]. The tree shows them capped at the
 *   [PactSpendCap], so the client's own "no more points" check also enforces the cap.
 * - **Points spent** are the owned nodes, not counting the root.
 * - **Resets available** are the sum of every [PactResetSource] minus the resets used, at most
 *   [MAX_RESETS] (63, the width of Jagex's `talent_resets_available`).
 *
 * [refresh] recomputes all three and the effects. It runs on login, after a commit, reset or grant,
 * and should be called by content whose [PactPointSource] or [PactResetSource] answer changes.
 *
 * Everything here is for players whose pacts are active ([PactActivation]). For anyone else the
 * points, resets and effects read as none, commits and resets are refused with [NOT_ACTIVE], grants
 * and the admin setters change nothing, and a refresh only clears what an active session showed.
 */
@Singleton
class DemonicPacts
@Inject
constructor(
    private val tree: PactTree,
    private val state: PactState,
    private val activation: PactActivation,
    private val effectVarbits: PactEffectVarbits,
    private val pointSources: Set<PactPointSource>,
    private val resetSources: Set<PactResetSource>,
    private val spendCap: PactSpendCap,
    private val resetGuard: PactResetGuard,
    private val commitListeners: Set<PactCommitListener>,
    private val resetListeners: Set<PactResetListener>,
    private val effectsListeners: Set<PactEffectsListener>,
    private val effectValues: PactEffectValues,
) {
    fun isActive(player: Player): Boolean = activation.isActive(player)

    fun owned(player: Player): List<PactNode> = state.owned(player)

    fun isOwned(player: Player, node: PactNode): Boolean = state.isOwned(player, node)

    /** The sum of every [PactPointSource], uncapped. */
    fun pointsEarned(player: Player): Int =
        if (isActive(player)) pointSources.sumOf { it.points(player) } else 0

    fun pointsSpent(player: Player): Int = PactAllocation.pointsSpent(state.owned(player))

    fun spendCap(player: Player): Int = spendCap.cap(player)

    /** The points [player] can still spend: within both their points and the spend cap. */
    fun pointsAvailable(player: Player): Int =
        (minOf(pointsEarned(player), spendCap(player)) - pointsSpent(player)).coerceAtLeast(0)

    fun resetsEarned(player: Player): Int =
        if (isActive(player)) resetSources.sumOf { it.resets(player) } else 0

    /**
     * The resets [player] can use: earned minus used, at most [MAX_RESETS], which is what Jagex's
     * `talent_resets_available` can hold. Resets earned past it wait until the count drops below.
     */
    fun resetsAvailable(player: Player): Int =
        (resetsEarned(player) - state.resetsUsed(player)).coerceIn(0, MAX_RESETS)

    /** The effects of [player]'s owned nodes. */
    fun effects(player: Player): PactEffects {
        if (!isActive(player)) {
            return PactEffects.NONE
        }
        return player.attr[EFFECTS] ?: rebuildEffects(player, state.owned(player))
    }

    /**
     * The effects [player]'s owned nodes give while their pacts are active, whether or not they are
     * now: for content that undoes an effect saved while they were.
     */
    fun ownedEffects(player: Player): PactEffects = PactEffects.of(state.owned(player), effectValues)

    /**
     * Whether `league_type` still holds the value [refresh] sets for an active player and clears for
     * an inactive one. Read before the login refresh (`onPlayerInit`), it tells whether the player's
     * pacts were active when they logged out, and so whether their saved levels hold pact boosts.
     */
    fun wasActiveAtLogout(player: Player): Boolean = state.isLeagueTypeSet(player)

    /** Adds [points] to the points given through this API ([GrantedPactPoints]). */
    fun grantPoints(player: Player, points: Int) {
        require(points >= 0) { "points must not be negative: $points" }
        if (!isActive(player)) {
            return
        }
        state.setPointsGranted(player, state.pointsGranted(player) + points)
        refresh(player)
    }

    /**
     * Adds [resets] to the resets given through this API ([GrantedPactResets]), without taking the
     * resets available past [MAX_RESETS].
     *
     * @return The resets added: fewer than [resets] at the cap, 0 for an inactive player.
     */
    fun grantReset(player: Player, resets: Int = 1): Int {
        require(resets >= 0) { "resets must not be negative: $resets" }
        if (!isActive(player)) {
            return 0
        }
        val granted = resets.coerceAtMost(MAX_RESETS - resetsAvailable(player)).coerceAtLeast(0)
        if (granted > 0) {
            state.setResetsGranted(player, state.resetsGranted(player) + granted)
            refresh(player)
        }
        return granted
    }

    /**
     * Buys the nodes [nodeIndices] (indices in `enum_5942` order, as the tree interface sends
     * them) if [PactAllocation.validate] allows it, then tells the [PactCommitListener]s.
     */
    fun commit(player: Player, nodeIndices: Collection<Int>): PactCommitResult {
        if (!isActive(player)) {
            return PactCommitResult.Refused(PactCommitRefusal.NotActive)
        }
        val owned = state.owned(player)
        val result =
            PactAllocation.validate(
                tree = tree,
                owned = owned,
                requested = nodeIndices,
                pointsEarned = pointsEarned(player),
                spendCap = spendCap(player),
            )
        if (result is PactCommitResult.Committed) {
            state.setOwned(player, owned + result.added)
            refresh(player)
            for (listener in commitListeners) {
                listener.onCommit(player, result.added)
            }
        }
        return result
    }

    /**
     * Refunds every owned node except the root, using one reset. Refused when nothing but the root
     * is owned, when the [PactResetGuard] refuses, or when no reset is available.
     */
    fun reset(player: Player): PactResetResult {
        if (!isActive(player)) {
            return PactResetResult.Refused(NOT_ACTIVE)
        }
        val owned = state.owned(player)
        val removed = owned.filter { it.index != PactTree.ROOT_INDEX }
        if (removed.isEmpty()) {
            return PactResetResult.Refused(NOTHING_TO_RESET)
        }
        val refusal = resetGuard.refusal(player)
        if (refusal != null) {
            return PactResetResult.Refused(refusal)
        }
        if (resetsAvailable(player) <= 0) {
            return PactResetResult.Refused(NO_RESETS)
        }
        val usedBelowCap = maxOf(state.resetsUsed(player), resetsEarned(player) - MAX_RESETS)
        state.setResetsUsed(player, usedBelowCap + 1)
        state.setOwned(player, owned - removed.toSet())
        refresh(player)
        for (listener in resetListeners) {
            listener.onReset(player, removed)
        }
        return PactResetResult.Reset(removed)
    }

    /**
     * Recomputes the shown points and resets and the effects from the current state, and marks the
     * player as in the Demonic Pacts League (`league_type`, which the tree's effect panel and the
     * skill guides read). For an inactive player it clears what an earlier active session showed
     * instead ([deactivate]).
     */
    fun refresh(player: Player) {
        if (!isActive(player)) {
            deactivate(player)
            return
        }
        val owned = state.owned(player)
        val earned = minOf(pointsEarned(player), spendCap(player))
        state.publish(player, earned, PactAllocation.pointsSpent(owned), resetsAvailable(player))
        state.setLeagueType(player)
        val effects = rebuildEffects(player, owned)
        effectVarbits.publish(player, effects)
        notifyEffects(player, effects)
    }

    /**
     * [refresh], then sends every pact var, for a login whose var sync may not have gone out. For an
     * inactive player it only clears those vars ([deactivate]), sending only the ones that change.
     */
    fun refreshOnLogin(player: Player) {
        if (!isActive(player)) {
            deactivate(player)
            return
        }
        refresh(player)
        state.resync(player)
        effectVarbits.resync(player)
    }

    /** Admin: owns exactly [nodes], ignoring links, points and the spend cap. */
    fun setOwnedUnchecked(player: Player, nodes: Collection<PactNode>) {
        if (!isActive(player)) {
            return
        }
        state.setOwned(player, nodes)
        refresh(player)
    }

    /** Admin: sets the points granted through this API. */
    fun setPointsGranted(player: Player, points: Int) {
        if (!isActive(player)) {
            return
        }
        state.setPointsGranted(player, points)
        refresh(player)
    }

    /** Admin: makes [resets] resets available, through the granted and used counters. */
    fun setResetsAvailable(player: Player, resets: Int) {
        if (!isActive(player)) {
            return
        }
        val target = resets.coerceIn(0, MAX_RESETS)
        val fromOtherSources = resetsEarned(player) - state.resetsGranted(player)
        if (target >= fromOtherSources) {
            state.setResetsGranted(player, target - fromOtherSources)
            state.setResetsUsed(player, 0)
        } else {
            state.setResetsGranted(player, 0)
            state.setResetsUsed(player, fromOtherSources - target)
        }
        refresh(player)
    }

    /**
     * Clears the shown points, resets, effect totals and `league_type` of a player whose pacts are
     * inactive (they may have been active before, as these vars are saved), and tells the
     * [PactEffectsListener]s their effects are gone. Their owned nodes and counters are kept, so
     * switching pacts back on restores them. A var already clear is not sent.
     */
    private fun deactivate(player: Player) {
        state.clearShown(player)
        effectVarbits.publish(player, PactEffects.NONE)
        player.attr.remove(EFFECTS)
        notifyEffects(player, PactEffects.NONE)
    }

    private fun notifyEffects(player: Player, effects: PactEffects) {
        for (listener in effectsListeners) {
            listener.onEffectsRefreshed(player, effects)
        }
    }

    private fun rebuildEffects(player: Player, owned: List<PactNode>): PactEffects {
        val effects = PactEffects.of(owned, effectValues)
        player.attr[EFFECTS] = effects
        return effects
    }

    companion object {
        const val NO_RESETS: String = "You need at least one pact reset point to do that."
        const val NOTHING_TO_RESET: String = "You have no pacts to reset."
        const val NOT_ACTIVE: String = "Demonic pacts aren't available to you."

        /** The most resets a player can have available: what `talent_resets_available` holds. */
        val MAX_RESETS: Int by lazy { PactState.MAX_RESETS_SHOWN }

        private val EFFECTS: AttributeKey<PactEffects> = AttributeKey()
    }
}
