package org.rsmod.content.leagues.demonicpacts.tree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PactTreeTest {
    private val nodes =
        listOf(
            node("AA", 0),
            node("B1", 1),
            node("B2", 2),
            node("C1", 3),
            node("C2", 4, PactNodeSize.Large),
        )

    private val links =
        listOf("AA" to "B1", "B1" to "AA", "AA" to "B2", "B1" to "C1", "C1" to "C2", "B2" to "C1")

    @Test
    fun `links in both directions count as one edge`() {
        val tree = PactTree(nodes, links)
        assertEquals(setOf(0 to 1, 0 to 2, 1 to 3, 3 to 4, 2 to 3), tree.edges)
        assertTrue(tree.isLinked(tree["B1"], tree["AA"]))
        assertFalse(tree.isLinked(tree["AA"], tree["C1"]))
        assertEquals(listOf("B1", "B2", "C2"), tree.neighbours(tree["C1"]).map(PactNode::id))
    }

    @Test
    fun `hops count the shortest path from the root`() {
        val tree = PactTree(nodes, links)
        assertEquals("AA", tree.root.id)
        assertEquals(listOf(0, 1, 1, 2, 3), tree.nodes.map(tree::hopsFromRoot))
        assertEquals(listOf("C2"), tree.capstones.map(PactNode::id))
    }

    @Test
    fun `node bits map to five varps of 32`() {
        val node = node("X", 37)
        assertEquals(1, node.varpIndex)
        assertEquals(5, node.bitInVarp)
    }

    @Test
    fun `the description fills in the value and drops the tags`() {
        val node = node("X", 0).copy(text = "+#% <col=*>Regen</col><br><br>More")
        assertEquals("+10% Regen\n\nMore", node.description())
    }

    @Test
    fun `a node unreachable from the root is refused`() {
        assertThrows<IllegalArgumentException> {
            PactTree(nodes, listOf("AA" to "B1", "AA" to "B2", "B1" to "C1"))
        }
    }

    @Test
    fun `nodes out of index order are refused`() {
        assertThrows<IllegalArgumentException> { PactTree(nodes.reversed(), links) }
    }

    @Test
    fun `shipped node ids name 132 nodes in bit order with AA first`() {
        val ids = PactTreeLoader.readNodeIds()
        assertEquals(132, ids.size)
        assertEquals((0 until 132).toList(), ids.map(PactNodeId::bit))
        assertEquals(132, ids.map(PactNodeId::id).toSet().size)
        assertEquals(132, ids.map(PactNodeId::dbrow).toSet().size)
        assertEquals(PactNodeId("AA", 0, 15404), ids.first())
    }

    private fun node(id: String, index: Int, size: PactNodeSize = PactNodeSize.Medium) =
        PactNode(
            id = id,
            index = index,
            dbrow = 100 + index,
            effect = PactEffect(index + 1, 10),
            style = PactStyle.Varied,
            size = size,
            drawX = 0,
            drawY = 0,
            sprite = 0,
            text = id,
        )
}
