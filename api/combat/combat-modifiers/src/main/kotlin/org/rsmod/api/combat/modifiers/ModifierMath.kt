package org.rsmod.api.combat.modifiers

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The exact arithmetic of every combat modifier, kept pure so that the game, tests and future
 * simulators agree.
 *
 * Conventions:
 * - Percentages are percentage points (`25.0` = 25%). They are resolved to basis points (0.01%)
 *   first, so `12.5` and `2.75` are exact, and all scaling is done in integer arithmetic.
 * - Scaled results are **rounded down** (towards zero), matching how OSRS combat formulas round.
 * - Probabilities are converted to basis points ([CHANCE_SCALE] = 100%) and rolled with
 *   `GameRandom.of(CHANCE_SCALE) < basisPoints`. A chance of 0% or 100% does not consume a random
 *   value.
 */
public object ModifierMath {
    /** Damage multiplier of a critical hit. */
    public const val CRITICAL_HIT_MULTIPLIER: Int = 4

    /** 100% expressed in basis points. Chance rolls draw from `0 until CHANCE_SCALE`. */
    public const val CHANCE_SCALE: Int = 10_000

    /** The longest attack range a player can have (RSMod `MAX_ATTACK_RANGE`). */
    public const val MAX_ATTACK_RANGE: Int = 10

    /** The fastest possible attack delay, in ticks. */
    public const val MIN_ATTACK_DELAY: Int = 1

    /** The most times an extra hit can chain, whatever [AttackModifiers.chainCaps] asks for. */
    public const val MAX_CHAIN_DEPTH: Int = 10

    /** The share of a player's hit that a player's protection prayer blocks, in percent. */
    public const val PVP_PROTECTION_PERCENT: Int = 40

    private const val ONE: Long = CHANCE_SCALE.toLong()

    /** Scales an attack roll: `floor(roll × max(0, 1 + accuracy / 100))`. */
    public fun scaleAttackRoll(attackRoll: Int, accuracyPercent: Double): Int =
        scale(attackRoll, max(0L, ONE + basisPoints(accuracyPercent)))

    /** Scales a defence roll: `floor(roll × (1 - ignore / 100))`, ignore clamped to `0..100`. */
    public fun scaleDefenceRoll(defenceRoll: Int, defenceRollIgnorePercent: Double): Int =
        scale(defenceRoll, ONE - basisPoints(clampPercent(defenceRollIgnorePercent)))

    /**
     * Applies max-hit modifiers: `max(0, floor(maxHit × (1 + percent / 100)) + flat)`.
     *
     * The percentage applies first so flat bonuses are never multiplied. A total percentage below
     * -100% is treated as -100%.
     */
    public fun modifyMaxHit(maxHit: Int, maxHitPercent: Double, maxHitFlat: Int): Int {
        if (maxHitPercent == 0.0 && maxHitFlat == 0) {
            return maxHit
        }
        val multiplied = scale(maxHit, max(0L, ONE + basisPoints(maxHitPercent)))
        return max(0, multiplied + maxHitFlat)
    }

    /**
     * Returns the lowest damage a successful hit may roll, given the summed [minHitFlat] and the
     * final [maxHit]. Successful hits roll uniformly in `minHit..maxHit`.
     *
     * Values of `1` or less keep the default minimum of `1`. Larger values are clamped to
     * [maxHit], so a min hit never exceeds the max hit.
     */
    public fun minHit(minHitFlat: Int, maxHit: Int): Int =
        if (minHitFlat <= 1 || maxHit <= 1) 1 else min(minHitFlat, maxHit)

    /**
     * Applies attack-speed modifiers to an attack delay of [baseDelay] ticks.
     * - A positive [delta] slows the attack down: `baseDelay + delta`.
     * - A negative [delta] speeds it up, but never below `min(baseDelay, floor)`: the [floor]
     *   limits how fast a modifier can make an attack, and never slows down an attack that is
     *   already faster than the floor.
     *
     * The result is never below [MIN_ATTACK_DELAY].
     */
    public fun attackDelay(baseDelay: Int, delta: Int, floor: Int): Int {
        if (delta == 0) {
            return baseDelay
        }
        val modified = baseDelay + delta
        if (delta > 0) {
            return max(MIN_ATTACK_DELAY, modified)
        }
        val limit = max(MIN_ATTACK_DELAY, min(baseDelay, floor))
        return max(limit, modified)
    }

    /** Applies an attack-range delta, clamped to `1..MAX_ATTACK_RANGE`. */
    public fun attackRange(baseRange: Int, delta: Int): Int {
        if (delta == 0) {
            return baseRange
        }
        return (baseRange + delta).coerceIn(1, MAX_ATTACK_RANGE)
    }

    /** Removes [reductionPercent] (clamped to `0..100`) of [damage], rounding the result down. */
    public fun reduceDamage(damage: Int, reductionPercent: Double): Int {
        if (damage <= 0) {
            return damage
        }
        return scale(damage, ONE - basisPoints(clampPercent(reductionPercent)))
    }

    /**
     * Applies an npc's protection prayer to [damage].
     *
     * The prayer blocks [protectionPercent] of the damage; [penetrationPercent] of that block is
     * ignored. Formally `floor(damage × (1 - protection / 100 × (1 - penetration / 100)))`, with
     * both percentages clamped to `0..100`.
     */
    public fun applyProtection(
        damage: Int,
        protectionPercent: Double,
        penetrationPercent: Double,
    ): Int {
        val protection = basisPoints(clampPercent(protectionPercent))
        if (protection == 0L || damage <= 0) {
            return damage
        }
        val penetration = basisPoints(clampPercent(penetrationPercent))
        val blocked = protection * (ONE - penetration) / ONE
        return scale(damage, ONE - blocked)
    }

    /**
     * Converts a prayer penetration ([penetrationPercent] of the block ignored, clamped to
     * `0..100`) into the percentage points a player's protection prayer blocks less of a player's
     * hit: `floor(PVP_PROTECTION_PERCENT × penetration / 100)`, the `HitBuilder.penetration` the
     * standard player hit modifier subtracts from its block.
     */
    public fun pvpPrayerPenetration(penetrationPercent: Double): Int =
        percentOf(PVP_PROTECTION_PERCENT, clampPercent(penetrationPercent))

    /** Returns `floor(value × percent / 100)`; negative percentages count as `0`. */
    public fun percentOf(value: Int, percent: Double): Int {
        if (value <= 0 || percent <= 0.0) {
            return 0
        }
        return scale(value, basisPoints(percent))
    }

    /**
     * Raises a final hit chance (`0..CHANCE_SCALE`) for extra accuracy rolls and a chance to roll
     * max accuracy.
     *
     * With `n = 1 + extraRolls` rolls, a roll hitting with chance `p` hits at least once with
     * `pₙ = 1 - (1 - p)ⁿ`. Once per check, [maxRollChancePercent] (`c`) replaces the attacker's
     * random roll with its maximum, which hits with [maxRollHitChance] (`m`, at least `p`):
     * `result = c × mₙ + (1 - c) × pₙ`. The result is rounded to the nearest basis point, like
     * the unmodified hit chance, so a single `GameRandom` roll decides the whole check.
     */
    public fun modifyHitChance(
        hitChance: Int,
        attackRoll: Int,
        defenceRoll: Int,
        extraRolls: Int,
        maxRollChancePercent: Double,
    ): Int {
        val rolls = 1 + max(0, extraRolls)
        val maxRollChance = chanceBasisPoints(maxRollChancePercent)
        if (rolls == 1 && maxRollChance == 0) {
            return hitChance
        }
        val chance = hitChance.coerceIn(0, CHANCE_SCALE) / ONE.toDouble()
        val normal = atLeastOnce(chance, rolls)
        if (maxRollChance == 0) {
            return toChance(normal)
        }
        val maxRoll = max(chance, maxRollHitChance(attackRoll, defenceRoll) / ONE.toDouble())
        val share = maxRollChance / ONE.toDouble()
        return toChance(share * atLeastOnce(maxRoll, rolls) + (1 - share) * normal)
    }

    /**
     * The hit chance (`0..CHANCE_SCALE`) of an attacker whose accuracy roll is its maximum: the
     * defender rolls uniformly in `0..defenceRoll` and the attack hits when the defender rolls
     * below [attackRoll], so the chance is `min(attackRoll, defenceRoll + 1) / (defenceRoll + 1)`.
     */
    public fun maxRollHitChance(attackRoll: Int, defenceRoll: Int): Int {
        if (attackRoll <= 0) {
            return 0
        }
        if (defenceRoll < 0) {
            return CHANCE_SCALE
        }
        val outcomes = defenceRoll.toLong() + 1
        return (min(attackRoll.toLong(), outcomes) * ONE / outcomes).toInt()
    }

    /**
     * Scales the bonus part of a prayer multiplier by [effectPercent]: `1 + (prayerBonus - 1) × (1
     * + effectPercent / 100)`. Multipliers of `1.0` or less (no prayer) are returned unchanged, and
     * the scaled bonus never drops below `0`.
     */
    public fun prayerBonus(prayerBonus: Double, effectPercent: Double): Double {
        if (effectPercent == 0.0 || prayerBonus <= 1.0) {
            return prayerBonus
        }
        val bonus = ((prayerBonus - 1.0) * ONE).roundToLong()
        val scaled = max(0L, bonus * (ONE + basisPoints(effectPercent)) / ONE)
        return 1.0 + scaled / ONE.toDouble()
    }

    /** Scales a prayer damage bonus (in any unit) by [effectPercent], rounding down. */
    public fun prayerDamageBonus(prayerDamageBonus: Int, effectPercent: Double): Int {
        if (effectPercent == 0.0 || prayerDamageBonus <= 0) {
            return prayerDamageBonus
        }
        return scale(prayerDamageBonus, max(0L, ONE + basisPoints(effectPercent)))
    }

    /**
     * Adds [percent] (percentage points) to a magic damage bonus in tenths of a percent (`100` =
     * +10%), rounding the added amount towards zero.
     */
    public fun magicDamageBonus(magicDamageBonus: Int, percent: Double): Int {
        if (percent == 0.0) {
            return magicDamageBonus
        }
        return magicDamageBonus + (basisPoints(percent) / 10).toInt()
    }

    /** Converts a chance in percentage points to basis points, clamped to `0..CHANCE_SCALE`. */
    public fun chanceBasisPoints(chancePercent: Double): Int =
        basisPoints(clampPercent(chancePercent)).toInt()

    /** Clamps a percentage to `0..100`. */
    public fun clampPercent(percent: Double): Double = percent.coerceIn(0.0, 100.0)

    private fun basisPoints(percent: Double): Long = (percent * (CHANCE_SCALE / 100)).roundToLong()

    private fun atLeastOnce(chance: Double, rolls: Int): Double = 1.0 - (1.0 - chance).pow(rolls)

    private fun toChance(probability: Double): Int =
        (probability * CHANCE_SCALE).roundToInt().coerceIn(0, CHANCE_SCALE)

    private fun scale(value: Int, multiplier: Long): Int {
        if (multiplier == ONE) {
            return value
        }
        return (value.toLong() * multiplier / ONE).toInt()
    }
}
