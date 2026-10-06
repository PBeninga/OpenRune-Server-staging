package org.rsmod.content.leagues.demonicpacts.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.leagues.demonicpacts.effects.defence.DefencePactEffects
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects
import org.rsmod.content.leagues.demonicpacts.tree.PactTreeLoader

/**
 * Maps the tree's effect ids to their implementations. Each effect batch adds its ids object to
 * [implemented] and its nodes to [nodesByBatch]; once every batch has landed, [implemented] covers
 * all 73 effect ids of the tree.
 */
class PactEffectCoverageTest {
    private val implemented: Map<String, Set<Int>> =
        mapOf(
            "defence" to DefencePactEffects.ids,
            "magic" to MagicPactEffects.ids,
            "melee" to MeleePactEffects.ids,
            "ranged" to RangedPactEffects.ids,
            "resources" to ResourcePactEffects.ids,
            "stats" to StatPactEffects.ids,
        )

    private val nodesByBatch: Map<String, Set<String>> =
        mapOf(
            "defence" to setOf("D1", "J1", "M1", "G1", "J8", "F2", "F13", "H9", "I6"),
            "magic" to
                setOf(
                    "I1", "I2", "I3", "I4", "L1", "L2", "L3", "L4", "L5", "L6", "L7", "L8", "F7",
                    "F8",
                ),
            "melee" to
                setOf(
                    "D2", "D3", "J3", "M3", "G4", "M2", "G8", "G5", "G10", "G6", "G7", "B3", "J4",
                    "D4", "M4", "J2", "H4",
                ),
            "ranged" to
                setOf(
                    "B2", "K9", "K10", "E1", "E2", "E3", "K3", "G9", "N9", "N4", "N5", "H6",
                    "K1", "K2", "K6", "N8", "K4", "K8", "N6", "N7", "H1",
                ),
            "resources" to
                setOf(
                    "AA", "BA", "BB", "BC", "F3", "H2", "J6", "B1", "C1", "C2", "C3", "C4", "F5",
                    "F6", "F9", "F10", "G2",
                ),
            "stats" to
                setOf(
                    "CA", "CB", "CC", "DB", "DC", "EB", "F1", "G3", "H3", "FA", "FB", "FC", "FD",
                    "IA", "IB", "IC", "ID", "GA", "GB", "GC", "GD", "JA", "JB", "JC", "JD", "HA",
                    "HB", "HC", "HD", "KA", "KB", "KC", "N1", "N2", "N3", "H7", "H10", "I5", "I7",
                    "J5", "J7", "J9", "K5", "K7", "DA", "EA", "EC", "F11", "F12", "G11", "G12",
                    "H5", "H8", "F4",
                ),
        )

    @Test
    fun GameTestState.`every implemented effect id is used by the tree`() = runGameTest {
        val treeIds = PactTreeLoader.load().nodes.map { it.effect.id }.toSet()
        for ((batch, ids) in implemented) {
            assertTrue(treeIds.containsAll(ids)) { "$batch: ${ids - treeIds} not in the tree" }
        }
    }

    @Test
    fun `no effect id is implemented twice`() {
        val all = implemented.values.flatten()
        assertEquals(all.size, all.toSet().size) { "duplicates: $implemented" }
    }

    @Test
    fun GameTestState.`each batch implements exactly the effects of its nodes`() = runGameTest {
        val tree = PactTreeLoader.load()
        for ((batch, ids) in nodesByBatch) {
            val effects = ids.map { tree[it].effect.id }.toSet()
            assertEquals(implemented.getValue(batch), effects) { batch }
        }
    }
}
