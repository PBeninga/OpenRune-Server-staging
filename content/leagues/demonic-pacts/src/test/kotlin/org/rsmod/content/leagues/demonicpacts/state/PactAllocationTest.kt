package org.rsmod.content.leagues.demonicpacts.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.rsmod.content.leagues.demonicpacts.tree.PactEffect
import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.content.leagues.demonicpacts.tree.PactNodeSize
import org.rsmod.content.leagues.demonicpacts.tree.PactStyle
import org.rsmod.content.leagues.demonicpacts.tree.PactTree

class PactAllocationTest {
    private val tree =
        PactTree(
            listOf(
                node("AA", 0, effect = 1),
                node("B1", 1, effect = 12),
                node("B2", 2, effect = 12),
                node("C1", 3, effect = 13),
                node("C2", 4, effect = 13),
            ),
            listOf("AA" to "B1", "AA" to "B2", "B1" to "C1", "C1" to "C2"),
        )

    @Test
    fun `the root needs no link and costs nothing`() {
        val result = validate(owned = emptyList(), requested = listOf(0), earned = 0)
        assertEquals(PactCommitResult.Committed(listOf(tree["AA"]), pointsSpent = 0), result)
    }

    @Test
    fun `nodes may link through others in the same commit, in any order`() {
        val result = validate(owned = listOf("AA"), requested = listOf(4, 1, 3), earned = 3)
        val added = listOf(tree["B1"], tree["C1"], tree["C2"])
        assertEquals(PactCommitResult.Committed(added, pointsSpent = 3), result)
    }

    @Test
    fun `a node with no owned or selected neighbour is refused`() {
        assertRefused(PactCommitRefusal.NotLinked, owned = listOf("AA"), requested = listOf(3))
        assertRefused(PactCommitRefusal.NotLinked, owned = emptyList(), requested = listOf(1))
    }

    @Test
    fun `spending past the points earned is refused`() {
        assertRefused(
            PactCommitRefusal.NotEnoughPoints,
            owned = listOf("AA", "B1"),
            requested = listOf(2, 3),
            earned = 2,
        )
    }

    @Test
    fun `spending past the cap is refused even with points to spare`() {
        assertRefused(
            PactCommitRefusal.OverSpendCap,
            owned = listOf("AA", "B1"),
            requested = listOf(2, 3),
            earned = 10,
            cap = 2,
        )
    }

    @Test
    fun `forged selections are refused`() {
        assertRefused(PactCommitRefusal.NothingSelected, owned = listOf("AA"), requested = listOf())
        assertRefused(PactCommitRefusal.InvalidSelection, owned = listOf("AA"), requested = listOf(5))
        assertRefused(PactCommitRefusal.InvalidSelection, owned = listOf("AA"), requested = listOf(-1))
        assertRefused(
            PactCommitRefusal.InvalidSelection,
            owned = listOf("AA"),
            requested = listOf(1, 1),
        )
        assertRefused(PactCommitRefusal.AlreadyOwned, owned = listOf("AA"), requested = listOf(0))
    }

    @Test
    fun `points spent leave out the root`() {
        assertEquals(0, PactAllocation.pointsSpent(listOf(tree["AA"])))
        assertEquals(2, PactAllocation.pointsSpent(listOf(tree["AA"], tree["B1"], tree["B2"])))
        assertEquals(1, PactAllocation.pointsSpent(listOf(tree["C2"])))
    }

    @Test
    fun `effects sum the values of repeated effect ids`() {
        val effects = PactEffects.of(listOf(tree["AA"], tree["B1"], tree["B2"], tree["C1"]))
        assertEquals(mapOf(1 to 10, 12 to 20, 13 to 10), effects.toMap())
        assertEquals(0, effects[99])
        assertSame(PactEffects.NONE, PactEffects.of(emptyList()))
    }

    @Test
    fun `effect values replace the node values but keep the node counts`() {
        val values = PactEffectValues { node -> if (node.id == "AA") 7 else node.effect.value }
        val effects = PactEffects.of(listOf(tree["AA"], tree["B1"], tree["B2"], tree["C1"]), values)
        assertEquals(mapOf(1 to 7, 12 to 20, 13 to 10), effects.toMap())
        assertEquals(1, effects.nodeCount(1))
    }

    private fun validate(
        owned: List<String>,
        requested: List<Int>,
        earned: Int = 40,
        cap: Int = 40,
    ): PactCommitResult =
        PactAllocation.validate(tree, owned.map(tree::get), requested, earned, cap)

    private fun assertRefused(
        refusal: PactCommitRefusal,
        owned: List<String>,
        requested: List<Int>,
        earned: Int = 40,
        cap: Int = 40,
    ) {
        assertEquals(PactCommitResult.Refused(refusal), validate(owned, requested, earned, cap))
    }

    private fun node(id: String, index: Int, effect: Int) =
        PactNode(
            id = id,
            index = index,
            dbrow = 100 + index,
            effect = PactEffect(effect, 10),
            style = PactStyle.Varied,
            size = PactNodeSize.Medium,
            drawX = 0,
            drawY = 0,
            sprite = 0,
            text = id,
        )
}
