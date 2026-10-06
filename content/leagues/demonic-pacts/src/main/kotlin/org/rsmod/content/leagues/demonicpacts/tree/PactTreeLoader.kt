package org.rsmod.content.leagues.demonicpacts.tree

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Provider
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.table.TalentTreeRow

/** Wiki id ↔ node index ↔ dbrow, read from the pack resource `demonic-pacts/node-ids.toml`. */
data class PactNodeId(val id: String, val bit: Int, val dbrow: Int)

/**
 * Builds the [PactTree] from the cache: the node order from `enum.talent_tree_node_order`
 * (`enum_5942`, index → `dbtable.talent_tree` row) and each node's data and links from its
 * [TalentTreeRow]. Node names come from `demonic-pacts/node-ids.toml`, which must agree with the
 * cache.
 */
object PactTreeLoader {
    private const val NODE_ORDER_ENUM: String = "enum.talent_tree_node_order"
    private const val NODE_IDS_RESOURCE: String = "demonic-pacts/node-ids.toml"
    private const val DBROW_MASK: Int = 0xFFFF

    private val toml: ObjectMapper = ObjectMapper(TomlFactory()).registerKotlinModule()

    val shipped: PactTree by lazy { load() }

    fun load(nodeIds: List<PactNodeId> = readNodeIds()): PactTree {
        val order = nodeOrder()
        val idsByDbrow = nodeIds.associateBy(PactNodeId::dbrow)
        require(idsByDbrow.size == nodeIds.size) { "Duplicate dbrows in $NODE_IDS_RESOURCE." }
        require(order.size == nodeIds.size) {
            "$NODE_ORDER_ENUM has ${order.size} nodes but $NODE_IDS_RESOURCE names ${nodeIds.size}."
        }

        val rows = order.map(TalentTreeRow::getRow)
        val nodes =
            rows.mapIndexed { index, row ->
                val name =
                    idsByDbrow[row.rowId] ?: error("No wiki id for talent_tree row ${row.rowId}.")
                require(name.bit == index) {
                    "${name.id} is bit ${name.bit} in $NODE_IDS_RESOURCE but $index in the cache."
                }
                row.toNode(name.id, index)
            }
        val idOf = nodes.associate { it.dbrow to it.id }
        val links =
            rows.flatMap { row ->
                row.linkedNodes.map { linked ->
                    val to = idOf[linked.rowId] ?: error("Unknown linked row ${linked.rowId}.")
                    idOf.getValue(row.rowId) to to
                }
            }
        return PactTree(nodes, links)
    }

    fun readNodeIds(): List<PactNodeId> {
        val stream =
            PactTreeLoader::class.java.classLoader.getResourceAsStream(NODE_IDS_RESOURCE)
                ?: error("Missing pact resource: $NODE_IDS_RESOURCE")
        val text = stream.bufferedReader().use { it.readText() }
        return parseNodeIds(text)
    }

    fun parseNodeIds(text: String): List<PactNodeId> {
        val file = toml.readValue<NodeIdsFile>(text)
        return file.nodes.map { (id, entry) -> PactNodeId(id, entry.bit, entry.dbrow) }
    }

    private fun nodeOrder(): List<Int> {
        val enum =
            ServerCacheManager.getEnum(NODE_ORDER_ENUM.asRSCM(RSCMType.ENUM))
                ?: error("Missing $NODE_ORDER_ENUM in the cache.")
        val byIndex =
            enum.values.entries.associate { (key, value) ->
                (key as Number).toInt() to ((value as Number).toInt() and DBROW_MASK)
            }
        val indices = byIndex.keys.sorted()
        require(indices == indices.indices.toList()) {
            "$NODE_ORDER_ENUM keys are not 0 until ${indices.size}: $indices"
        }
        return indices.map(byIndex::getValue)
    }

    private fun TalentTreeRow.toNode(id: String, index: Int): PactNode {
        val (effectId, value) = effect
        val (x, y) = drawCoord
        return PactNode(
            id = id,
            index = index,
            dbrow = rowId,
            effect = PactEffect(effectId, value),
            style = PactStyle.fromNodeType(nodeType),
            size = PactNodeSize.fromNodeSize(nodeSize),
            drawX = x,
            drawY = y,
            sprite = nodeSprite,
            text = name,
        )
    }
}

class PactTreeProvider : Provider<PactTree> {
    override fun get(): PactTree = PactTreeLoader.shipped
}

private data class NodeIdsFile(val nodes: LinkedHashMap<String, NodeIdEntry>)

private data class NodeIdEntry(val bit: Int, val dbrow: Int)
