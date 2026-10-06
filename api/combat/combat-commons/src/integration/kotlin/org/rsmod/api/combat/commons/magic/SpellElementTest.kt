package org.rsmod.api.combat.commons.magic

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.enums.SpellbookEnums.ancient_spellbook
import org.rsmod.api.enums.SpellbookEnums.normal_spellbook
import org.rsmod.api.testing.GameTestState

class SpellElementTest {
    @Test
    fun GameTestState.`every standard combat spell has its element`() = runGameTest {
        val elements = combatSpells(normal_spellbook.filterValuesNotNull().values)
        val expected =
            mapOf(
                "obj.01_wind_strike" to SpellElement.Air,
                "obj.05_water_strike" to SpellElement.Water,
                "obj.09_earth_strike" to SpellElement.Earth,
                "obj.13_fire_strike" to SpellElement.Fire,
                "obj.17_wind_bolt" to SpellElement.Air,
                "obj.23_water_bolt" to SpellElement.Water,
                "obj.29_earth_bolt" to SpellElement.Earth,
                "obj.35_fire_bolt" to SpellElement.Fire,
                "obj.41_wind_blast" to SpellElement.Air,
                "obj.47_water_blast" to SpellElement.Water,
                "obj.53_earth_blast" to SpellElement.Earth,
                "obj.59_fire_blast" to SpellElement.Fire,
                "obj.62_wind_wave" to SpellElement.Air,
                "obj.65_water_wave" to SpellElement.Water,
                "obj.70_earth_wave" to SpellElement.Earth,
                "obj.75_fire_wave" to SpellElement.Fire,
                "obj.81_wind_surge" to SpellElement.Air,
                "obj.85_water_surge" to SpellElement.Water,
                "obj.90_earth_surge" to SpellElement.Earth,
                "obj.95_fire_surge" to SpellElement.Fire,
            )
        assertEquals(expected, elements.filterValues { it != null })

        val withoutElement = elements.filterValues { it == null }.keys
        for (spell in NON_ELEMENTAL_STANDARD) {
            assertTrue(spell in withoutElement, "$spell should be a combat spell without element")
        }
    }

    @Test
    fun GameTestState.`every ancient combat spell has its element`() = runGameTest {
        val elements = combatSpells(ancient_spellbook.filterValuesNotNull().values)
        val expected =
            mapOf(
                "obj.50_smoke_rush" to SpellElement.Smoke,
                "obj.52_shadow_rush" to SpellElement.Shadow,
                "obj.56_blood_rush" to SpellElement.Blood,
                "obj.58_ice_rush" to SpellElement.Ice,
                "obj.62_smoke_burst" to SpellElement.Smoke,
                "obj.64_shadow_burst" to SpellElement.Shadow,
                "obj.68_blood_burst" to SpellElement.Blood,
                "obj.70_ice_burst" to SpellElement.Ice,
                "obj.74_smoke_blitz" to SpellElement.Smoke,
                "obj.76_shadow_blitz" to SpellElement.Shadow,
                "obj.80_blood_blitz" to SpellElement.Blood,
                "obj.82_ice_blitz" to SpellElement.Ice,
                "obj.86_smoke_barrage" to SpellElement.Smoke,
                "obj.88_shadow_barrage" to SpellElement.Shadow,
                "obj.92_blood_barrage" to SpellElement.Blood,
                "obj.94_ice_barrage" to SpellElement.Ice,
            )
        assertEquals(expected, elements)
    }

    @Test
    fun `ancient elements count as their standard base element`() {
        assertEquals(SpellElement.Air, SpellElement.Smoke.base)
        assertEquals(SpellElement.Water, SpellElement.Ice.base)
        assertEquals(SpellElement.Fire, SpellElement.Blood.base)
        assertEquals(SpellElement.Earth, SpellElement.Shadow.base)
        for (element in listOf(SpellElement.Air, SpellElement.Water, SpellElement.Earth)) {
            assertEquals(element, element.base)
            assertTrue(element.isStandard)
        }
        assertTrue(SpellElement.Smoke.isAncient)
        assertFalse(SpellElement.Fire.isAncient)
    }

    @Test
    fun GameTestState.`elemental weakness is read from npc params`() = runGameTest {
        assertEquals(ElementalWeakness(SpellElement.Water, 10), ElementalWeakness.of(npcType("npc.imp")))
        assertEquals(
            ElementalWeakness(SpellElement.Fire, 50),
            ElementalWeakness.of(npcType("npc.spider")),
        )
        assertEquals(
            ElementalWeakness(SpellElement.Earth, 25),
            ElementalWeakness.of(npcType("npc.giant_frog_nodrops")),
        )
        assertEquals(
            ElementalWeakness(SpellElement.Air, 35),
            ElementalWeakness.of(npcType("npc.ancient_wyvern")),
        )
        assertNull(ElementalWeakness.of(npcType("npc.man")))
    }

    @Test
    fun GameTestState.`ancient spells only hit a weakness when counted as their base`() =
        runGameTest {
            val imp = npcType("npc.imp")
            assertTrue(ElementalWeakness.isWeakTo(imp, SpellElement.Water))
            assertFalse(ElementalWeakness.isWeakTo(imp, SpellElement.Ice))
            assertTrue(ElementalWeakness.isWeakTo(imp, SpellElement.Ice, ancientAsBase = true))
            assertFalse(ElementalWeakness.isWeakTo(imp, SpellElement.Fire, ancientAsBase = true))
            assertFalse(ElementalWeakness.isWeakTo(npcType("npc.man"), SpellElement.Air))
        }

    private fun combatSpells(spells: Collection<ItemServerType>): Map<String, SpellElement?> =
        spells
            .filter { it.paramOrNull(BaseParams.spell_type) == MagicSpellType.Combat.id }
            .associate { RSCM.getReverseMapping(RSCMType.OBJ, it.id) to SpellElement.of(it) }

    private companion object {
        val NON_ELEMENTAL_STANDARD =
            listOf(
                "obj.20_bind",
                "obj.39_crumble_undead",
                "obj.50_iban_blast",
                "obj.50_magic_dart",
                "obj.60_saradomin_strike",
                "obj.60_claws_of_guthix",
                "obj.60_flames_of_zamorak",
            )
    }
}
