package org.rsmod.content.leagues.demonicpacts.state

import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.content.leagues.demonicpacts.tree.PactTree

/** Why a commit was refused, with the message the player is shown. */
enum class PactCommitRefusal(val message: String) {
    NothingSelected("You haven't selected any pacts."),
    InvalidSelection("Those pacts can't be selected."),
    AlreadyOwned("You already have one of those pacts."),
    NotLinked("Each pact must be linked to one you have or are taking."),
    NotEnoughPoints("You don't have enough pact points for that."),
    OverSpendCap("You can't spend any more pact points."),
    NotActive(DemonicPacts.NOT_ACTIVE),
}

sealed interface PactCommitResult {
    /** [added] were bought, in index order; [pointsSpent] is the new spent total. */
    data class Committed(val added: List<PactNode>, val pointsSpent: Int) : PactCommitResult

    data class Refused(val refusal: PactCommitRefusal) : PactCommitResult
}

sealed interface PactResetResult {
    /** [removed] were refunded, in index order. */
    data class Reset(val removed: List<PactNode>) : PactResetResult

    data class Refused(val message: String) : PactResetResult
}

/**
 * The allocation rules, as pure functions over the tree. The root (node 0, `AA`) is the way in: it
 * needs no linked node, costs no point and isn't counted as spent. Every other node costs one point
 * and must be linked to a node that is owned or bought in the same commit.
 */
object PactAllocation {
    /** Points spent on [owned]: one per node except the root. */
    fun pointsSpent(owned: Collection<PactNode>): Int =
        owned.count { it.index != PactTree.ROOT_INDEX }

    /**
     * Checks a commit of the node indices [requested] (as the client sends them) for a player who
     * owns [owned], has earned [pointsEarned] and may spend at most [spendCap]. The checks, in
     * order: a non-empty selection of distinct, known, unowned nodes; every node linked to the
     * owned ones through the selection; enough points; within the spend cap.
     */
    fun validate(
        tree: PactTree,
        owned: Collection<PactNode>,
        requested: Collection<Int>,
        pointsEarned: Int,
        spendCap: Int,
    ): PactCommitResult {
        if (requested.isEmpty()) {
            return PactCommitResult.Refused(PactCommitRefusal.NothingSelected)
        }
        if (requested.any { it !in 0 until tree.size } || requested.toSet().size != requested.size) {
            return PactCommitResult.Refused(PactCommitRefusal.InvalidSelection)
        }
        val ownedIndices = owned.mapTo(HashSet()) { it.index }
        if (requested.any { it in ownedIndices }) {
            return PactCommitResult.Refused(PactCommitRefusal.AlreadyOwned)
        }
        val added = requested.sorted().map(tree::byIndex)
        if (!isLinked(tree, ownedIndices, added)) {
            return PactCommitResult.Refused(PactCommitRefusal.NotLinked)
        }
        val spent = pointsSpent(owned) + pointsSpent(added)
        if (spent > pointsEarned) {
            return PactCommitResult.Refused(PactCommitRefusal.NotEnoughPoints)
        }
        if (spent > spendCap) {
            return PactCommitResult.Refused(PactCommitRefusal.OverSpendCap)
        }
        return PactCommitResult.Committed(added, spent)
    }

    private fun isLinked(tree: PactTree, owned: Set<Int>, added: List<PactNode>): Boolean {
        val reached = HashSet(owned)
        val pending = added.toMutableList()
        var progress = true
        while (pending.isNotEmpty() && progress) {
            progress =
                pending.removeAll { node ->
                    val linked =
                        node.index == PactTree.ROOT_INDEX ||
                            tree.neighbours(node).any { it.index in reached }
                    if (linked) {
                        reached += node.index
                    }
                    linked
                }
        }
        return pending.isEmpty()
    }
}
