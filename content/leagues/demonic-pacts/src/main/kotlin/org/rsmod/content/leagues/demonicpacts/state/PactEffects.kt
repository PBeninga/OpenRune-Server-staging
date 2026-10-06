package org.rsmod.content.leagues.demonicpacts.state

import org.rsmod.content.leagues.demonicpacts.tree.PactNode

/**
 * A player's pact effects: each effect id of the owned nodes with the sum of their values, for
 * example 4 accuracy nodes (effect 12) of 25 give `this[12] == 100`, and how many owned nodes have
 * it ([nodeCount]). Immutable; [DemonicPacts] rebuilds it on login, commit and reset. Effect code
 * reads this, never the `talent_*` varbits, whose widths can be smaller than the sums.
 */
class PactEffects
private constructor(private val totals: Map<Int, Int>, private val counts: Map<Int, Int>) {
    val effectIds: Set<Int>
        get() = totals.keys

    val isEmpty: Boolean
        get() = totals.isEmpty()

    /** The summed value of [effectId], or 0 when no owned node has it. */
    operator fun get(effectId: Int): Int = totals[effectId] ?: 0

    operator fun contains(effectId: Int): Boolean = effectId in totals

    /** How many owned nodes have [effectId], whatever their values. */
    fun nodeCount(effectId: Int): Int = counts[effectId] ?: 0

    fun toMap(): Map<Int, Int> = totals

    override fun equals(other: Any?): Boolean =
        other is PactEffects && other.totals == totals && other.counts == counts

    override fun hashCode(): Int = 31 * totals.hashCode() + counts.hashCode()

    override fun toString(): String = "PactEffects($totals)"

    companion object {
        val NONE: PactEffects = PactEffects(emptyMap(), emptyMap())

        /** The effects of [nodes], each adding its value from [values] (the cache's by default). */
        fun of(
            nodes: Iterable<PactNode>,
            values: PactEffectValues = CachePactEffectValues(),
        ): PactEffects {
            val totals = sortedMapOf<Int, Int>()
            val counts = sortedMapOf<Int, Int>()
            for (node in nodes) {
                totals.merge(node.effect.id, values.value(node), Int::plus)
                counts.merge(node.effect.id, 1, Int::plus)
            }
            return if (totals.isEmpty()) NONE else PactEffects(totals, counts)
        }
    }
}
