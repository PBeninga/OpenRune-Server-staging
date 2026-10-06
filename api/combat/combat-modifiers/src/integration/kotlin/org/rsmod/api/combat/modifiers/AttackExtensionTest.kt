package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.commons.styles.MagicAttackStyle
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.accuracy.AccuracyRollModifier
import org.rsmod.api.combat.formulas.maxhit.MaxHitModifier
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope

/**
 * The PF1 attack extensions (extra accuracy rolls, max accuracy rolls, max hit chance, strength
 * terms and prayer effect) change `PlayerAttackManager` rolls through the formula hooks.
 */
class AttackExtensionTest {
    @Test
    fun GameTestState.`extra accuracy rolls raise the rolled hit chance`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget()
            npc.defenceLvl = 99
            val type = MeleeAttackType.Stab
            val style = MeleeAttackStyle.Accurate
            val base = deps.accuracy.getMeleeHitChance(player, npc, type, style, type, 1.0)
            val expected = ModifierMath.modifyHitChance(base, 0, 0, 1, 0.0)
            assertTrue(expected > base)

            hooks.provider.attack = AttackModifiers(extraAccuracyRolls = 1)
            assertRollChance(expected) {
                deps.manager.rollMeleeAccuracy(player, npc, type, style, type, 1.0)
            }

            val ranged = RangedAttackType.Standard
            val rangedStyle = RangedAttackStyle.Accurate
            val rangedBase =
                deps.accuracy.getRangedHitChance(player, npc, ranged, rangedStyle, ranged, 1.0)
            assertRollChance(ModifierMath.modifyHitChance(rangedBase, 0, 0, 1, 0.0)) {
                deps.manager.rollRangedAccuracy(player, npc, ranged, rangedStyle, ranged, 1.0)
            }
        }
    }

    @Test
    fun GameTestState.`a max accuracy roll chance uses the final attack and defence rolls`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.attack", 60)
            val npc = spawnTarget()
            npc.defenceLvl = 99
            val type = MeleeAttackType.Crush
            val style = MeleeAttackStyle.Accurate
            val capture = RollCapture()
            val base = deps.accuracy.getMeleeHitChance(player, npc, type, style, type, 1.0, capture)
            val expected =
                ModifierMath.modifyHitChance(base, capture.attackRoll, capture.defenceRoll, 0, 50.0)
            assertTrue(expected > base)

            hooks.provider.attack = AttackModifiers(maxAccuracyRollChancePercent = 50.0)
            assertRollChance(expected) {
                deps.manager.rollMeleeAccuracy(player, npc, type, style, type, 1.0)
            }
            val spell = objType("obj.01_wind_strike")
            val book = Spellbook.Standard
            val spellCapture = RollCapture()
            val spellBase =
                deps.accuracy.getSpellHitChance(player, npc, spell, book, false, spellCapture)
            val spellExpected =
                ModifierMath.modifyHitChance(
                    spellBase,
                    spellCapture.attackRoll,
                    spellCapture.defenceRoll,
                    0,
                    50.0,
                )
            assertRollChance(spellExpected) {
                deps.manager.rollSpellAccuracy(player, npc, spell, book, sunfireRune = false)
            }
        }
    }

    @Test
    fun GameTestState.`a max hit chance rolls the max hit without a damage roll`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.strength", 99)
            val npc = spawnTarget()
            val manager = deps.manager
            val type = MeleeAttackType.Slash
            val style = MeleeAttackStyle.Aggressive
            val maxHit = manager.calculateMeleeMaxHit(player, npc, type, style, 1.0)

            hooks.provider.attack = AttackModifiers(maxHitChancePercent = 100.0)
            assertEquals(maxHit, manager.rollMeleeMaxHit(player, npc, type, style, 1.0))
            val spell = objType("obj.01_wind_strike")
            val book = Spellbook.Standard
            assertEquals(8, manager.rollSpellMaxHit(player, npc, spell, book, 8, 5, false))

            // 20% = 2,000 basis points: a roll of 1,999 maxes, 2,000 rolls damage as usual.
            hooks.provider.attack = AttackModifiers(maxHitChancePercent = 20.0)
            random.next = 1_999
            assertEquals(maxHit, manager.rollMeleeMaxHit(player, npc, type, style, 1.0))
            random.next = 2_000
            random.then = 3
            assertEquals(3, manager.rollMeleeMaxHit(player, npc, type, style, 1.0))
        }
    }

    @Test
    fun GameTestState.`strength terms feed the max hit formulas`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.strength", 99)
            setLevel("stat.ranged", 99)
            setLevel("stat.magic", 99)
            val npc = spawnTarget()
            val maxHits = deps.maxHits
            val manager = deps.manager
            val melee = MeleeAttackType.Slash
            val meleeStyle = MeleeAttackStyle.Aggressive
            val ranged = RangedAttackType.Standard
            val rangedStyle = RangedAttackStyle.Accurate
            val spell = objType("obj.01_wind_strike")
            val book = Spellbook.Standard
            val plus40 = TermModifier(strength = 40, magicDamage = 500)

            val meleeBase = maxHits.getMeleeMaxHit(player, npc, melee, meleeStyle, 1.0)
            val meleeExpected =
                maxHits.getMeleeMaxHit(player, npc, melee, meleeStyle, 1.0, false, plus40)
            val rangedExpected =
                maxHits.getRangedMaxHit(player, npc, ranged, rangedStyle, 1.0, 0, plus40)
            val spellExpected =
                maxHits.getSpellMaxHitRange(player, npc, spell, book, 20, 5, false, plus40)
            val staffExpected = maxHits.getStaffMaxHit(player, npc, 20, 1.0, plus40)
            assertTrue(meleeExpected > meleeBase)
            assertTrue(spellExpected.last > 20)

            hooks.provider.attack =
                AttackModifiers(
                    meleeStrengthFlat = 40,
                    rangedStrengthFlat = 40,
                    magicDamagePercent = 50.0,
                )
            assertEquals(
                meleeExpected,
                manager.calculateMeleeMaxHit(player, npc, melee, meleeStyle, 1.0),
            )
            assertEquals(
                rangedExpected,
                manager.calculateRangedMaxHit(player, npc, ranged, rangedStyle, 1.0, 0),
            )
            assertEquals(
                spellExpected,
                manager.calculateSpellMaxHit(player, npc, spell, book, 20, 5, false),
            )
            assertEquals(staffExpected, manager.calculateStaffMaxHit(player, npc, 20, 1.0))
            assertEquals(staffExpected, player.vars["varp.com_maxhit"])
        }
    }

    @Test
    fun GameTestState.`prayer effect scales offensive prayers for accuracy and damage`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.ranged", 99)
            player.setVarBit("varbit.prayer_rigour", 1)
            val npc = spawnTarget()
            npc.defenceLvl = 99
            val type = RangedAttackType.Standard
            val style = RangedAttackStyle.Accurate
            val prayer = PrayerModifier(30.0)

            val baseMax = deps.maxHits.getRangedMaxHit(player, npc, type, style, 1.0, 0)
            val expectedMax =
                deps.maxHits.getRangedMaxHit(player, npc, type, style, 1.0, 0, prayer)
            val baseChance = deps.accuracy.getRangedHitChance(player, npc, type, style, type, 1.0)
            val expectedChance =
                deps.accuracy.getRangedHitChance(player, npc, type, style, type, 1.0, prayer)
            assertTrue(expectedMax > baseMax)
            assertTrue(expectedChance > baseChance)

            hooks.provider.attack = AttackModifiers(prayerEffectPercent = 30.0)
            assertEquals(
                expectedMax,
                deps.manager.calculateRangedMaxHit(player, npc, type, style, 1.0, 0),
            )
            assertRollChance(expectedChance) {
                deps.manager.rollRangedAccuracy(player, npc, type, style, type, 1.0)
            }

            // Without the prayer active there is nothing to scale.
            player.setVarBit("varbit.prayer_rigour", 0)
            assertEquals(
                deps.maxHits.getRangedMaxHit(player, npc, type, style, 1.0, 0),
                deps.manager.calculateRangedMaxHit(player, npc, type, style, 1.0, 0),
            )
            val staffStyle = MagicAttackStyle.Accurate
            assertEquals(
                deps.accuracy.getStaffHitChance(player, npc, staffStyle, 1.0),
                deps.accuracy.getStaffHitChance(player, npc, staffStyle, 1.0, prayer),
            )
        }
    }

    @Test
    fun GameTestState.`an attack bonus term raises the attack roll of every style`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.attack", 70)
            setLevel("stat.ranged", 70)
            setLevel("stat.magic", 70)
            val npc = spawnTarget()
            npc.defenceLvl = 99
            val plus60 = AttackBonusModifier(60)
            hooks.provider.attack = AttackModifiers(attackBonusFlat = 60)

            val melee = MeleeAttackType.Stab
            val meleeStyle = MeleeAttackStyle.Accurate
            val meleeBase = deps.accuracy.getMeleeHitChance(player, npc, melee, meleeStyle, melee, 1.0)
            val meleeExpected =
                deps.accuracy.getMeleeHitChance(player, npc, melee, meleeStyle, melee, 1.0, plus60)
            assertTrue(meleeExpected > meleeBase)
            assertRollChance(meleeExpected) {
                deps.manager.rollMeleeAccuracy(player, npc, melee, meleeStyle, melee, 1.0)
            }

            val ranged = RangedAttackType.Standard
            val rangedStyle = RangedAttackStyle.Accurate
            val rangedBase =
                deps.accuracy.getRangedHitChance(player, npc, ranged, rangedStyle, ranged, 1.0)
            val rangedExpected =
                deps.accuracy.getRangedHitChance(
                    player,
                    npc,
                    ranged,
                    rangedStyle,
                    ranged,
                    1.0,
                    plus60,
                )
            assertTrue(rangedExpected > rangedBase)
            assertRollChance(rangedExpected) {
                deps.manager.rollRangedAccuracy(player, npc, ranged, rangedStyle, ranged, 1.0)
            }

            val spell = objType("obj.01_wind_strike")
            val book = Spellbook.Standard
            val spellBase = deps.accuracy.getSpellHitChance(player, npc, spell, book, false)
            val spellExpected =
                deps.accuracy.getSpellHitChance(player, npc, spell, book, false, plus60)
            assertTrue(spellExpected > spellBase)
            assertRollChance(spellExpected) {
                deps.manager.rollSpellAccuracy(player, npc, spell, book, sunfireRune = false)
            }
        }
    }

    private fun GameTestScope.assertRollChance(expectedChance: Int, roll: () -> Boolean) {
        random.next = expectedChance - 1
        assertTrue(roll()) { "Expected a roll of ${expectedChance - 1} to hit." }
        random.next = expectedChance
        assertFalse(roll())
    }

    private class RollCapture : AccuracyRollModifier {
        var attackRoll: Int = -1
        var defenceRoll: Int = -1

        override fun modifyHitChance(hitChance: Int, attackRoll: Int, defenceRoll: Int): Int {
            this.attackRoll = attackRoll
            this.defenceRoll = defenceRoll
            return hitChance
        }
    }

    private class AttackBonusModifier(private val bonus: Int) : AccuracyRollModifier {
        override fun modifyAttackBonus(attackBonus: Int): Int = attackBonus + bonus
    }

    private class TermModifier(private val strength: Int, private val magicDamage: Int) :
        MaxHitModifier {
        override fun modifyStrengthBonus(strengthBonus: Int): Int = strengthBonus + strength

        override fun modifyMagicDamageBonus(magicDamageBonus: Int): Int =
            magicDamageBonus + magicDamage
    }

    private class PrayerModifier(private val effect: Double) :
        AccuracyRollModifier, MaxHitModifier {
        override fun modifyPrayerBonus(prayerBonus: Double): Double =
            ModifierMath.prayerBonus(prayerBonus, effect)

        override fun modifyMagicPrayerDamageBonus(prayerDamageBonus: Int): Int =
            ModifierMath.prayerDamageBonus(prayerDamageBonus, effect)
    }
}
