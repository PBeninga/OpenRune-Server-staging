package org.rsmod.content.leagues.demonicpacts.tree

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState

/**
 * The tree built from the cache matches `demonic-pacts/pact-nodes.json`, a snapshot decoded from
 * the rev-240 cache and matched to the wiki node ids.
 */
class PactTreeDataTest {
    @Test
    fun GameTestState.`the cache tree has the snapshot's nodes, edges and effect ids`() =
        runGameTest {
            val tree = PactTreeLoader.load()
            assertEquals(132, snapshot.counts.nodes)
            assertEquals(snapshot.counts.nodes, tree.size)
            assertEquals(164, snapshot.counts.edges)
            assertEquals(snapshot.edges.map(::edgeKey).toSet(), tree.edgeKeys())
            assertEquals(snapshot.counts.edges, tree.edges.size)
            assertEquals(73, snapshot.counts.effectIds)
            assertEquals(snapshot.nodes.map { it.effects.single().effect }.toSet(), tree.effectIds)
            assertEquals(snapshot.counts.effectIds, tree.effectIds.size)
        }

    @Test
    fun GameTestState.`every node equals its snapshot entry`() = runGameTest {
        val tree = PactTreeLoader.load()
        for (expected in snapshot.nodes) {
            val node = tree[expected.id]
            val effect = expected.effects.single()
            val message = "node ${expected.id}"
            assertEquals(expected.bit, node.index, message)
            assertEquals(expected.dbrow, node.dbrow, message)
            assertEquals(expected.varp, "combat_mastery_perm_${node.varpIndex}", message)
            assertEquals(expected.bitInVarp, node.bitInVarp, message)
            assertEquals(PactEffect(effect.effect, effect.value), node.effect, message)
            assertEquals(expected.nodeType, node.style.nodeType, message)
            assertEquals(expected.nodeSize, node.size.nodeSize, message)
            assertEquals(expected.spriteId, node.sprite, message)
            assertEquals(expected.drawCoord, listOf(node.drawX, node.drawY), message)
            val text = expected.cacheText.replace("#", effect.value.toString())
            assertEquals(text, node.description(), message)
            val links = tree.neighbours(node).map { it.id }.toSet()
            assertEquals(expected.links.toSet(), links, message)
        }
    }

    @Test
    fun GameTestState.`AA is the root with six links`() = runGameTest {
        val tree = PactTreeLoader.load()
        assertSame(tree["AA"], tree.root)
        assertEquals(0, tree.hopsFromRoot(tree.root))
        assertEquals(
            setOf("B1", "B2", "B3", "BA", "BB", "BC"),
            tree.neighbours(tree.root).map { it.id }.toSet(),
        )
    }

    @Test
    fun GameTestState.`the deepest capstone is nine hops from AA`() = runGameTest {
        val tree = PactTreeLoader.load()
        assertEquals(14, tree.capstones.size)
        val deepest = tree.capstones.maxOf(tree::hopsFromRoot)
        assertEquals(9, deepest)
        assertEquals(deepest, tree.nodes.maxOf(tree::hopsFromRoot))
        assertEquals(
            listOf("N4", "N5", "N6", "N7", "N8", "N9"),
            tree.capstones.filter { tree.hopsFromRoot(it) == deepest }.map { it.id }.sorted(),
        )
    }

    @Test
    fun GameTestState.`the shipped tree is the cache tree`() = runGameTest {
        val tree = PactTreeLoader.shipped
        assertEquals(PactTreeLoader.load().nodes, tree.nodes)
        assertSame(tree, PactTreeProvider().get())
    }

    private fun PactTree.edgeKeys(): Set<Set<String>> =
        edges.map { (a, b) -> setOf(byIndex(a).id, byIndex(b).id) }.toSet()

    private fun edgeKey(edge: List<String>): Set<String> = edge.toSet()

    private data class Snapshot(
        val counts: Counts,
        val nodes: List<SnapshotNode>,
        val edges: List<List<String>>,
    )

    private data class Counts(val nodes: Int, val edges: Int, val effectIds: Int)

    private data class SnapshotNode(
        val id: String,
        val bit: Int,
        val varp: String,
        val bitInVarp: Int,
        val dbrow: Int,
        val nodeType: Int?,
        val nodeSize: Int?,
        val spriteId: Int,
        val drawCoord: List<Int>,
        val links: List<String>,
        val effects: List<SnapshotEffect>,
        val cacheText: String,
    )

    private data class SnapshotEffect(val effect: Int, val value: Int)

    private companion object {
        const val SNAPSHOT: String = "demonic-pacts/pact-nodes.json"

        val snapshot: Snapshot by lazy {
            val stream =
                PactTreeDataTest::class.java.classLoader.getResourceAsStream(SNAPSHOT)
                    ?: error("Missing test resource $SNAPSHOT")
            ObjectMapper()
                .registerKotlinModule()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(stream)
        }
    }
}
