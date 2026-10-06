package org.rsmod.api.combat.modifiers

/**
 * Stat modifiers that a [CombatModifierProvider] contributes to one PvN or PvP attack.
 *
 * ### Units
 * - Every `...Percent` field is in **percentage points**: `25.0` means +25%. Negative values are
 *   penalties.
 * - Flat fields are in hitpoints (damage) or game ticks (attack speed) or tiles (attack range).
 *
 * ### Stacking
 * Contributions from all providers are combined with [plus]: every field is **summed** (additive
 * stacking, no stacking caps), except [attackSpeedFloor], where the **highest** floor
 * wins so that no provider can push the attack speed below a floor it declared, and [chainCaps],
 * where the **highest** cap of each source wins. Results are only
 * clamped where a value stops making sense (a probability above 100%, a negative max hit, ...); see
 * [ModifierMath] for the exact rounding of each stat.
 *
 * ### Where each stat applies (order of operations)
 * 1. [prayerEffectPercent] scales the attacker's offensive prayer bonus and [attackBonusFlat] is
 *    added to the attack bonus of the attack's style, then [accuracyPercent]
 *    multiplies the final attack roll and [defenceRollIgnorePercent] shrinks the target's final
 *    defence roll before the hit chance is computed. The hit chance is then raised by
 *    [extraAccuracyRolls] and [maxAccuracyRollChancePercent] (see [ModifierMath.modifyHitChance]).
 * 2. The strength terms ([meleeStrengthFlat], [rangedStrengthFlat], [magicDamagePercent]) and
 *    [prayerEffectPercent] change the inputs of the max hit formula; [maxHitPercent] then
 *    [maxHitFlat] modify its result; [minHitFlat] raises the lowest damage a successful hit can
 *    roll, and [maxHitChancePercent] is the chance that a successful hit rolls the max hit.
 * 3. [criticalChancePercent] is rolled on the rolled hit of [HitSource.Base] hits only and
 *    multiplies it by [ModifierMath.CRITICAL_HIT_MULTIPLIER].
 * 4. The npc's protection prayer, reduced by [prayerPenetrationPercent], and then the npc's hard
 *    damage cap ([NpcCombatRules]) apply last.
 *
 * [attackSpeedDelta]/[attackSpeedFloor] change the delay before the next attack and
 * [attackRangeDelta] changes how far away the attacker can attack from.
 *
 * ### PvP
 * Against a player only the accuracy, max hit, prayer penetration, attack speed and attack range
 * fields apply ([pvp]); the pipeline drops the rest. Prayer penetration then ignores part of the
 * player's protection prayer ([ModifierMath.pvpPrayerPenetration]).
 *
 * @property accuracyPercent Percentage added to the final attack roll.
 * @property defenceRollIgnorePercent Percentage of the target's final defence roll that is ignored
 *   (Armour Pierce). The total is clamped to `0..100`.
 * @property maxHitPercent Percentage added to the max hit (damage boosts and bane effects).
 * @property maxHitFlat Flat damage added to the max hit, after [maxHitPercent].
 * @property minHitFlat The lowest damage a successful hit rolls (clamped to the max hit). `0` or
 *   `1` keep the default minimum of `1`.
 * @property criticalChancePercent Chance for a base hit to deal 4× damage. Clamped to `0..100`.
 * @property attackSpeedDelta Ticks added to the attack delay; negative values attack faster.
 * @property attackSpeedFloor The fastest attack delay (in ticks) that a negative [attackSpeedDelta]
 *   may reach. `0` means no floor other than the global minimum of 1 tick. A floor never slows down
 *   a weapon that is already faster than it.
 * @property attackRangeDelta Tiles added to the attack range (clamped to `1..10`).
 * @property prayerPenetrationPercent Percentage of the target's protection prayer that is ignored.
 *   Clamped to `0..100`.
 * @property procChancePercent Additive proc chances keyed by the extra-hit source they produce (for
 *   example [HitSource.Echo] or [HitSource.DoubleStrike]). The pipeline only sums these; the
 *   [CombatProcListener] that implements the proc reads the total from
 *   [HitRolledEvent.procChancePercent] and rolls it, so that the same proc from different systems
 *   (for example pacts and relics) stacks additively.
 * @property extraAccuracyRolls Additional accuracy rolls: the attack hits if any roll hits (pact
 *   N7 "roll accuracy twice" is `1`). Negative totals count as `0`.
 * @property maxAccuracyRollChancePercent Chance, once per accuracy check, that the attacker's
 *   accuracy roll is its maximum (pact H1). Clamped to `0..100`.
 * @property maxHitChancePercent Chance that a successful hit deals the max hit instead of a random
 *   roll (pact N6 is `100`, E3 and L1 are partial). Clamped to `0..100`.
 * @property meleeStrengthFlat Melee strength bonus added to the worn bonus of melee attacks.
 * @property rangedStrengthFlat Ranged strength bonus added to the worn bonus of ranged attacks.
 * @property magicDamagePercent Magic damage bonus (percentage points, `2.0` = +2%) added to the
 *   worn magic damage bonus of magic attacks. Resolved to tenths of a percent, rounded down.
 * @property prayerEffectPercent How much more effective the attacker's offensive prayers of the
 *   attack's style are: the bonus part of each prayer multiplier is scaled by `1 + percent / 100`
 *   (pact K1, `30.0`: Rigour's +20% accuracy becomes +26%). Applies to both accuracy and damage.
 * @property attackBonusFlat Equipment attack bonus added to the worn attack bonus of the attack's
 *   style (stab, slash or crush for melee, ranged, magic) before the attack roll is computed.
 * @property chainCaps How many times an extra hit of each source may chain into another extra hit
 *   of the same source (pact K3 echoes, D3 Blindbag). Missing or `0` keeps the rule that
 *   extra hits never chain. See [HitRolledEvent.chainDepth].
 */
public data class AttackModifiers(
    val accuracyPercent: Double = 0.0,
    val defenceRollIgnorePercent: Double = 0.0,
    val maxHitPercent: Double = 0.0,
    val maxHitFlat: Int = 0,
    val minHitFlat: Int = 0,
    val criticalChancePercent: Double = 0.0,
    val attackSpeedDelta: Int = 0,
    val attackSpeedFloor: Int = 0,
    val attackRangeDelta: Int = 0,
    val prayerPenetrationPercent: Double = 0.0,
    val procChancePercent: Map<HitSource, Double> = emptyMap(),
    val extraAccuracyRolls: Int = 0,
    val maxAccuracyRollChancePercent: Double = 0.0,
    val maxHitChancePercent: Double = 0.0,
    val meleeStrengthFlat: Int = 0,
    val rangedStrengthFlat: Int = 0,
    val magicDamagePercent: Double = 0.0,
    val prayerEffectPercent: Double = 0.0,
    val chainCaps: Map<HitSource, Int> = emptyMap(),
    val attackBonusFlat: Int = 0,
) {
    /** `true` if this modifies the accuracy roll at all. */
    public val affectsAccuracy: Boolean
        get() =
            accuracyPercent != 0.0 ||
                defenceRollIgnorePercent != 0.0 ||
                extraAccuracyRolls > 0 ||
                maxAccuracyRollChancePercent > 0.0 ||
                prayerEffectPercent != 0.0 ||
                attackBonusFlat != 0

    /** `true` if this modifies the max hit at all. */
    public val affectsMaxHit: Boolean
        get() = maxHitPercent != 0.0 || maxHitFlat != 0 || affectsMaxHitTerms

    /** `true` if this modifies an input of the max hit formula (strength terms, prayers). */
    public val affectsMaxHitTerms: Boolean
        get() =
            meleeStrengthFlat != 0 ||
                rangedStrengthFlat != 0 ||
                magicDamagePercent != 0.0 ||
                prayerEffectPercent != 0.0

    /** Combines two contributions: fields are summed and the highest speed floor wins. */
    public operator fun plus(other: AttackModifiers): AttackModifiers {
        if (other == NONE) {
            return this
        }
        if (this == NONE) {
            return other
        }
        return AttackModifiers(
            accuracyPercent = accuracyPercent + other.accuracyPercent,
            defenceRollIgnorePercent = defenceRollIgnorePercent + other.defenceRollIgnorePercent,
            maxHitPercent = maxHitPercent + other.maxHitPercent,
            maxHitFlat = maxHitFlat + other.maxHitFlat,
            minHitFlat = minHitFlat + other.minHitFlat,
            criticalChancePercent = criticalChancePercent + other.criticalChancePercent,
            attackSpeedDelta = attackSpeedDelta + other.attackSpeedDelta,
            attackSpeedFloor = maxOf(attackSpeedFloor, other.attackSpeedFloor),
            attackRangeDelta = attackRangeDelta + other.attackRangeDelta,
            prayerPenetrationPercent = prayerPenetrationPercent + other.prayerPenetrationPercent,
            procChancePercent = sumChances(procChancePercent, other.procChancePercent),
            extraAccuracyRolls = extraAccuracyRolls + other.extraAccuracyRolls,
            maxAccuracyRollChancePercent =
                maxAccuracyRollChancePercent + other.maxAccuracyRollChancePercent,
            maxHitChancePercent = maxHitChancePercent + other.maxHitChancePercent,
            meleeStrengthFlat = meleeStrengthFlat + other.meleeStrengthFlat,
            rangedStrengthFlat = rangedStrengthFlat + other.rangedStrengthFlat,
            magicDamagePercent = magicDamagePercent + other.magicDamagePercent,
            prayerEffectPercent = prayerEffectPercent + other.prayerEffectPercent,
            chainCaps = maxCaps(chainCaps, other.chainCaps),
            attackBonusFlat = attackBonusFlat + other.attackBonusFlat,
        )
    }

    /** The summed proc chance for [source], in percentage points. `0.0` if none. */
    public fun procChancePercent(source: HitSource): Double = procChancePercent[source] ?: 0.0

    /** How many times extra hits of [source] may chain (see [chainCaps]). `0` if none. */
    public fun chainCap(source: HitSource): Int =
        (chainCaps[source] ?: 0).coerceIn(0, ModifierMath.MAX_CHAIN_DEPTH)

    /** The strength bonus term for [style]: melee or ranged strength. `0` for magic. */
    public fun strengthFlat(style: CombatStyle): Int =
        when (style) {
            CombatStyle.Melee -> meleeStrengthFlat
            CombatStyle.Ranged -> rangedStrengthFlat
            CombatStyle.Magic -> 0
        }

    /**
     * Applies [extraAccuracyRolls] and [maxAccuracyRollChancePercent] to a final hit chance. See
     * [ModifierMath.modifyHitChance].
     */
    public fun modifyHitChance(hitChance: Int, attackRoll: Int, defenceRoll: Int): Int =
        ModifierMath.modifyHitChance(
            hitChance = hitChance,
            attackRoll = attackRoll,
            defenceRoll = defenceRoll,
            extraRolls = extraAccuracyRolls,
            maxRollChancePercent = maxAccuracyRollChancePercent,
        )

    /** Applies [prayerEffectPercent] to a prayer multiplier. See [ModifierMath.prayerBonus]. */
    public fun modifyPrayerBonus(prayerBonus: Double): Double =
        ModifierMath.prayerBonus(prayerBonus, prayerEffectPercent)

    /** Adds [attackBonusFlat] to an equipment attack bonus. */
    public fun modifyAttackBonus(attackBonus: Int): Int = attackBonus + attackBonusFlat

    /** Applies [accuracyPercent] to a final attack roll. See [ModifierMath.scaleAttackRoll]. */
    public fun scaleAttackRoll(attackRoll: Int): Int =
        ModifierMath.scaleAttackRoll(attackRoll, accuracyPercent)

    /**
     * Applies [defenceRollIgnorePercent] to a final defence roll. See
     * [ModifierMath.scaleDefenceRoll].
     */
    public fun scaleDefenceRoll(defenceRoll: Int): Int =
        ModifierMath.scaleDefenceRoll(defenceRoll, defenceRollIgnorePercent)

    /** Applies [maxHitPercent] and [maxHitFlat] to a max hit. See [ModifierMath.modifyMaxHit]. */
    public fun modifyMaxHit(maxHit: Int): Int =
        ModifierMath.modifyMaxHit(maxHit, maxHitPercent, maxHitFlat)

    /** The lowest damage a successful hit may roll. See [ModifierMath.minHit]. */
    public fun minHit(maxHit: Int): Int = ModifierMath.minHit(minHitFlat, maxHit)

    /**
     * The part of these modifiers that applies to an attack on a player (Jagex's PvP limit: stat
     * boosts, attack speed and range apply, procs don't): the accuracy, max hit, prayer
     * penetration, attack speed and attack range fields. The min hit, the max hit chance, critical
     * hits, proc chances and chain caps are dropped.
     */
    public fun pvp(): AttackModifiers {
        if (this == NONE) {
            return NONE
        }
        return AttackModifiers(
            accuracyPercent = accuracyPercent,
            defenceRollIgnorePercent = defenceRollIgnorePercent,
            extraAccuracyRolls = extraAccuracyRolls,
            maxAccuracyRollChancePercent = maxAccuracyRollChancePercent,
            attackBonusFlat = attackBonusFlat,
            prayerEffectPercent = prayerEffectPercent,
            maxHitPercent = maxHitPercent,
            maxHitFlat = maxHitFlat,
            meleeStrengthFlat = meleeStrengthFlat,
            rangedStrengthFlat = rangedStrengthFlat,
            magicDamagePercent = magicDamagePercent,
            prayerPenetrationPercent = prayerPenetrationPercent,
            attackSpeedDelta = attackSpeedDelta,
            attackSpeedFloor = attackSpeedFloor,
            attackRangeDelta = attackRangeDelta,
        )
    }

    public companion object {
        /** No modification at all. Adding [NONE] to anything returns it unchanged. */
        public val NONE: AttackModifiers = AttackModifiers()

        private fun sumChances(
            first: Map<HitSource, Double>,
            second: Map<HitSource, Double>,
        ): Map<HitSource, Double> {
            if (first.isEmpty()) {
                return second
            }
            if (second.isEmpty()) {
                return first
            }
            val sum = LinkedHashMap(first)
            for ((source, chance) in second) {
                sum[source] = (sum[source] ?: 0.0) + chance
            }
            return sum
        }

        private fun maxCaps(
            first: Map<HitSource, Int>,
            second: Map<HitSource, Int>,
        ): Map<HitSource, Int> {
            if (first.isEmpty()) {
                return second
            }
            if (second.isEmpty()) {
                return first
            }
            val max = LinkedHashMap(first)
            for ((source, cap) in second) {
                max[source] = maxOf(max[source] ?: 0, cap)
            }
            return max
        }
    }
}

/**
 * Stat modifiers that a [CombatModifierProvider] contributes to a hit a player receives from an npc
 * (NvP). Fields are in percentage points and are **summed** across providers.
 *
 * Neither stat applies to typeless or mechanic damage ([DefenceContext.isMitigable]).
 *
 * @property damageReductionPercent Percentage of the incoming damage removed, applied after
 *   protection prayers and rounded down. The total (including [DamageReceivedResponse] reductions)
 *   is clamped to `0..100`.
 * @property dodgeChancePercent Chance to take no damage from the hit. Clamped to `0..100`.
 */
public data class DefenceModifiers(
    val damageReductionPercent: Double = 0.0,
    val dodgeChancePercent: Double = 0.0,
) {
    /** Combines two contributions by summing every field. */
    public operator fun plus(other: DefenceModifiers): DefenceModifiers =
        DefenceModifiers(
            damageReductionPercent = damageReductionPercent + other.damageReductionPercent,
            dodgeChancePercent = dodgeChancePercent + other.dodgeChancePercent,
        )

    public companion object {
        /** No modification at all. */
        public val NONE: DefenceModifiers = DefenceModifiers()
    }
}

/**
 * Stat modifiers that a [CombatModifierProvider] contributes when a resource is consumed.
 *
 * @property regenerateChancePercent Chance (percentage points, summed across providers, clamped to
 *   `0..100`) that the resource is refunded instead of consumed ("Regenerate"). The pipeline rolls
 *   it once per [ResourceContext].
 */
public data class ResourceModifiers(val regenerateChancePercent: Double = 0.0) {
    /** Combines two contributions by summing every field. */
    public operator fun plus(other: ResourceModifiers): ResourceModifiers =
        ResourceModifiers(regenerateChancePercent + other.regenerateChancePercent)

    public companion object {
        /** No modification at all. */
        public val NONE: ResourceModifiers = ResourceModifiers()
    }
}
