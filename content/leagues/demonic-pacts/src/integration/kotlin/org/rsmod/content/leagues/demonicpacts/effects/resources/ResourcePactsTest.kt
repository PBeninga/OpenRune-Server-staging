package org.rsmod.content.leagues.demonicpacts.effects.resources

import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.magic.SpellElement
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ResourceKind
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.defenceLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.leagues.demonicpacts.PactsActiveFor
import org.rsmod.content.leagues.demonicpacts.effects.registerOpponent
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.REGENERATE
import org.rsmod.content.leagues.demonicpacts.state.PactEffectVarbits
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj

class ResourcePactsTest {
    @Test
    fun GameTestState.`each Regenerate node refunds at its chance over many uses`() =
        resourcePactTest {
            val arrow = objType("obj.rune_arrow")
            for ((node, chance) in REGENERATE_NODES) {
                own(node)
                assertEquals(chance, deps.pacts.effects(player)[REGENERATE]) { node }
                var refunded = 0
                repeat(USES) {
                    val kind = ResourceKind.entries[it % ResourceKind.entries.size]
                    if (deps.pipeline.isRefunded(player, kind, arrow, 1)) {
                        refunded++
                    }
                }
                val rate = refunded * 100.0 / USES
                assertTrue(rate in (chance - TOLERANCE)..(chance + TOLERANCE)) {
                    "$node: $rate% refunded, expected $chance%"
                }
            }
        }

    @Test
    fun GameTestState.`Regenerate nodes stack, show their sum and cap at 100 percent`() =
        resourcePactTest {
            own("AA", "BA", "BB", "BC", "F3", "H2", "J6")
            assertEquals(155, deps.pacts.effects(player)[REGENERATE])
            assertEquals(155, player.vars[PactEffectVarbits.VARBITS.getValue(REGENERATE)])
            val arrow = objType("obj.rune_arrow")
            repeat(50) { assertTrue(deps.pipeline.isRefunded(player, ResourceKind.Ammo, arrow, 1)) }
        }

    @Test
    fun GameTestState.`Regenerate saves arrows shot and runes of any spell`() = resourcePactTest {
        val npc = target()
        player.worn[Wearpos.Quiver.slot] = InvObj("obj.rune_arrow", 1_000)
        giveRunes("obj.airrune", "obj.lawrune", "obj.earthrune")
        val runesPerCast = runesHeld()
        castOutsideCombat(TELEPORT)
        val teleportCost = runesPerCast - runesHeld()

        own("AA")
        val arrow = objType("obj.rune_arrow")
        repeat(USES) { deps.ammo.useQuiverAmmo(player, arrow, npc.coords, 1, dropChance = NEVER_DROPS) }
        val saved = player.worn[Wearpos.Quiver.slot]!!.count - (1_000 - USES)
        assertTrue(saved in USES * 45 / 100..USES * 55 / 100) { "saved $saved of $USES arrows" }

        val before = runesHeld()
        repeat(CASTS) { castOutsideCombat(TELEPORT) }
        val spent = before - runesHeld()
        val full = teleportCost * CASTS
        assertTrue(spent in full * 40 / 100..full * 60 / 100) { "spent $spent of $full runes" }
    }

    @Test
    fun GameTestState.`an inactive player never Regenerates`() =
        resourcePactTest(PactsActiveFor()) {
            own("AA", "F3", "H2")
            val arrow = objType("obj.rune_arrow")
            repeat(20) { assertFalse(deps.pipeline.isRefunded(player, ResourceKind.Ammo, arrow, 1)) }
        }

    @Test
    fun GameTestState.`B1 boosts Magic by 1 per Regenerating cast, at most 10`() =
        resourcePactTest {
            own("B1", *ALWAYS_REGENERATE)
            val npc = target()
            giveRunes("obj.airrune", "obj.mindrune")

            castOn(npc, "obj.01_wind_strike")
            assertEquals(100, player.magicLvl)
            repeat(11) { castOn(npc, "obj.01_wind_strike") }
            assertEquals(109, player.magicLvl)
            assertEquals(10, deps.boosts.amount(player, PactRegenerateBoost.Magic))
        }

    @Test
    fun GameTestState.`B1 boost lasts 30 ticks after its latest trigger`() = resourcePactTest {
        own("B1", *ALWAYS_REGENERATE)
        val npc = target()
        giveRunes("obj.airrune", "obj.mindrune")

        castOn(npc, "obj.01_wind_strike")
        scope.advance(20)
        castOn(npc, "obj.01_wind_strike")
        scope.advance(22)
        assertEquals(101, player.magicLvl)
        scope.advance(6)
        assertEquals(99, player.magicLvl)
        assertEquals(0, deps.boosts.amount(player, PactRegenerateBoost.Magic))
    }

    @Test
    fun GameTestState.`B1 needs a combat spell`() = resourcePactTest {
        own("B1", *ALWAYS_REGENERATE)
        giveRunes("obj.airrune", "obj.mindrune")
        castOutsideCombat("obj.01_wind_strike")
        assertEquals(99, player.magicLvl)
    }

    @Test
    fun GameTestState.`C1 restores 1 Prayer per air rune at a 15 percent chance`() =
        resourcePactTest {
            own("C1", *ALWAYS_REGENERATE)
            val npc = target()
            giveRunes("obj.airrune", "obj.mindrune", "obj.chaosrune")
            setCurrentLevel("stat.prayer", 50)

            // 15% = 1,500 basis points: 1,499 restores, 1,500 doesn't.
            scope.random.next = 1_499
            spendRunesOn(npc, "obj.01_wind_strike")
            assertEquals(51, player.prayerLvl)
            scope.random.next = 1_500
            spendRunesOn(npc, "obj.01_wind_strike")
            assertEquals(51, player.prayerLvl)

            // Wind bolt spends 2 air runes: one roll each.
            queueRolls(0, 0)
            spendRunesOn(npc, "obj.17_wind_bolt")
            assertEquals(53, player.prayerLvl)
        }

    @Test
    fun GameTestState.`C2 heals 1 per water rune Regenerated`() = resourcePactTest {
        own("C2", *ALWAYS_REGENERATE)
        val npc = target()
        giveRunes("obj.airrune", "obj.waterrune", "obj.mindrune", "obj.chaosrune")
        setCurrentLevel("stat.hitpoints", 50)

        spendRunesOn(npc, "obj.05_water_strike")
        assertEquals(51, player.hitpoints)
        spendRunesOn(npc, "obj.23_water_bolt")
        assertEquals(53, player.hitpoints)
        spendRunesOn(npc, "obj.01_wind_strike")
        assertEquals(53, player.hitpoints)
    }

    @Test
    fun GameTestState.`C3 adds 1 damage per fire rune to the next spell hit that lands`() =
        resourcePactTest {
            own("C3", *ALWAYS_REGENERATE)
            val npc = target()
            giveRunes("obj.airrune", "obj.firerune", "obj.mindrune")

            castOn(npc, "obj.13_fire_strike", accuracy = MISS)
            assertEquals(3, deps.modifiers.fireDamage(player))
            val modifiers = magicModifiers(npc, "obj.01_wind_strike")
            assertEquals(3, modifiers.maxHitFlat)
            assertEquals(3, modifiers.minHitFlat)

            val damage = castOn(npc, "obj.01_wind_strike")
            assertTrue(damage >= 3) { "damage $damage" }
            assertEquals(0, deps.modifiers.fireDamage(player))
            assertEquals(0, magicModifiers(npc, "obj.01_wind_strike").maxHitFlat)
        }

    @Test
    fun GameTestState.`C4 boosts Defence by earth runes, at most 20 percent, for 30 ticks`() =
        resourcePactTest {
            own("C4", *ALWAYS_REGENERATE)
            val npc = target()
            giveRunes("obj.airrune", "obj.earthrune", "obj.mindrune")

            spendRunesOn(npc, "obj.09_earth_strike")
            assertEquals(101, player.defenceLvl)
            repeat(10) { spendRunesOn(npc, "obj.09_earth_strike") }
            assertEquals(118, player.defenceLvl)

            scope.advance(31)
            assertEquals(99, player.defenceLvl)
        }

    @Test
    fun GameTestState.`in PvP only the B1 and C4 stat boosts trigger`() = resourcePactTest {
        own("B1", "C1", "C2", "C3", "C4", *ALWAYS_REGENERATE)
        val npc = target()
        val opponent = scope.registerOpponent(RESOURCE_TEST_COORDS.translateX(2))
        giveRunes("obj.airrune", "obj.earthrune", "obj.waterrune", "obj.firerune", "obj.mindrune")
        setCurrentLevel("stat.prayer", 50)
        setCurrentLevel("stat.hitpoints", 50)

        // A C1 roll of 0 would restore Prayer.
        scope.random.next = 0
        spendRunesOn(opponent, "obj.09_earth_strike")
        assertEquals(100, player.magicLvl)
        assertEquals(101, player.defenceLvl)
        assertEquals(50, player.prayerLvl)

        spendRunesOn(opponent, "obj.05_water_strike")
        assertEquals(50, player.hitpoints)
        spendRunesOn(opponent, "obj.13_fire_strike")
        assertEquals(0, deps.modifiers.fireDamage(player))

        // C3's damage, stored by a cast on an npc, waits for the next npc hit.
        spendRunesOn(npc, "obj.13_fire_strike")
        assertEquals(3, deps.modifiers.fireDamage(player))
        assertEquals(3, magicModifiers(npc, "obj.01_wind_strike").maxHitFlat)
        assertEquals(0, magicModifiers(opponent, "obj.01_wind_strike").maxHitFlat)
    }

    @Test
    fun GameTestState.`F5, F6, F9 and F10 add a rune when a powered staff Regenerates a charge`() =
        resourcePactTest {
            own("F5", "F6", "F9", "F10", *ALWAYS_REGENERATE)
            val npc = target()
            player.righthand = InvObj("obj.tumekens_shadow", vars = 100)

            regenerateCharge(npc)
            for (rune in PactRuneElements.elementalRunes.values) {
                assertEquals(1, player.inv.count(rune)) { rune }
            }
            assertEquals(100, deps.charges.getWeaponCharges(player, SHADOW_CHARGES))
        }

    @Test
    fun GameTestState.`F5 water runes count for C2 and B1`() = resourcePactTest {
        own("F5", "C2", "B1", *ALWAYS_REGENERATE)
        val npc = target()
        player.righthand = InvObj("obj.tumekens_shadow", vars = 100)
        setCurrentLevel("stat.hitpoints", 50)

        regenerateCharge(npc)
        assertEquals(1, player.inv.count("obj.waterrune"))
        assertEquals(51, player.hitpoints)
        assertEquals(100, player.magicLvl)
    }

    @Test
    fun GameTestState.`F5 needs a Regenerated charge`() = resourcePactTest {
        own("F5")
        val npc = target()
        player.righthand = InvObj("obj.tumekens_shadow", vars = 100)
        regenerateCharge(npc)
        assertEquals(0, player.inv.count("obj.waterrune"))
        assertEquals(99, deps.charges.getWeaponCharges(player, SHADOW_CHARGES))
    }

    @Test
    fun GameTestState.`G2 makes 20 percent of special attacks free`() = resourcePactTest {
        own("G2")
        setVarp("varp.sa_energy", 1_000)
        // 20% = 2,000 basis points: 1,999 is free, 2,000 is not.
        scope.random.next = 1_999
        deps.energy.takeSpecialEnergy(player, 250)
        assertEquals(1_000, player.vars["varp.sa_energy"])
        scope.random.next = 2_000
        deps.energy.takeSpecialEnergy(player, 250)
        assertEquals(750, player.vars["varp.sa_energy"])
    }

    @Test
    fun GameTestState.`combination runes count as both their elements`() = resourcePactTest {
        val mist = PactRuneElements.of(objType("obj.mistrune"))
        assertEquals(setOf(SpellElement.Air, SpellElement.Water), mist)
        assertEquals(setOf(SpellElement.Fire), PactRuneElements.of(objType("obj.sunfirerune")))
        assertEquals(emptySet<SpellElement>(), PactRuneElements.of(objType("obj.mindrune")))
    }

    private fun ResourcePactsTestScope.giveRunes(vararg runes: String) {
        for ((slot, rune) in runes.withIndex()) {
            player.inv[slot] = InvObj(rune, 1_000)
        }
    }

    private fun ResourcePactsTestScope.runesHeld(): Int =
        RUNES.sumOf { player.inv.count(it) }

    private fun ResourcePactsTestScope.regenerateCharge(npc: Npc) {
        deps.pipeline.withAttack(player, npc, CombatStyle.Magic) {
            val result = deps.charges.attemptDetractWeapon(player, SHADOW_CHARGES)
            assertTrue(result is ObjChargeManager.Uncharge.Success) { "$result" }
        }
    }

    private companion object {
        const val USES = 2_000

        val REGENERATE_NODES: Map<String, Int> =
            mapOf("AA" to 50, "BA" to 5, "BB" to 5, "BC" to 5, "F3" to 30, "H2" to 30, "J6" to 30)
        const val CASTS = 100
        const val NEVER_DROPS = Int.MAX_VALUE
        const val TELEPORT = "obj.31_lumbridge_teleport"
        val RUNES = listOf("obj.airrune", "obj.lawrune", "obj.earthrune")
        const val TOLERANCE = 3.5
        const val SHADOW_CHARGES = "varobj.tumeken_charges"
    }
}
