package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.hit.HitType

class ModifierTypesTest {
    @Test
    fun `attack modifiers sum additively and keep the highest speed floor`() {
        val first =
            AttackModifiers(
                accuracyPercent = 25.0,
                defenceRollIgnorePercent = 5.0,
                maxHitPercent = 10.0,
                maxHitFlat = 2,
                minHitFlat = 3,
                criticalChancePercent = 1.0,
                attackSpeedDelta = -1,
                attackSpeedFloor = 2,
                attackRangeDelta = 1,
                prayerPenetrationPercent = 25.0,
                procChancePercent = mapOf(HitSource.Echo to 5.0),
            )
        val second =
            AttackModifiers(
                accuracyPercent = 50.0,
                defenceRollIgnorePercent = 10.0,
                maxHitPercent = 2.5,
                maxHitFlat = 1,
                minHitFlat = 3,
                criticalChancePercent = 4.0,
                attackSpeedDelta = -2,
                attackSpeedFloor = 3,
                attackRangeDelta = 2,
                prayerPenetrationPercent = 25.0,
                procChancePercent = mapOf(HitSource.Echo to 25.0, HitSource.DoubleStrike to 10.0),
            )

        val total = first + second

        assertEquals(75.0, total.accuracyPercent)
        assertEquals(15.0, total.defenceRollIgnorePercent)
        assertEquals(12.5, total.maxHitPercent)
        assertEquals(3, total.maxHitFlat)
        assertEquals(6, total.minHitFlat)
        assertEquals(5.0, total.criticalChancePercent)
        assertEquals(-3, total.attackSpeedDelta)
        assertEquals(3, total.attackSpeedFloor)
        assertEquals(3, total.attackRangeDelta)
        assertEquals(50.0, total.prayerPenetrationPercent)
        assertEquals(30.0, total.procChancePercent(HitSource.Echo))
        assertEquals(10.0, total.procChancePercent(HitSource.DoubleStrike))
        assertEquals(0.0, total.procChancePercent(HitSource.Cleave))
    }

    @Test
    fun `adding NONE changes nothing`() {
        val modifiers = AttackModifiers(accuracyPercent = 10.0, maxHitFlat = 4)
        assertSame(modifiers, modifiers + AttackModifiers.NONE)
        assertSame(modifiers, AttackModifiers.NONE + modifiers)
        assertFalse(AttackModifiers.NONE.affectsAccuracy)
        assertFalse(AttackModifiers.NONE.affectsMaxHit)
        assertTrue(modifiers.affectsAccuracy)
        assertTrue(modifiers.affectsMaxHit)
    }

    @Test
    fun `attack modifier helpers delegate to modifier math`() {
        val modifiers =
            AttackModifiers(
                accuracyPercent = 20.0,
                defenceRollIgnorePercent = 10.0,
                maxHitPercent = 50.0,
                maxHitFlat = 1,
                minHitFlat = 5,
            )
        assertEquals(1_200, modifiers.scaleAttackRoll(1_000))
        assertEquals(900, modifiers.scaleDefenceRoll(1_000))
        assertEquals(16, modifiers.modifyMaxHit(10))
        assertEquals(5, modifiers.minHit(16))
    }

    @Test
    fun `defence and resource modifiers sum additively`() {
        val defence = DefenceModifiers(3.0, 2.0) + DefenceModifiers(5.0, 2.5)
        assertEquals(8.0, defence.damageReductionPercent)
        assertEquals(4.5, defence.dodgeChancePercent)

        val resource = ResourceModifiers(10.0) + ResourceModifiers(25.0)
        assertEquals(35.0, resource.regenerateChancePercent)
    }

    @Test
    fun `extra hits must be tagged as extra hits`() {
        assertThrows(IllegalArgumentException::class.java) { ExtraHit.reroll(HitSource.Base) }
        assertThrows(IllegalArgumentException::class.java) {
            ExtraHit.fixed(HitSource.Base, damage = 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExtraHit.fixed(HitSource.Cleave, damage = 5, delayOffset = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExtraHit.fixed(HitSource.Cleave, damage = -5)
        }

        val reroll = ExtraHit.reroll(HitSource.DoubleStrike)
        assertTrue(reroll.isReroll)
        assertEquals(null, reroll.fixedDamage)

        val fixed = ExtraHit.fixed(HitSource.Chain, damage = 7, hitType = HitType.Magic)
        assertFalse(fixed.isReroll)
        assertEquals(7, fixed.fixedDamage)
        assertEquals(HitType.Magic, fixed.hitType)
    }

    @Test
    fun `retaliation hits combine flat damage and a share of the damage taken`() {
        assertEquals(3, RetaliationHit(HitSource.Thorns, flatDamage = 3).damage(0))
        assertEquals(2, RetaliationHit(HitSource.Recoil, percentOfDamageTaken = 10.0).damage(25))
        assertEquals(
            5,
            RetaliationHit(HitSource.Reflect, flatDamage = 1, percentOfDamageTaken = 20.0)
                .damage(20),
        )
        assertThrows(IllegalArgumentException::class.java) {
            RetaliationHit(HitSource.Echo, flatDamage = 3)
        }
    }

    @Test
    fun `hit source flags`() {
        assertFalse(HitSource.Base.isExtra)
        for (source in HitSource.entries - HitSource.Base) {
            assertTrue(source.isExtra)
        }
        assertTrue(HitSource.Thorns.isRetaliation)
        assertTrue(HitSource.Recoil.isRetaliation)
        assertTrue(HitSource.Reflect.isRetaliation)
        assertFalse(HitSource.DoubleStrike.isRetaliation)
    }

    @Test
    fun `combat style maps to hit types`() {
        for (style in CombatStyle.entries) {
            assertEquals(style, CombatStyle.from(style.hitType))
        }
        assertEquals(null, CombatStyle.from(HitType.Typeless))
    }

    @Test
    fun `empty registry is empty`() {
        assertTrue(CombatModifierRegistry.EMPTY.isEmpty)
        val provider = object : CombatModifierProvider {}
        assertFalse(CombatModifierRegistry.of(providers = listOf(provider)).isEmpty)
    }
}
