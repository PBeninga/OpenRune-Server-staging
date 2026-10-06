package org.rsmod.content.leagues.demonicpacts.tree

/** One `(effect id, value)` pair of a node, from the `effect` column of `dbtable.talent_tree`. */
data class PactEffect(val id: Int, val value: Int)

/** The combat style a node belongs to, from the `node_type` column (none for varied nodes). */
enum class PactStyle(val nodeType: Int?) {
    Varied(null),
    Melee(3),
    Ranged(4),
    Magic(5);

    companion object {
        fun fromNodeType(nodeType: Int?): PactStyle =
            entries.firstOrNull { it.nodeType == nodeType }
                ?: error("Unknown talent_tree node_type: $nodeType")
    }
}

/** A node's size, from the `node_size` column; [Large] nodes are the capstones. */
enum class PactNodeSize(val nodeSize: Int?) {
    Medium(null),
    Small(2),
    Large(3);

    companion object {
        fun fromNodeSize(nodeSize: Int?): PactNodeSize =
            entries.firstOrNull { it.nodeSize == nodeSize }
                ?: error("Unknown talent_tree node_size: $nodeSize")
    }
}

/**
 * A pact node. [id] is the wiki id (`AA`, `F7`, ...). [index] is its position in `enum_5942`, so
 * its owned flag is bit [bitInVarp] of varp `combat_mastery_perm_[varpIndex]`. [text] is the
 * cache's tooltip template, where the client puts the effect value in place of `#`.
 */
data class PactNode(
    val id: String,
    val index: Int,
    val dbrow: Int,
    val effect: PactEffect,
    val style: PactStyle,
    val size: PactNodeSize,
    val drawX: Int,
    val drawY: Int,
    val sprite: Int,
    val text: String,
) {
    val varpIndex: Int
        get() = index / Int.SIZE_BITS

    val bitInVarp: Int
        get() = index % Int.SIZE_BITS

    val isCapstone: Boolean
        get() = size == PactNodeSize.Large

    /** The tooltip as plain text: [text] with every `#` set to the effect value and no tags. */
    fun description(): String =
        text
            .replace("#", effect.value.toString())
            .replace(LINE_BREAK, "\n")
            .replace(COLOUR_TAG, "")

    override fun toString(): String = "PactNode($id, index=$index, effect=$effect)"

    private companion object {
        val LINE_BREAK: Regex = Regex("<br>")
        val COLOUR_TAG: Regex = Regex("</?col(=[^>]*)?>")
    }
}
