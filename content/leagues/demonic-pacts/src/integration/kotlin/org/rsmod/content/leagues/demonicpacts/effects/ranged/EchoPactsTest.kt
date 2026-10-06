package org.rsmod.content.leagues.demonicpacts.effects.ranged

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.leagues.demonicpacts.effects.OPPONENT_HITPOINTS
import org.rsmod.map.CoordGrid

/**
 * The echo pacts B2, K9, K10, E1, E2, E3, K3 and G9, and N9's extra thrown target. None of them
 * works against players.
 */
class EchoPactsTest {
    @Test
    fun GameTestState.`B2 fires a ranged echo on 25% of Regenerated shots`() =
        echoOnRegenerateCase("B2", chancePercent = 25)

    @Test
    fun GameTestState.`K9 fires a ranged echo on 5% of Regenerated shots`() =
        echoOnRegenerateCase("K9", chancePercent = 5)

    @Test
    fun GameTestState.`K10 fires a ranged echo on 5% of Regenerated shots`() =
        echoOnRegenerateCase("K10", chancePercent = 5)

    @Test
    fun GameTestState.`echo chances on Regenerate stack`() = rangedPactTest {
        own("B2", "K9", "K10")
        bowAndArrows()
        val npc = target()
        assertEquals(35.0, modifiers(npc).procChancePercent(HitSource.Echo))
    }

    @Test
    fun GameTestState.`a shot whose ammo isn't Regenerated never echoes`() = rangedPactTest {
        own("B2", "K9", "K10")
        bowAndArrows()
        regenerate.ammoPercent = 0.0
        val npc = target()

        queueRolls(NO_DROP, HIT, 5, ECHO)
        rangedAttack(npc)

        assertTrue(recorder.dealt(HitSource.Echo).isEmpty())
        assertEquals(TARGET_HP - 5, npc.hitpoints)
    }

    @Test
    fun GameTestState.`echoes never fire against players`() = rangedPactTest {
        own("B2", "K9", "K10", "K3")
        bowAndArrows()
        val opponent = opponent()
        assertEquals(0.0, modifiers(opponent).procChancePercent(HitSource.Echo))
        assertEquals(0, modifiers(opponent).chainCap(HitSource.Echo))

        queueRolls(HIT, 5)
        pvpRangedAttack(opponent)

        assertEquals(OPPONENT_HITPOINTS - 5, opponent.hitpoints)
        assertTrue(recorder.rolled.isEmpty())
    }

    @Test
    fun GameTestState.`special attacks never echo`() = rangedPactTest {
        own("B2", "E2")
        wield("obj.xbows_crossbow_runite")
        quiver("obj.xbows_crossbow_bolts_runite")
        val npc = target()

        queueRolls(HIT, 5, ECHO)
        rangedAttack(npc, special = true)

        assertTrue(recorder.dealt(HitSource.Echo).isEmpty())
    }

    @Test
    fun GameTestState.`E1 makes a bow's echoes never miss`() = rangedPactTest {
        own("B2", "E1")
        bowAndArrows()
        val npc = target()
        val echo = modifiers(npc, source = HitSource.Echo)
        assertEquals(100.0, echo.defenceRollIgnorePercent)
        assertEquals(100.0, echo.maxAccuracyRollChancePercent)
        assertEquals(0.0, modifiers(npc).defenceRollIgnorePercent)

        queueRolls(HIT, 5, ECHO, MISS, 7)
        rangedAttack(npc)

        assertEquals(listOf(7), recorder.dealt(HitSource.Echo).map { it.damage })
        assertEquals(TARGET_HP - 12, npc.hitpoints)
    }

    @Test
    fun GameTestState.`E1 doesn't work with the Eclipse atlatl`() = rangedPactTest {
        own("B2", "E1")
        wield("obj.eclipse_atlatl")
        quiver("obj.atlatl_dart")
        val npc = target()
        assertEquals(0.0, modifiers(npc, source = HitSource.Echo).defenceRollIgnorePercent)
    }

    @Test
    fun GameTestState.`E2 adds 15% echo chance with a crossbow only`() = rangedPactTest {
        own("B2", "E2")
        val npc = target()
        wield("obj.xbows_crossbow_runite")
        assertEquals(40.0, modifiers(npc).procChancePercent(HitSource.Echo))
        bowAndArrows()
        assertEquals(25.0, modifiers(npc).procChancePercent(HitSource.Echo))
        wield("obj.heavy_ballista")
        assertEquals(40.0, modifiers(npc).procChancePercent(HitSource.Echo))
    }

    @Test
    fun GameTestState.`E3 gives a thrown weapon's echoes a 20% max hit chance`() = rangedPactTest {
        own("E3")
        wield("obj.rune_dart", count = 100)
        val npc = target()
        assertEquals(20.0, modifiers(npc, source = HitSource.Echo).maxHitChancePercent)
        assertEquals(0.0, modifiers(npc).maxHitChancePercent)
        wield("obj.eclipse_atlatl")
        assertEquals(20.0, modifiers(npc, source = HitSource.Echo).maxHitChancePercent)
        bowAndArrows()
        assertEquals(0.0, modifiers(npc, source = HitSource.Echo).maxHitChancePercent)
    }

    @Test
    fun GameTestState.`echoes don't chain without K3`() = rangedPactTest {
        own("B2")
        bowAndArrows()
        val npc = target()

        queueRolls(HIT, 5, ECHO, HIT, 3, ECHO)
        rangedAttack(npc)

        assertEquals(1, recorder.dealt(HitSource.Echo).size)
    }

    @Test
    fun GameTestState.`K3 chains echoes at half the chance, at most 4 times`() = rangedPactTest {
        own("B2", "K3")
        bowAndArrows()
        val npc = target()
        assertEquals(4, modifiers(npc).chainCap(HitSource.Echo))

        // 25% halves to 12.5%: a chain roll of 1,249 echoes again.
        queueRolls(HIT, 5, ECHO, HIT, 3)
        repeat(4) { queueRolls(1_249, HIT, 3) }
        queueRolls(ECHO)
        rangedAttack(npc)

        assertEquals(5, recorder.dealt(HitSource.Echo).size)
        assertEquals(4, recorder.rolled.maxOf { it.chainDepth })
        assertEquals(TARGET_HP - 5 - 5 * 3, npc.hitpoints)
    }

    @Test
    fun GameTestState.`K3 chained echoes fail above half the chance`() = rangedPactTest {
        own("B2", "K3")
        bowAndArrows()
        val npc = target()

        queueRolls(HIT, 5, ECHO, HIT, 3, 1_250)
        rangedAttack(npc)

        assertEquals(1, recorder.dealt(HitSource.Echo).size)
    }

    @Test
    fun GameTestState.`G9 gives two-handed melee hits a 5% echo chance`() = rangedPactTest {
        own("G9")
        wield("obj.saradomin_sword")
        val npc = target()
        assertEquals(5.0, modifiers(npc, CombatStyle.Melee).procChancePercent(HitSource.Echo))

        queueRolls(HIT, 6, 499, HIT, 4)
        meleeAttack(npc)

        assertEquals(listOf(4), recorder.dealt(HitSource.Echo).map { it.damage })
        assertEquals(TARGET_HP - 10, npc.hitpoints)

        queueRolls(HIT, 6, 500)
        meleeAttack(npc)
        assertEquals(1, recorder.dealt(HitSource.Echo).size)
    }

    @Test
    fun GameTestState.`G9 doesn't echo one-handed weapons or misses`() = rangedPactTest {
        own("G9")
        wield("obj.abyssal_whip")
        val npc = target()
        assertEquals(0.0, modifiers(npc, CombatStyle.Melee).procChancePercent(HitSource.Echo))

        wield("obj.saradomin_sword")
        queueRolls(MISS, ECHO)
        meleeAttack(npc)
        assertTrue(recorder.dealt(HitSource.Echo).isEmpty())
    }

    @Test
    fun GameTestState.`G9 echoes count as a bow, crossbow and thrown weapon`() = rangedPactTest {
        own("G9", "E1", "E2", "E3")
        wield("obj.saradomin_sword")
        val npc = target()
        assertEquals(20.0, modifiers(npc, CombatStyle.Melee).procChancePercent(HitSource.Echo))
        val echo = modifiers(npc, CombatStyle.Melee, HitSource.Echo)
        assertEquals(100.0, echo.defenceRollIgnorePercent)
        assertEquals(20.0, echo.maxHitChancePercent)
        assertEquals(0.0, modifiers(npc, CombatStyle.Melee).maxHitChancePercent)
    }

    @Test
    fun GameTestState.`N9 throws at a second nearby npc in multi-combat`() = rangedPactTest {
        own("N9")
        wield("obj.rune_dart", count = 100)
        val coords = multiCombatCoords()
        with(scope) { player.placeAt(coords) }
        val npc = target(tiles = 2, from = coords)
        val second = target(tiles = 3, from = coords)
        assertEquals(second, deps.extraTarget.findTarget(scope.player, npc))

        queueRolls(HIT, 5, HIT, 6)
        rangedAttack(npc)

        assertEquals(TARGET_HP - 5, npc.hitpoints)
        assertEquals(TARGET_HP - 6, second.hitpoints)
        val extra = recorder.dealt(HitSource.ExtraTarget).single()
        assertEquals(second, extra.target)
    }

    @Test
    fun GameTestState.`N9 needs a thrown weapon and multi-combat`() = rangedPactTest {
        own("N9")
        wield("obj.rune_dart", count = 100)
        val npc = target(tiles = 2)
        target(tiles = 3)
        assertNull(deps.extraTarget.findTarget(scope.player, npc))

        val coords = multiCombatCoords()
        with(scope) { player.placeAt(coords) }
        val multiNpc = target(tiles = 2, from = coords)
        target(tiles = 3, from = coords)
        assertNotNull(deps.extraTarget.findTarget(scope.player, multiNpc))
        bowAndArrows()
        queueRolls(HIT, 5, HIT, 6)
        rangedAttack(multiNpc)
        assertTrue(recorder.dealt(HitSource.ExtraTarget).isEmpty())
    }

    private fun GameTestState.echoOnRegenerateCase(id: String, chancePercent: Int) =
        rangedPactTest {
            own(id)
            bowAndArrows()
            val npc = target()
            assertEquals(chancePercent.toDouble(), modifiers(npc).procChancePercent(HitSource.Echo))
            val threshold = chancePercent * 100

            queueRolls(HIT, 5, threshold - 1, HIT, 7)
            rangedAttack(npc)
            assertEquals(listOf(7), recorder.dealt(HitSource.Echo).map { it.damage })
            assertEquals(TARGET_HP - 12, npc.hitpoints)

            queueRolls(HIT, 5, threshold)
            rangedAttack(npc)
            assertEquals(1, recorder.dealt(HitSource.Echo).size)
            assertEquals(TARGET_HP - 17, npc.hitpoints)
        }

    private fun RangedPactsTestScope.bowAndArrows() {
        wield("obj.magic_shortbow")
        quiver("obj.rune_arrow")
    }

    /** The first of a few known multi-combat spots, with open ground to the east. */
    private fun RangedPactsTestScope.multiCombatCoords(): CoordGrid {
        val found = MULTI_CANDIDATES.firstOrNull { deps.areaChecker.inArea(MULTIWAY, it) }
        return checkNotNull(found) { "None of $MULTI_CANDIDATES is in $MULTIWAY." }
    }

    private companion object {
        const val ECHO = 0
        const val NO_DROP = 1
        const val MULTIWAY = "area.multiway"

        val MULTI_CANDIDATES: List<CoordGrid> =
            listOf(
                CoordGrid(2882, 5310, 2),
                CoordGrid(3484, 9510, 2),
                CoordGrid(1665, 10050, 0),
                CoordGrid(3082, 3420, 0),
                CoordGrid(3150, 3800, 0),
            )
    }
}
