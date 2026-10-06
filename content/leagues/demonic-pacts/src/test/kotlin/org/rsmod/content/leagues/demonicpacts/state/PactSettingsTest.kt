package org.rsmod.content.leagues.demonicpacts.state

import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.api.server.config.ServerConfigLoader

class PactSettingsTest {
    @TempDir lateinit var dir: Path

    @Test
    fun `a config without the section leaves pacts off with the Jagex numbers`() {
        assertEquals(PactSettings(), settings(""))
        assertEquals(PactSettings(enabled = false, 2, 2, 40), settings(""))
    }

    @Test
    fun `enabled alone keeps the Jagex numbers`() {
        val yaml =
            """
            gameplay:
              demonic-pacts:
                enabled: true
            """
        assertEquals(PactSettings(enabled = true), settings(yaml))
    }

    @Test
    fun `the section's numbers override the defaults`() {
        val yaml =
            """
            gameplay:
              demonic-pacts:
                enabled: true
                starting-points: 0
                free-resets: 5
                spend-cap: 60
            """
        val expected = PactSettings(true, startingPoints = 0, freeResets = 5, spendCap = 60)
        assertEquals(expected, settings(yaml))
    }

    @Test
    fun `game example yml keeps pacts off with the Jagex numbers`() {
        val example = Path.of("../../../game.example.yml")
        val config = ServerConfigLoader().load(example)
        assertEquals(PactSettings(enabled = false), PactSettings.from(config.gameplay.demonicPacts))
    }

    private fun settings(gameplay: String): PactSettings {
        val file = dir.resolve("game.yml")
        file.writeText(HEADER + gameplay.trimIndent())
        val config = ServerConfigLoader().load(file)
        return PactSettings.from(config.gameplay.demonicPacts)
    }

    private companion object {
        val HEADER =
            """
            name: "test"
            game-port: 43594
            revision: 241
            environment: "TEST"
            world: 255

            """
                .trimIndent() + "\n"
    }
}
