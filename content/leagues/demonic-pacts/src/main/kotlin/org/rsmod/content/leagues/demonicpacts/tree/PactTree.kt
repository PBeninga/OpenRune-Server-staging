package org.rsmod.content.leagues.demonicpacts.tree

import com.google.inject.ProvidedBy

/**
 * The Demonic Pacts tree: [nodes] ordered by [PactNode.index], joined by undirected links. Node 0
 * is the [root] (`AA`), which every account owns. Built once from the cache by [PactTreeLoader].
 */
@ProvidedBy(PactTreeProvider::class)
class PactTree(val nodes: List<PactNode>, links: Collection<Pair<String, String>>) {
    private val byId: Map<String, PactNode> = nodes.associateBy(PactNode::id)
    private val byDbrow: Map<Int, PactNode> = nodes.associateBy(PactNode::dbrow)
    private val adjacency: List<List<PactNode>>
    private val hops: IntArray

    val root: PactNode
        get() = nodes[ROOT_INDEX]

    val size: Int
        get() = nodes.size

    /** Every link once, as an `(index, index)` pair with the lower index first. */
    val edges: Set<Pair<Int, Int>>

    val effectIds: Set<Int> = nodes.mapTo(sortedSetOf()) { it.effect.id }

    val capstones: List<PactNode> = nodes.filter(PactNode::isCapstone)

    init {
        require(nodes.isNotEmpty()) { "A pact tree needs at least its root." }
        nodes.forEachIndexed { i, node ->
            require(node.index == i) { "Node ${node.id} has index ${node.index}, expected $i." }
        }
        require(byId.size == nodes.size) { "Duplicate pact node ids." }
        require(byDbrow.size == nodes.size) { "Duplicate pact node dbrows." }

        edges =
            links.mapTo(LinkedHashSet()) { (from, to) ->
                val a = get(from).index
                val b = get(to).index
                require(a != b) { "Pact node $from links to itself." }
                minOf(a, b) to maxOf(a, b)
            }
        val neighbours = List(nodes.size) { sortedSetOf<Int>() }
        for ((a, b) in edges) {
            neighbours[a] += b
            neighbours[b] += a
        }
        adjacency = neighbours.map { set -> set.map(nodes::get) }
        hops = hopsFrom(ROOT_INDEX, neighbours)
        val unreachable = nodes.filter { hops[it.index] < 0 }
        require(unreachable.isEmpty()) { "Pact nodes not linked to the root: $unreachable" }
    }

    operator fun get(id: String): PactNode = byId[id] ?: error("No pact node with id '$id'.")

    fun getOrNull(id: String): PactNode? = byId[id]

    fun byIndex(index: Int): PactNode = nodes[index]

    fun byDbrow(dbrow: Int): PactNode? = byDbrow[dbrow]

    fun neighbours(node: PactNode): List<PactNode> = adjacency[node.index]

    fun isLinked(a: PactNode, b: PactNode): Boolean =
        (minOf(a.index, b.index) to maxOf(a.index, b.index)) in edges

    /** The fewest links between [root] and [node]. */
    fun hopsFromRoot(node: PactNode): Int = hops[node.index]

    private fun hopsFrom(start: Int, neighbours: List<Set<Int>>): IntArray {
        val distance = IntArray(neighbours.size) { -1 }
        val queue = ArrayDeque<Int>()
        distance[start] = 0
        queue += start
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in neighbours[current]) {
                if (distance[next] < 0) {
                    distance[next] = distance[current] + 1
                    queue += next
                }
            }
        }
        return distance
    }

    companion object {
        const val ROOT_INDEX: Int = 0
    }
}
