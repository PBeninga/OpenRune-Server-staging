package org.rsmod.content.leagues.demonicpacts.effects.magic

import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.leagues.demonicpacts.PactsActiveFor
import org.rsmod.content.leagues.demonicpacts.effects.OPPONENT_HITPOINTS
import org.rsmod.game.entity.Npc

/**
 * The magic pacts: I1–I4, L1–L8, F7 and F8. In PvP the speed and damage pacts apply (L4 to the
 * max hit only); I3, whose cost a proc takes, and the hit procs (I4, L2, L3) don't.
 */
class MagicPactsTest {
    @Test
    fun GameTestState.`I1 adds 7 percent damage to air spells per active prayer`() =
        magicPactTest {
            val npc = target()
            own("I1")
            val baseline = spellRange(npc, WIND)
            setVarBit(ALL_PRAYERS, 0b11)

            assertEquals(baseline.last * 114 / 100, spellRange(npc, WIND).last)
            assertEquals(baseline, spellRange(npc, WATER))
        }

    @Test
    fun GameTestState.`I2 adds up to 20 percent damage to water spells by Hitpoints`() =
        magicPactTest {
            val npc = target()
            val baseline = spellRange(npc, WATER)
            own("I2")

            assertEquals(baseline.last * 120 / 100, spellRange(npc, WATER).last)
            setCurrentLevel("stat.hitpoints", 50)
            assertEquals(22, spellRange(npc, WATER).last)
            assertEquals(baseline, spellRange(npc, WIND))
        }

    @Test
    fun GameTestState.`I3 burns 6 percent of Hitpoints on a fire cast for twice that in damage`() =
        magicPactTest {
            val npc = target()
            val baseline = spellRange(npc, FIRE)
            own("I3")

            assertEquals(10..baseline.last + 10, spellRange(npc, FIRE))
            queueRolls(HIT, 25)
            cast(npc, FIRE)
            assertEquals(99 - 5, player.hitpoints)
            assertEquals(TARGET_HP - 25, npc.hitpoints)

            queueRolls(MISS)
            cast(npc, FIRE)
            assertEquals(99 - 10, player.hitpoints)
        }

    @Test
    fun GameTestState.`I3 never burns the last Hitpoint`() = magicPactTest {
        val npc = target()
        val baseline = spellRange(npc, FIRE)
        own("I3")
        setCurrentLevel("stat.hitpoints", 3)

        assertEquals(4..baseline.last + 4, spellRange(npc, FIRE))
        queueRolls(MISS)
        cast(npc, FIRE)
        assertEquals(1, player.hitpoints)

        assertEquals(baseline, spellRange(npc, FIRE))
        queueRolls(MISS)
        cast(npc, FIRE)
        assertEquals(1, player.hitpoints)
    }

    @Test
    fun GameTestState.`I4 earth hits lower the npc's Defence and Magic by 2`() = magicPactTest {
        val npc = target()
        npc.baseDefenceLvl = 20
        npc.defenceLvl = 20
        npc.baseMagicLvl = 3
        npc.magicLvl = 3
        own("I4")

        queueRolls(HIT, 5)
        cast(npc, EARTH)
        assertEquals(18, npc.defenceLvl)
        assertEquals(1, npc.magicLvl)

        queueRolls(HIT, 5)
        cast(npc, EARTH)
        assertEquals(16, npc.defenceLvl)
        assertEquals(0, npc.magicLvl)

        queueRolls(MISS)
        cast(npc, EARTH)
        queueRolls(HIT, 5)
        cast(npc, WIND)
        assertEquals(16, npc.defenceLvl)
    }

    @Test
    fun GameTestState.`L1 gives air spells a max hit chance per prayer bonus, doubled on weakness`() =
        magicPactTest {
            wear(Wearpos.Hat, "obj.blessedstar")
            wear(Wearpos.Torso, "obj.monkrobetop")
            val bonus = deps.wornBonuses.prayerBonus(player)
            assertTrue(bonus > 0) { "prayer bonus: $bonus" }
            val man = target()
            val wyvern = target(tiles = 3, npc = "npc.ancient_wyvern")
            own("L1")

            assertEquals(bonus.toDouble(), modifiers(man, WIND).maxHitChancePercent)
            assertEquals(bonus * 2.0, modifiers(wyvern, WIND).maxHitChancePercent)
            assertEquals(0.0, modifiers(man, WATER).maxHitChancePercent)
        }

    @Test
    fun GameTestState.`L2 water hits heal 60 percent of their damage`() = magicPactTest {
        val npc = target()
        own("L2")
        setCurrentLevel("stat.hitpoints", 50)

        queueRolls(HIT, 11)
        cast(npc, WATER)
        assertEquals(50 + 6, player.hitpoints)

        queueRolls(HIT, 11)
        cast(npc, WIND)
        assertEquals(56, player.hitpoints)
    }

    @Test
    fun GameTestState.`L3 fire hits burn the npc for 1 per stack every 4 ticks`() = magicPactTest {
        val npc = target()
        own("L3")

        queueRolls(HIT, 5)
        cast(npc, FIRE)
        assertEquals(1, deps.burns.stacks(player, npc))
        val afterHit = TARGET_HP - 5
        assertEquals(afterHit, npc.hitpoints)

        scope.advance(PactBurns.INTERVAL * PactBurns.DAMAGE_TICKS + 2)
        assertEquals(afterHit - PactBurns.DAMAGE_TICKS, npc.hitpoints)
        assertEquals(0, deps.burns.stacks(player, npc))

        scope.advance(PactBurns.INTERVAL * 2)
        assertEquals(afterHit - PactBurns.DAMAGE_TICKS, npc.hitpoints)
    }

    @Test
    fun GameTestState.`L3 burn stacks up to 5`() = magicPactTest {
        val npc = target()
        own("L3")
        repeat(7) { deps.burns.apply(player, npc) }
        assertEquals(PactBurns.MAX_STACKS, deps.burns.stacks(player, npc))

        scope.advance(PactBurns.INTERVAL + 2)
        assertEquals(TARGET_HP - PactBurns.MAX_STACKS, npc.hitpoints)
    }

    @Test
    fun GameTestState.`L3 fire hits bounce to the 2 closest npcs in multi-combat`() =
        magicPactTest {
            val coords = multiCombatCoords()
            placeAt(coords)
            val npc = target(tiles = 2, from = coords)
            val first = target(tiles = 3, from = coords)
            val second = target(tiles = 4, from = coords)
            val far = target(tiles = 5, from = coords)
            own("L3")
            assertEquals(listOf(first, second), deps.procs.bounceTargets(player, npc))

            queueRolls(HIT, 5, HIT, 4, HIT, 3)
            cast(npc, FIRE)

            assertEquals(2, recorder.dealt(HitSource.Chain).size)
            assertEquals(TARGET_HP - 5, npc.hitpoints)
            assertEquals(TARGET_HP - 4, first.hitpoints)
            assertEquals(TARGET_HP - 3, second.hitpoints)
            assertEquals(TARGET_HP, far.hitpoints)
        }

    @Test
    fun GameTestState.`L3 doesn't bounce outside multi-combat or on a splash`() = magicPactTest {
        val npc = target(tiles = 2)
        target(tiles = 3)
        own("L3")
        assertEquals(emptyList<Any>(), deps.procs.bounceTargets(player, npc))

        val coords = multiCombatCoords()
        placeAt(coords)
        val multiNpc = target(tiles = 2, from = coords)
        target(tiles = 3, from = coords)
        queueRolls(MISS)
        cast(multiNpc, FIRE)
        assertTrue(recorder.dealt(HitSource.Chain).isEmpty())
        assertEquals(0, deps.burns.stacks(player, multiNpc))
    }

    @Test
    fun GameTestState.`L4 earth spells deal 1 more damage per 12 Defence levels`() =
        magicPactTest {
            val npc = target()
            val baseline = spellRange(npc, EARTH)
            own("L4")

            assertEquals(8..baseline.last + 8, spellRange(npc, EARTH))
            setCurrentLevel("stat.defence", 60)
            assertEquals(5..baseline.last + 5, spellRange(npc, EARTH))
            assertEquals(baseline, spellRange(npc, FIRE))
        }

    @Test
    fun GameTestState.`L5 makes smoke spells count as air for I1`() = magicPactTest {
        val npc = target()
        own("I1")
        setVarBit(ALL_PRAYERS, 0b11)
        val without = spellRange(npc, SMOKE)
        own("I1", "L5")
        assertEquals(without.last * 114 / 100, spellRange(npc, SMOKE).last)
    }

    @Test
    fun GameTestState.`L5 makes smoke spells count as air for L1's weakness`() = magicPactTest {
        wear(Wearpos.Hat, "obj.blessedstar")
        val bonus = deps.wornBonuses.prayerBonus(player)
        val wyvern = target(tiles = 3, npc = "npc.ancient_wyvern")
        own("L1")
        assertEquals(0.0, modifiers(wyvern, SMOKE).maxHitChancePercent)
        own("L1", "L5")
        assertEquals(bonus * 2.0, modifiers(wyvern, SMOKE).maxHitChancePercent)
    }

    @Test
    fun GameTestState.`L6 makes ice spells count as water for I2 and L2`() = magicPactTest {
        val npc = target()
        own("I2", "L2")
        val without = spellRange(npc, ICE)
        own("I2", "L2", "L6")
        assertEquals(without.last * 120 / 100, spellRange(npc, ICE).last)

        setCurrentLevel("stat.hitpoints", 50)
        queueRolls(HIT, 10)
        cast(npc, ICE)
        assertEquals(50 + 6, player.hitpoints)
    }

    @Test
    fun GameTestState.`L7 makes blood spells count as fire for I3 and L3`() = magicPactTest {
        val npc = target()
        own("I3", "L3")
        val without = spellRange(npc, BLOOD)
        own("I3", "L3", "L7")
        assertEquals(10..without.last + 10, spellRange(npc, BLOOD))

        queueRolls(HIT, 12)
        cast(npc, BLOOD)
        assertEquals(1, deps.burns.stacks(player, npc))
    }

    @Test
    fun GameTestState.`L8 makes shadow spells count as earth for I4 and L4`() = magicPactTest {
        val npc = target()
        npc.baseDefenceLvl = 20
        npc.defenceLvl = 20
        own("I4", "L4")
        val without = spellRange(npc, SHADOW)
        queueRolls(HIT, 5)
        cast(npc, SHADOW)
        assertEquals(20, npc.defenceLvl)

        own("I4", "L4", "L8")
        assertEquals(8..without.last + 8, spellRange(npc, SHADOW))
        queueRolls(HIT, 9)
        cast(npc, SHADOW)
        assertEquals(18, npc.defenceLvl)
    }

    @Test
    fun GameTestState.`F7 makes spellbook spells 2 ticks faster, not below 2`() = magicPactTest {
        val npc = target()
        own("F7")

        assertEquals(SPELL_ATTACK_RATE - 2, attackDelay(npc, WIND, SPELL_ATTACK_RATE))
        assertEquals(2, attackDelay(npc, WIND, 3))
        wear(Wearpos.RightHand, "obj.tots_charged")
        assertEquals(STAFF_ATTACK_RATE, attackDelay(npc, null, STAFF_ATTACK_RATE))
    }

    @Test
    fun GameTestState.`F8 makes powered staves 3 ticks faster and one-handed ones lose 8`() =
        magicPactTest {
            val npc = target()
            wear(Wearpos.RightHand, "obj.tots_charged")
            val oneHanded = staffMaxHit(npc)
            wear(Wearpos.RightHand, "obj.tumekens_shadow")
            val twoHanded = staffMaxHit(npc)
            own("F8")

            assertEquals(1, attackDelay(npc, null, STAFF_ATTACK_RATE))
            assertEquals(twoHanded, staffMaxHit(npc))
            wear(Wearpos.RightHand, "obj.tots_charged")
            assertEquals(1, attackDelay(npc, null, STAFF_ATTACK_RATE))
            assertEquals(oneHanded - 8, staffMaxHit(npc))
            assertEquals(SPELL_ATTACK_RATE, attackDelay(npc, WIND, SPELL_ATTACK_RATE))
        }

    @Test
    fun GameTestState.`in PvP F7, I1 and L4 apply, and I3 and the hit procs don't`() =
        magicPactTest {
            val opponent = opponent()
            val wind = spellRange(opponent, WIND)
            val earth = spellRange(opponent, EARTH)
            val fire = spellRange(opponent, FIRE)
            own("F7", "I1", "I3", "L2", "L3", "L4")
            setVarBit(ALL_PRAYERS, 0b11)

            assertEquals(SPELL_ATTACK_RATE - 2, attackDelay(opponent, WIND, SPELL_ATTACK_RATE))
            assertEquals(wind.last * 114 / 100, spellRange(opponent, WIND).last)
            assertEquals(earth.first..earth.last + 8, spellRange(opponent, EARTH))
            assertEquals(fire, spellRange(opponent, FIRE))

            queueRolls(HIT, 5)
            cast(opponent, FIRE)
            assertEquals(99, player.hitpoints)
            assertEquals(OPPONENT_HITPOINTS - 5, opponent.hitpoints)
            assertTrue(recorder.dealt.isEmpty())
        }

    @Test
    fun GameTestState.`an inactive player gets none of the magic pacts`() =
        magicPactTest(activation = PactsActiveFor()) {
            val npc = target()
            val baseline = spellRange(npc, FIRE)
            own("I3", "F7", "L3")

            assertEquals(baseline, spellRange(npc, FIRE))
            assertEquals(SPELL_ATTACK_RATE, attackDelay(npc, WIND, SPELL_ATTACK_RATE))
            queueRolls(HIT, 5)
            cast(npc, FIRE)
            assertEquals(99, player.hitpoints)
            assertEquals(0, deps.burns.stacks(player, npc))
        }

    private fun MagicPactsTestScope.staffMaxHit(npc: Npc): Int =
        deps.manager.calculateStaffMaxHit(player, npc, STAFF_BASE_MAX_HIT, 1.0)

    private companion object {
        const val ALL_PRAYERS = "varbit.prayer_allactive"
        const val STAFF_BASE_MAX_HIT = 30
        const val WIND = "obj.41_wind_blast"
        const val WATER = "obj.47_water_blast"
        const val EARTH = "obj.53_earth_blast"
        const val FIRE = "obj.59_fire_blast"
        const val SMOKE = "obj.50_smoke_rush"
        const val ICE = "obj.58_ice_rush"
        const val BLOOD = "obj.56_blood_rush"
        const val SHADOW = "obj.52_shadow_rush"
    }
}
