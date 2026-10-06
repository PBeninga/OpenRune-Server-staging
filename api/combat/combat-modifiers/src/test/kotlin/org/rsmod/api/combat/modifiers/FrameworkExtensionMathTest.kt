package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.stat.PlayerHealing
import org.rsmod.api.player.stat.StatBoosts

/** Pure math of the Phase 3 framework extensions (PF1). */
class FrameworkExtensionMathTest {
    @Test
    fun `hit chance is unchanged without extra rolls or a max-roll chance`() {
        assertEquals(4_321, ModifierMath.modifyHitChance(4_321, 100, 200, 0, 0.0))
        assertEquals(4_321, ModifierMath.modifyHitChance(4_321, 100, 200, -3, -5.0))
    }

    @Test
    fun `extra accuracy rolls hit if any roll hits`() {
        // 1 - 0.5^2 = 75%, 1 - 0.5^3 = 87.5%.
        assertEquals(7_500, ModifierMath.modifyHitChance(5_000, 100, 200, 1, 0.0))
        assertEquals(8_750, ModifierMath.modifyHitChance(5_000, 100, 200, 2, 0.0))
        assertEquals(0, ModifierMath.modifyHitChance(0, 0, 200, 1, 0.0))
        assertEquals(10_000, ModifierMath.modifyHitChance(10_000, 300, 200, 1, 0.0))
    }

    @Test
    fun `a max accuracy roll hits when the defender rolls below the attack roll`() {
        assertEquals(10_000, ModifierMath.maxRollHitChance(300, 200))
        assertEquals(10_000, ModifierMath.maxRollHitChance(201, 200))
        // Defence rolls 0..199: an attack roll of 100 beats 100 of 200 outcomes.
        assertEquals(5_000, ModifierMath.maxRollHitChance(100, 199))
        assertEquals(0, ModifierMath.maxRollHitChance(0, 200))
        assertEquals(10_000, ModifierMath.maxRollHitChance(5, -1))
    }

    @Test
    fun `max-roll chance blends the max-roll and normal hit chances`() {
        // Normal chance 25%, max roll 50%, a 10% max-roll chance: 0.1 * 0.5 + 0.9 * 0.25.
        assertEquals(2_750, ModifierMath.modifyHitChance(2_500, 100, 199, 0, 10.0))
        assertEquals(5_000, ModifierMath.modifyHitChance(2_500, 100, 199, 0, 100.0))
        // A max roll is never worse than the normal hit chance.
        assertEquals(9_000, ModifierMath.modifyHitChance(9_000, 1, 199, 0, 100.0))
        // Both: 0.5 * (1 - 0.5^2) + 0.5 * (1 - 0.75^2) = 0.375 + 0.21875.
        assertEquals(5_938, ModifierMath.modifyHitChance(2_500, 100, 199, 1, 50.0))
    }

    @Test
    fun `prayer effect scales the bonus part of a prayer multiplier`() {
        assertEquals(1.26, ModifierMath.prayerBonus(1.2, 30.0), 1e-9)
        assertEquals(1.299, ModifierMath.prayerBonus(1.23, 30.0), 1e-9)
        assertEquals(1.2, ModifierMath.prayerBonus(1.2, 0.0), 1e-9)
        assertEquals(1.0, ModifierMath.prayerBonus(1.0, 30.0), 1e-9)
        assertEquals(1.0, ModifierMath.prayerBonus(1.2, -200.0), 1e-9)
        // Augury's +4% magic damage (40 tenths) becomes +5.2%.
        assertEquals(52, ModifierMath.prayerDamageBonus(40, 30.0))
        assertEquals(0, ModifierMath.prayerDamageBonus(0, 30.0))
    }

    @Test
    fun `magic damage percent adds tenths of a percent`() {
        assertEquals(120, ModifierMath.magicDamageBonus(100, 2.0))
        assertEquals(100, ModifierMath.magicDamageBonus(100, 0.0))
        assertEquals(5, ModifierMath.magicDamageBonus(0, 0.55))
    }

    @Test
    fun `new attack modifier fields sum and chain caps keep the highest`() {
        val first =
            AttackModifiers(
                extraAccuracyRolls = 1,
                maxAccuracyRollChancePercent = 5.0,
                maxHitChancePercent = 20.0,
                meleeStrengthFlat = 5,
                rangedStrengthFlat = 5,
                magicDamagePercent = 2.0,
                prayerEffectPercent = 30.0,
                chainCaps = mapOf(HitSource.Echo to 4),
            )
        val second =
            AttackModifiers(
                extraAccuracyRolls = 1,
                maxAccuracyRollChancePercent = 10.0,
                maxHitChancePercent = 5.0,
                meleeStrengthFlat = 3,
                magicDamagePercent = 1.0,
                chainCaps = mapOf(HitSource.Echo to 2, HitSource.Blindbag to 3),
            )
        val sum = first + second
        assertEquals(2, sum.extraAccuracyRolls)
        assertEquals(15.0, sum.maxAccuracyRollChancePercent)
        assertEquals(25.0, sum.maxHitChancePercent)
        assertEquals(8, sum.meleeStrengthFlat)
        assertEquals(5, sum.rangedStrengthFlat)
        assertEquals(3.0, sum.magicDamagePercent)
        assertEquals(30.0, sum.prayerEffectPercent)
        assertEquals(4, sum.chainCap(HitSource.Echo))
        assertEquals(3, sum.chainCap(HitSource.Blindbag))
        assertEquals(0, sum.chainCap(HitSource.DoubleStrike))
        assertEquals(8, sum.strengthFlat(CombatStyle.Melee))
        assertEquals(5, sum.strengthFlat(CombatStyle.Ranged))
        assertEquals(0, sum.strengthFlat(CombatStyle.Magic))
        assertTrue(sum.affectsAccuracy)
        assertTrue(sum.affectsMaxHit)
        assertTrue(sum.affectsMaxHitTerms)
        assertFalse(AttackModifiers(maxHitChancePercent = 50.0).affectsMaxHit)
        val huge = AttackModifiers(chainCaps = mapOf(HitSource.Echo to 1_000))
        assertEquals(ModifierMath.MAX_CHAIN_DEPTH, huge.chainCap(HitSource.Echo))
    }

    @Test
    fun `overheal cap adds a share of the base level to the resting level`() {
        assertEquals(128, PlayerHealing.overhealCap(99, 99, 30.0))
        assertEquals(143, PlayerHealing.overhealCap(114, 99, 30.0))
        assertEquals(130, PlayerHealing.overhealCap(100, 100, 30.0))
        assertEquals(99, PlayerHealing.overhealCap(99, 99, 0.0))
        assertEquals(99, PlayerHealing.overhealCap(99, 99, -10.0))
    }

    @Test
    fun `timed boosts stack up to their cap and are never lowered`() {
        assertEquals(1, StatBoosts.timedBoost(0, 1, 10))
        assertEquals(10, StatBoosts.timedBoost(9, 3, 10))
        assertEquals(10, StatBoosts.timedBoost(10, 1, 10))
        assertEquals(12, StatBoosts.timedBoost(12, 1, 10))
        assertEquals(4, StatBoosts.timedBoost(4, -2, 10))
    }
}
