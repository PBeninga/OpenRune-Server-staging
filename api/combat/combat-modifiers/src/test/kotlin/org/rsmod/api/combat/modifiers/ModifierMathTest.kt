package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModifierMathTest {
    @Test
    fun `accuracy percent scales the attack roll and rounds down`() {
        assertEquals(10_000, ModifierMath.scaleAttackRoll(10_000, 0.0))
        assertEquals(12_500, ModifierMath.scaleAttackRoll(10_000, 25.0))
        assertEquals(1123, ModifierMath.scaleAttackRoll(999, 12.5))
        assertEquals(5_000, ModifierMath.scaleAttackRoll(10_000, -50.0))
        assertEquals(0, ModifierMath.scaleAttackRoll(10_000, -150.0))
    }

    @Test
    fun `defence ignore shrinks the defence roll and is clamped to 0-100`() {
        assertEquals(8_500, ModifierMath.scaleDefenceRoll(10_000, 15.0))
        assertEquals(9_250, ModifierMath.scaleDefenceRoll(10_000, 7.5))
        assertEquals(0, ModifierMath.scaleDefenceRoll(10_000, 100.0))
        assertEquals(0, ModifierMath.scaleDefenceRoll(10_000, 250.0))
        assertEquals(10_000, ModifierMath.scaleDefenceRoll(10_000, -10.0))
    }

    @Test
    fun `max hit applies the percentage first and then the flat bonus`() {
        assertEquals(40, ModifierMath.modifyMaxHit(40, 0.0, 0))
        assertEquals(45, ModifierMath.modifyMaxHit(40, 12.5, 0))
        assertEquals(47, ModifierMath.modifyMaxHit(40, 10.0, 3))
        assertEquals(12, ModifierMath.modifyMaxHit(10, 29.0, 0))
        assertEquals(0, ModifierMath.modifyMaxHit(40, -100.0, 0))
        assertEquals(0, ModifierMath.modifyMaxHit(40, -250.0, 0))
        assertEquals(0, ModifierMath.modifyMaxHit(5, 0.0, -10))
    }

    @Test
    fun `scaling uses exact integer arithmetic`() {
        // `100 * 0.29` is `28.999999999999996` in floating point.
        assertEquals(29, ModifierMath.modifyMaxHit(100, -71.0, 0))
        assertEquals(29, ModifierMath.reduceDamage(100, 71.0))
        assertEquals(29, ModifierMath.percentOf(100, 29.0))
    }

    @Test
    fun `min hit keeps the default minimum and never exceeds the max hit`() {
        assertEquals(1, ModifierMath.minHit(0, 40))
        assertEquals(1, ModifierMath.minHit(1, 40))
        assertEquals(3, ModifierMath.minHit(3, 40))
        assertEquals(40, ModifierMath.minHit(50, 40))
        assertEquals(1, ModifierMath.minHit(5, 0))
    }

    @Test
    fun `attack delay applies the delta with a floor that never slows attacks down`() {
        assertEquals(4, ModifierMath.attackDelay(4, 0, 0))
        assertEquals(3, ModifierMath.attackDelay(4, -1, 0))
        assertEquals(7, ModifierMath.attackDelay(5, 2, 0))
        assertEquals(1, ModifierMath.attackDelay(4, -10, 0))
        // Pact F7: spells attack 2 ticks faster, down to 2 ticks.
        assertEquals(3, ModifierMath.attackDelay(5, -2, 2))
        assertEquals(2, ModifierMath.attackDelay(3, -2, 2))
        // Pact F8: powered staves attack 3 ticks faster, down to 1 tick.
        assertEquals(1, ModifierMath.attackDelay(4, -3, 1))
        // A floor never slows down a weapon that is already faster than it.
        assertEquals(2, ModifierMath.attackDelay(2, -1, 3))
        // A positive delta is never floored.
        assertEquals(6, ModifierMath.attackDelay(4, 2, 5))
    }

    @Test
    fun `attack range applies the delta within 1 to 10 tiles`() {
        assertEquals(1, ModifierMath.attackRange(1, 0))
        assertEquals(2, ModifierMath.attackRange(1, 1))
        assertEquals(10, ModifierMath.attackRange(7, 5))
        assertEquals(1, ModifierMath.attackRange(1, -3))
    }

    @Test
    fun `damage reduction rounds down and is clamped to 0-100`() {
        assertEquals(5, ModifierMath.reduceDamage(10, 50.0))
        assertEquals(6, ModifierMath.reduceDamage(10, 33.0))
        assertEquals(0, ModifierMath.reduceDamage(10, 150.0))
        assertEquals(10, ModifierMath.reduceDamage(10, -20.0))
        assertEquals(0, ModifierMath.reduceDamage(0, 50.0))
    }

    @Test
    fun `prayer penetration ignores part of the npc protection`() {
        assertEquals(40, ModifierMath.applyProtection(40, 0.0, 50.0))
        assertEquals(0, ModifierMath.applyProtection(40, 100.0, 0.0))
        assertEquals(10, ModifierMath.applyProtection(40, 100.0, 25.0))
        assertEquals(40, ModifierMath.applyProtection(40, 100.0, 100.0))
        assertEquals(40, ModifierMath.applyProtection(40, 100.0, 175.0))
        assertEquals(28, ModifierMath.applyProtection(40, 60.0, 50.0))
    }

    @Test
    fun `chances convert to clamped basis points`() {
        assertEquals(0, ModifierMath.chanceBasisPoints(0.0))
        assertEquals(0, ModifierMath.chanceBasisPoints(-5.0))
        assertEquals(275, ModifierMath.chanceBasisPoints(2.75))
        assertEquals(1_250, ModifierMath.chanceBasisPoints(12.5))
        assertEquals(10_000, ModifierMath.chanceBasisPoints(100.0))
        assertEquals(10_000, ModifierMath.chanceBasisPoints(150.0))
    }

    @Test
    fun `percent of rounds down and ignores negative percentages`() {
        assertEquals(2, ModifierMath.percentOf(25, 10.0))
        assertEquals(0, ModifierMath.percentOf(0, 10.0))
        assertEquals(0, ModifierMath.percentOf(25, -10.0))
        assertEquals(25, ModifierMath.percentOf(25, 100.0))
    }
}
