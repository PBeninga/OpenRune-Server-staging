package org.rsmod.tools.wiki.dumping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeparateRollRatesTest {
    private fun entry(obj: String, weight: Int, denominator: Int, subsection: String = "Uniques") =
        ResolvedDropEntry(
            obj = obj,
            quantity = "1",
            weight = weight,
            rollDenominator = denominator,
            wikiName = obj,
            subsection = subsection,
        )

    private fun perItemChance(roll: SeparateRollSpec, obj: String): Double {
        val total = roll.entries.sumOf { it.weight!! }
        val weight = roll.entries.single { it.obj == obj }.weight!!
        return roll.accessNumerator.toDouble() / roll.accessDenominator * weight / total
    }

    @Test
    fun `a group of items keeps each item at its own wiki rate`() {
        val uniques =
            listOf("obj.blowpipe_fang", "obj.magic_fang", "obj.serpentine_visage", "obj.uncut_onyx")
                .map { entry(it, 1, 1024) }
        val main = (1..20).map { entry("obj.common$it", 10, 249, subsection = "Main") }

        val (_, _, separate) = GeneratedDropTableSpec.finalizeMainRolls(main + uniques, emptyList())

        val roll = separate.single()
        assertEquals(4, roll.accessNumerator)
        assertEquals(1024, roll.accessDenominator)
        for (unique in uniques) {
            assertEquals(1.0 / 1024, perItemChance(roll, unique.obj), 1e-15)
        }
    }

    @Test
    fun `a group that would pass the denominator is split per item`() {
        val supplies = (1..4).map { entry("obj.supply$it", 1, 3, subsection = "Supplies") }

        val specs = GeneratedDropTableSpec.buildSeparateRollSpecs(supplies, 3)

        assertEquals(4, specs.size)
        assertEquals(listOf(1, 1, 1, 1), specs.map { it.accessNumerator })
    }
}
