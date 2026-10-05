package org.rsmod.api.droptable.toml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dtx.impl.chance.MultiChanceTable
import dtx.rs.RSDropTable
import dtx.rs.RSPrerollTableBuilder
import dtx.rs.RSWeightedTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.PendingDropItemConfig
import org.rsmod.game.entity.Player

class DropTableTomlRollsTest {
    private val mapper =
        ObjectMapper(TomlFactory())
            .registerKotlinModule()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private val toml =
        """
        id = "Test"
        npcs = ["npc.test"]

        [main]
        total = 4
        rolls = 2

        [[main.entries]]
        weight = 4
        obj = "obj.bones"

        [[main.separate_rolls]]
        numerator = 4
        denominator = 1024
        rolls = 2

        [[main.separate_rolls.entries]]
        weight = 1
        obj = "obj.a"

        [[main.separate_rolls.entries]]
        weight = 1
        obj = "obj.b"

        [[main.separate_rolls]]
        numerator = 1
        denominator = 100

        [[main.separate_rolls.entries]]
        weight = 1
        obj = "obj.c"
        """
            .trimIndent()

    private val resolver =
        object : DropTableTomlResolver {
            override fun sharedTable(name: String): RSWeightedTable<Player, DropRollItem> =
                error("no shared tables")

            override fun applyBrimstoneKeyRoll(
                builder: RSPrerollTableBuilder<Player, DropRollItem>,
                konarTaskBonus: Boolean,
            ) = Unit

            override fun applyHooks(config: PendingDropItemConfig, hooks: TomlDropHooks) = Unit
        }

    @Test
    fun `rolls parse onto the table and its separate rolls`() {
        val table = DropTableTomlParser.parse(mapper.readValue<TomlDropTableDef>(toml), resolver)

        assertEquals(2, table.mainRolls)
        @Suppress("UNCHECKED_CAST")
        val main = table.tableEntries.toList()[3] as RSWeightedTable<Player, DropRollItem>
        assertEquals(listOf(2, 1), main.inlineSeparateRolls.map { it.rolls })
    }

    @Test
    fun `a separate roll with rolls = 2 is two chance rolls`() {
        val table = DropTableTomlParser.parse(mapper.readValue<TomlDropTableDef>(toml), resolver)

        // RSDropTable merges the inline rolls into its separate-roll section: the a/b roll twice
        // and the c roll once.
        val field = RSDropTable::class.java.getDeclaredField("separateRolls")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val section = field.get(table) as MultiChanceTable<Player, DropRollItem>
        assertEquals(3, section.tableEntries.size)
    }

    @Test
    fun `writer round-trips rolls and omits one roll`() {
        val def = mapper.readValue<TomlDropTableDef>(toml)
        val written = DropTableTomlWriter.write(def)

        assertEquals(2, Regex("^rolls = 2$", RegexOption.MULTILINE).findAll(written).count())
        assertFalse(Regex("^rolls = 1$", RegexOption.MULTILINE).containsMatchIn(written))
        assertEquals(def, mapper.readValue<TomlDropTableDef>(written))
    }
}
