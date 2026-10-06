package org.rsmod.content.leagues.demonicpacts.effects.ranged

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.BOW_ECHOES_NEVER_MISS
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.BOW_MAX_HIT_STACKS
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.BOW_MIN_HIT_STACKS
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.BOW_SPEED
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.BOW_SPEED_DELTA
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.CROSSBOW_DOUBLE_ACCURACY
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.CROSSBOW_ECHO_CHANCE
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.CROSSBOW_HEAVY_DAMAGE_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.CROSSBOW_MAX_HIT
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.CROSSBOW_SLOW_DELTA
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.CROSSBOW_SLOW_HEAVY
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.ECHO_CHAIN
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.ECHO_CHAIN_CAP
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.ECHO_ON_REGENERATE
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.HITPOINTS_PER_RANGED_STRENGTH
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.MAX_ACCURACY_BASE_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.MAX_ACCURACY_FROM_RANGE
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.MAX_ACCURACY_PER_TILE_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.RANGED_PRAYER_EFFECT
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.RANGED_PRAYER_EFFECT_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.RANGED_STRENGTH_FROM_HITPOINTS
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.STYLE_SWAP
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.STYLE_SWAP_DAMAGE_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.THROWN_ACCURACY
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.THROWN_ECHO_MAX_HIT
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.THROWN_MELEE_STRENGTH
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.THROWN_MELEE_STRENGTH_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.TWO_HANDED_MELEE_ECHOES
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.TWO_HANDED_MELEE_ECHO_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.tilesTo
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactEffects

/**
 * The stat side of the ranged and echo pacts, for a player's attack on an npc (in PvP the pipeline
 * keeps only their PvP stats; H6 and the bow stacks, which procs prime and build, give nothing):
 * - **Any style:** H1 gives a max accuracy roll chance of 5% + 5% per tile to the target; H6 gives
 *   +25% max hit to the next hit of another style ([StyleSwap]).
 * - **Ranged:** K1 makes ranged prayers 30% more effective; K2 adds 1 ranged strength per 10
 *   hitpoints between the current hitpoints and the Hitpoints level.
 * - **Thrown weapons** (the Eclipse atlatl included): K6 adds 80% of the melee strength bonus to
 *   ranged strength; N8 adds its value (60) to the ranged attack bonus.
 * - **Bows** (not the Eclipse atlatl): K4 attacks 1 tick faster; N4/N5 raise the min and max hit
 *   by the [BowStacks].
 * - **Crossbows** (ballistae included): K8 attacks 2 ticks slower for +70% max hit; N6 always rolls
 *   the max hit; N7 rolls accuracy twice.
 * - **Echo chance** ([RangedEchoes] rolls it): B2, K9 and K10 for ranged attacks, plus E2 with a
 *   crossbow; G9 gives 5% (plus E2's) to two-handed melee weapons. K3 lets echoes chain 4 times.
 * - **Echoes:** E1 makes them never miss with a bow and E3 gives a thrown weapon's echoes a 20%
 *   max hit chance. G9's echoes count as all three ranged weapons.
 */
@Singleton
class RangedPactModifiers
@Inject
constructor(
    private val pacts: DemonicPacts,
    private val wornBonuses: WornBonuses,
    private val bowStacks: BowStacks,
    private val styleSwap: StyleSwap,
) : CombatModifierProvider {
    override fun attackModifiers(context: AttackContext): AttackModifiers {
        val effects = pacts.effects(context.attacker)
        if (effects.isEmpty) {
            return AttackModifiers.NONE
        }
        val classes = context.weapon?.let(WeaponClasses::of) ?: emptySet()
        var modifiers = anyStyle(context, effects)
        modifiers += echoChance(context, effects, classes)
        if (context.source == HitSource.Echo) {
            modifiers += echo(context, effects, classes)
        }
        if (context.style == CombatStyle.Ranged) {
            modifiers += ranged(context, effects, classes)
        }
        return modifiers
    }

    private fun anyStyle(context: AttackContext, effects: PactEffects): AttackModifiers {
        val maxAccuracy =
            if (MAX_ACCURACY_FROM_RANGE in effects) {
                val tiles = context.attacker.tilesTo(context.target)
                MAX_ACCURACY_BASE_PERCENT + MAX_ACCURACY_PER_TILE_PERCENT * tiles
            } else {
                0.0
            }
        val swapped =
            STYLE_SWAP in effects &&
                !context.isPvp &&
                styleSwap.boostedAgainst(context.attacker, context.style)
        val maxHitPercent = if (swapped) STYLE_SWAP_DAMAGE_PERCENT else 0.0
        if (maxAccuracy == 0.0 && maxHitPercent == 0.0) {
            return AttackModifiers.NONE
        }
        return AttackModifiers(
            maxAccuracyRollChancePercent = maxAccuracy,
            maxHitPercent = maxHitPercent,
        )
    }

    private fun echoChance(
        context: AttackContext,
        effects: PactEffects,
        classes: Set<WeaponClass>,
    ): AttackModifiers {
        val chance =
            when (context.style) {
                CombatStyle.Ranged -> {
                    val crossbow = if (WeaponClass.Crossbow in classes) crossbowEcho(effects) else 0
                    (effects[ECHO_ON_REGENERATE] + crossbow).toDouble()
                }
                CombatStyle.Melee ->
                    if (hasMeleeEchoes(effects, classes)) {
                        TWO_HANDED_MELEE_ECHO_PERCENT + crossbowEcho(effects)
                    } else {
                        0.0
                    }
                CombatStyle.Magic -> 0.0
            }
        if (chance <= 0.0) {
            return AttackModifiers.NONE
        }
        val chainCaps =
            if (ECHO_CHAIN in effects) mapOf(HitSource.Echo to ECHO_CHAIN_CAP) else emptyMap()
        return AttackModifiers(
            procChancePercent = mapOf(HitSource.Echo to chance),
            chainCaps = chainCaps,
        )
    }

    private fun echo(
        context: AttackContext,
        effects: PactEffects,
        classes: Set<WeaponClass>,
    ): AttackModifiers {
        val asIf =
            if (context.style == CombatStyle.Melee && hasMeleeEchoes(effects, classes)) {
                G9_ECHO_CLASSES
            } else {
                classes
            }
        val neverMiss = BOW_ECHOES_NEVER_MISS in effects && WeaponClass.Bow in asIf
        val maxHitChance =
            if (WeaponClass.Thrown in asIf) effects[THROWN_ECHO_MAX_HIT].toDouble() else 0.0
        if (!neverMiss && maxHitChance == 0.0) {
            return AttackModifiers.NONE
        }
        return AttackModifiers(
            defenceRollIgnorePercent = if (neverMiss) NEVER_MISS_PERCENT else 0.0,
            maxAccuracyRollChancePercent = if (neverMiss) NEVER_MISS_PERCENT else 0.0,
            maxHitChancePercent = maxHitChance,
        )
    }

    private fun ranged(
        context: AttackContext,
        effects: PactEffects,
        classes: Set<WeaponClass>,
    ): AttackModifiers {
        var modifiers = AttackModifiers.NONE
        if (RANGED_PRAYER_EFFECT in effects) {
            modifiers += AttackModifiers(prayerEffectPercent = RANGED_PRAYER_EFFECT_PERCENT)
        }
        if (RANGED_STRENGTH_FROM_HITPOINTS in effects) {
            val player = context.attacker
            val difference = abs(player.baseHitpointsLvl - player.hitpoints)
            val strength = difference / HITPOINTS_PER_RANGED_STRENGTH
            modifiers += AttackModifiers(rangedStrengthFlat = strength)
        }
        if (WeaponClass.Thrown in classes) {
            modifiers += thrown(context, effects)
        }
        if (WeaponClass.Bow in classes) {
            modifiers += bow(context, effects)
        }
        if (WeaponClass.Crossbow in classes) {
            modifiers += crossbow(effects)
        }
        return modifiers
    }

    private fun thrown(context: AttackContext, effects: PactEffects): AttackModifiers {
        val meleeStrength =
            if (THROWN_MELEE_STRENGTH in effects) {
                val strength = wornBonuses.strengthBonus(context.attacker)
                (strength * THROWN_MELEE_STRENGTH_PERCENT / 100).coerceAtLeast(0)
            } else {
                0
            }
        return AttackModifiers(
            rangedStrengthFlat = meleeStrength,
            attackBonusFlat = effects[THROWN_ACCURACY],
        )
    }

    private fun bow(context: AttackContext, effects: PactEffects): AttackModifiers {
        val stacks = if (context.isPvp) 0 else bowStacks.stacks(context.attacker)
        val minHit = if (stacks > 0) stacks * effects[BOW_MIN_HIT_STACKS] else 0
        return AttackModifiers(
            attackSpeedDelta = if (BOW_SPEED in effects) BOW_SPEED_DELTA else 0,
            minHitFlat = if (minHit > 0) 1 + minHit else 0,
            maxHitFlat = stacks * effects[BOW_MAX_HIT_STACKS],
        )
    }

    private fun crossbow(effects: PactEffects): AttackModifiers {
        val heavy = CROSSBOW_SLOW_HEAVY in effects
        return AttackModifiers(
            attackSpeedDelta = if (heavy) CROSSBOW_SLOW_DELTA else 0,
            maxHitPercent = if (heavy) CROSSBOW_HEAVY_DAMAGE_PERCENT else 0.0,
            maxHitChancePercent = if (CROSSBOW_MAX_HIT in effects) ALWAYS_PERCENT else 0.0,
            extraAccuracyRolls = if (CROSSBOW_DOUBLE_ACCURACY in effects) 1 else 0,
        )
    }

    private fun crossbowEcho(effects: PactEffects): Int = effects[CROSSBOW_ECHO_CHANCE]

    private fun hasMeleeEchoes(effects: PactEffects, classes: Set<WeaponClass>): Boolean =
        TWO_HANDED_MELEE_ECHOES in effects &&
            WeaponClass.Melee in classes &&
            WeaponClass.TwoHanded in classes

    private companion object {
        const val ALWAYS_PERCENT = 100.0
        const val NEVER_MISS_PERCENT = 100.0

        /** G9's echoes act as if a bow, a crossbow and a thrown weapon were all equipped. */
        val G9_ECHO_CLASSES: Set<WeaponClass> =
            setOf(WeaponClass.Bow, WeaponClass.Crossbow, WeaponClass.Thrown)
    }
}
