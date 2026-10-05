package org.rsmod.tools.wiki.dumping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.droptable.toml.DropTableTomlWriter
import org.rsmod.tools.wiki.dumping.wiki.WikiDropParser

class SeparateRollRatesTest {
    private fun entry(
        obj: String,
        weight: Int,
        denominator: Int,
        rolls: Int = 1,
        subsection: String = "Uniques",
    ) =
        ResolvedDropEntry(
            obj = obj,
            quantity = "1",
            weight = weight,
            rollDenominator = denominator,
            wikiName = obj,
            subsection = subsection,
            rolls = rolls,
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

    @Test
    fun `rolls split groups and set the main table's rolls`() {
        val main = (1..10).map { entry("obj.common$it", 10, 100, rolls = 2, subsection = "Main") }
        val twice =
            listOf(entry("obj.unique_a", 1, 1024, rolls = 2), entry("obj.unique_b", 1, 1024, rolls = 2))
        val once = listOf(entry("obj.unique_c", 1, 1024, rolls = 1))

        val (mainEntries, _, separate) =
            GeneratedDropTableSpec.finalizeMainRolls(main + twice + once, emptyList())

        assertEquals(2, GeneratedDropTableSpec.mainRollsOf(mainEntries))
        assertEquals(listOf(2 to 2, 1 to 1), separate.map { it.accessNumerator to it.rolls })
    }

    @Test
    fun `main entries that disagree on rolls roll once`() {
        val main = listOf(entry("obj.a", 1, 10, rolls = 2), entry("obj.b", 1, 10, rolls = 3))

        assertEquals(1, GeneratedDropTableSpec.mainRollsOf(main))
    }

    @Test
    fun `rolls parse whole numbers only`() {
        assertEquals(2, WikiDropParser.parseRolls("2"))
        assertEquals(3, WikiDropParser.parseRolls(" 3 "))
        assertEquals(1, WikiDropParser.parseRolls("1.33"))
        assertEquals(1, WikiDropParser.parseRolls(null))
        assertEquals(1, WikiDropParser.parseRolls("abc"))
    }

    @Test
    fun `toml export writes the main and separate rolls`() {
        val toml = DropTableTomlWriter.write(DropTableTomlExporter.exportTable(twiceRolledSpec()))

        assertEquals(2, Regex("^rolls = 2$", RegexOption.MULTILINE).findAll(toml).count())
        assertEquals(1, Regex("^numerator = 2$", RegexOption.MULTILINE).findAll(toml).count())
    }

    @Test
    fun `kotlin export writes main rolls and a rolls block`() {
        val kotlin = DropTableCodeGenerator.generate(twiceRolledSpec())

        assertTrue("    mainRolls = 2," in kotlin, kotlin)
        assertTrue("        rolls(2) {\n            2 outOf 1024 separate" in kotlin, kotlin)
    }

    private fun twiceRolledSpec() =
        GeneratedDropTableSpec(
            tableVarName = "TestDropTable",
            tableIdentifier = "Test",
            npcRscmKeys = listOf("npc.test"),
            guaranteed = emptyList(),
            main = listOf(entry("obj.bones", 99, 100, rolls = 2, subsection = "Main")),
            mainMaxRoll = 100,
            mainRolls = 2,
            separateRolls =
                listOf(
                    SeparateRollSpec(
                        "Uniques",
                        2,
                        1024,
                        listOf(entry("obj.a", 1, 1024), entry("obj.b", 1, 1024)),
                        rolls = 2,
                    )
                ),
            tertiary = emptyList(),
        )
}
