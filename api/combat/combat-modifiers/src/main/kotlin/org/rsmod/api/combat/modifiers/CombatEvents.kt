package org.rsmod.api.combat.modifiers

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType

/**
 * A player's hit on an npc, resolved and queued. See [CombatProcListener.onHitRolled].
 *
 * This is a **base** hit, unless [chainDepth] is above `0`: then it is an extra hit of
 * `context.source` that may chain (see [AttackModifiers.chainCaps]).
 *
 * @property context The attack that produced the hit. For a chained extra hit, its `source` is the
 *   extra hit's source and its `target` the npc the extra hit was queued on. Hit events are PvN
 *   only, so its target is always an npc ([target]).
 * @property rolledDamage The damage as rolled by the combat formulas, before crit, protection and
 *   caps.
 * @property damage The damage queued on the target after crit, protection and caps.
 * @property maxHit The attacker's current max hit (after max-hit modifiers) as tracked in
 *   the `com_maxhit` varp. Useful for effects scaled on the max hit.
 * @property critical `true` if the hit was a critical hit.
 * @property hitDelay The number of ticks before the base hit lands. Extra hits land on the same
 *   tick by default.
 * @property modifiers The summed [AttackModifiers] of the attack.
 * @property baseHitIndex The index of this base hit within the current attack: `0` for the first.
 *   Multi-hit weapons (Scythe of Vitur, Torag's hammers, dual macuahuitl, dark bow), multi-hit
 *   special attacks and multi-target spells (ancient bursts and barrages) queue several base hits
 *   in one attack, and each one is offered to [CombatProcListener.onHitRolled]. A proc that should
 *   trigger at most once per attack checks `baseHitIndex == 0`. Always `0` outside an attack
 *   scope.
 * @property chainDepth `0` for a base hit; `n` for an extra hit that is the `n`-th hit of its
 *   source in a chain started by a base hit. Listeners only get chained events for sources with a
 *   chain cap, and may only answer them with extra hits of the same source; a listener that halves
 *   its chance on chained hits (pact K3) checks `chainDepth > 0`.
 */
public class HitRolledEvent(
    public val context: AttackContext,
    public val rolledDamage: Int,
    public val damage: Int,
    public val maxHit: Int,
    public val critical: Boolean,
    public val hitDelay: Int,
    public val modifiers: AttackModifiers,
    public val baseHitIndex: Int = 0,
    public val chainDepth: Int = 0,
) {
    /** The npc the hit was queued on. */
    public val target: Npc =
        requireNotNull(context.targetNpc) { "Hit events are PvN only: $context" }

    /** The source of this hit: [HitSource.Base], or the extra hit's source when chained. */
    public val source: HitSource
        get() = context.source

    /** `true` for a chained extra hit ([chainDepth] above `0`). */
    public val isChained: Boolean
        get() = chainDepth > 0

    /** The summed proc chance (percentage points) for [source]. */
    public fun procChancePercent(source: HitSource): Double = modifiers.procChancePercent(source)

    override fun toString(): String =
        "HitRolledEvent(" +
            "context=$context, " +
            "rolledDamage=$rolledDamage, " +
            "damage=$damage, " +
            "maxHit=$maxHit, " +
            "critical=$critical, " +
            "hitDelay=$hitDelay, " +
            "baseHitIndex=$baseHitIndex, " +
            "chainDepth=$chainDepth" +
            ")"
}

/**
 * An extra hit requested by [CombatProcListener.onHitRolled].
 *
 * Create one with [reroll], [retarget] or [fixed]. Extra hits:
 * - are tagged with their [source], which must not be [HitSource.Base];
 * - never crit, and never trigger [CombatProcListener.onHitRolled] unless their source has a chain
 *   cap ([AttackModifiers.chainCaps]);
 * - still go through the npc's protection prayer and damage cap ([NpcCombatRules]);
 * - are queued as damage only: they do not make the target retaliate or play its block animation.
 *
 * @property source The extra-hit tag.
 * @property target The npc to hit, or `null` for the base hit's target.
 * @property fixedDamage The damage of a [fixed] hit, or `null` for a [reroll] or [retarget].
 * @property delayOffset Extra ticks after the base hit lands. `0` lands with the base hit.
 * @property hitType The hitsplat type, or `null` for the base attack's style.
 * @property damagePercent The share of the damage a [reroll] rolls that it deals; `100.0` for
 *   every other extra hit.
 */
public class ExtraHit
private constructor(
    public val source: HitSource,
    public val target: Npc?,
    public val fixedDamage: Int?,
    public val delayOffset: Int,
    public val hitType: HitType?,
    public val damagePercent: Double = FULL_DAMAGE_PERCENT,
) {
    init {
        require(source.isExtra) { "Extra hits must not be tagged as `HitSource.Base`." }
        require(delayOffset >= 0) { "`delayOffset` must not be negative: $delayOffset" }
        require(fixedDamage == null || fixedDamage >= 0) { "Damage must not be negative." }
        require(damagePercent >= 0.0) { "`damagePercent` must not be negative: $damagePercent" }
    }

    /** `true` if this hit re-rolls the attack's accuracy and damage. */
    public val isReroll: Boolean
        get() = fixedDamage == null

    /** Returns the same extra hit against [npc] (used when a chained hit inherits a target). */
    internal fun withTarget(npc: Npc?): ExtraHit =
        if (target != null || npc == null) this
        else ExtraHit(source, npc, fixedDamage, delayOffset, hitType, damagePercent)

    /**
     * Applies [damagePercent] to the damage a re-roll [rolled]: rounded down, but at least 1 when
     * it rolled any.
     */
    internal fun scaleRolled(rolled: Int): Int {
        if (damagePercent == FULL_DAMAGE_PERCENT || rolled <= 0) {
            return rolled
        }
        return ModifierMath.percentOf(rolled, damagePercent).coerceAtLeast(1)
    }

    override fun toString(): String =
        "ExtraHit(" +
            "source=$source, " +
            "target=${target?.visType?.name}, " +
            "fixedDamage=$fixedDamage, " +
            "delayOffset=$delayOffset, " +
            "hitType=$hitType, " +
            "damagePercent=$damagePercent" +
            ")"

    public companion object {
        /**
         * An extra hit against the same target that re-rolls the attack's accuracy and damage with
         * the same parameters (double strike, echo). The re-roll still gets the attacker's stat
         * modifiers, with [AttackContext.source] set to [source], but never crits.
         *
         * Re-rolls replay the rolls made through `PlayerAttackManager` during the current attack.
         * If the attack made none (for example a special attack with custom damage, or a splashed
         * spell, whose damage roll never happened), the extra hit is skipped.
         */
        public fun reroll(source: HitSource, delayOffset: Int = 0): ExtraHit =
            ExtraHit(source, null, null, delayOffset, null)

        /**
         * A [reroll] that deals [damagePercent] of the damage it rolls, rounded down but at least 1
         * when it rolled any (pact D2's extra hit with 40% of the max hit). A miss stays a miss.
         */
        public fun reroll(source: HitSource, damagePercent: Double, delayOffset: Int = 0): ExtraHit =
            ExtraHit(source, null, null, delayOffset, null, damagePercent)

        /**
         * An extra hit that makes the attack again, with a full accuracy and damage roll, against
         * another [target] (pact N9's extra thrown target, L3's bounce). The rolls use the same
         * parameters as the attack and the attacker's stat modifiers for [target], with
         * [AttackContext.source] set to [source], but never crit.
         *
         * Like [reroll], it replays the rolls made through `PlayerAttackManager` for the attack's
         * own target, and is skipped if the attack made none. The hit is damage only: [target]
         * does not retaliate or play its block animation.
         */
        public fun retarget(source: HitSource, target: Npc, delayOffset: Int = 0): ExtraHit =
            ExtraHit(source, target, null, delayOffset, null)

        /**
         * An extra hit with a fixed [damage] (cleave and chain splashes, flat procs).
         *
         * @param target The npc to hit, or `null` for the base hit's target.
         * @param hitType The hitsplat type, or `null` for the base attack's style.
         */
        public fun fixed(
            source: HitSource,
            damage: Int,
            target: Npc? = null,
            delayOffset: Int = 0,
            hitType: HitType? = null,
        ): ExtraHit = ExtraHit(source, target, damage, delayOffset, hitType)

        private const val FULL_DAMAGE_PERCENT: Double = 100.0
    }
}

/**
 * A hit queued through the pipeline landing on an npc. See [CombatProcListener.onHitDealt].
 *
 * @property attacker The player who dealt the hit.
 * @property target The npc that took the hit.
 * @property source The tag of the hit.
 * @property style The style of the hit, or `null` for retaliation hits.
 * @property damage The damage actually dealt (capped to the npc's remaining hitpoints).
 * @property hit The processed hit.
 */
public class HitDealtEvent(
    public val attacker: Player,
    public val target: Npc,
    public val source: HitSource,
    public val style: CombatStyle?,
    public val damage: Int,
    public val hit: Hit,
) {
    override fun toString(): String =
        "HitDealtEvent(" +
            "attacker=${attacker.username}, " +
            "target=${target.visType.name}, " +
            "source=$source, " +
            "style=$style, " +
            "damage=$damage" +
            ")"
}

/**
 * An npc's hit on a player that is being applied. See [CombatProcListener.onDamageReceived].
 *
 * The event fires when the player's hit modifier runs: when the hit is queued for `queueHit`
 * (protection prayers are decided up front), or when it lands for `queueImpactHit`.
 *
 * @property context The defender, the attacking npc and whether the hit can be mitigated.
 * @property rolledDamage The damage before protection prayers (`queueHit` has already capped it to
 *   the defender's hitpoints).
 * @property incomingDamage The damage after protection prayers (and every other upstream hit
 *   modifier), before the pipeline's dodge and damage reduction.
 */
public class DamageReceivedEvent(
    public val context: DefenceContext,
    public val rolledDamage: Int,
    public val incomingDamage: Int,
) {
    override fun toString(): String =
        "DamageReceivedEvent(" +
            "context=$context, " +
            "rolledDamage=$rolledDamage, " +
            "incomingDamage=$incomingDamage" +
            ")"
}

/**
 * A listener's response to [DamageReceivedEvent].
 *
 * @property dodge `true` to take no damage from the hit (ignored for unmitigable hits).
 * @property reductionPercent Extra damage reduction in percentage points, summed with every other
 *   reduction (ignored for unmitigable hits).
 * @property retaliation Hits dealt back to the attacking npc (thorns, recoil, reflect).
 */
public class DamageReceivedResponse(
    public val dodge: Boolean = false,
    public val reductionPercent: Double = 0.0,
    public val retaliation: List<RetaliationHit> = emptyList(),
) {
    override fun toString(): String =
        "DamageReceivedResponse(" +
            "dodge=$dodge, " +
            "reductionPercent=$reductionPercent, " +
            "retaliation=$retaliation" +
            ")"

    public companion object {
        /** Leaves the hit unchanged. */
        public val NONE: DamageReceivedResponse = DamageReceivedResponse()
    }
}

/**
 * Damage dealt back to the npc that hit a player.
 *
 * The damage is `flatDamage + floor(damageTaken × percentOfDamageTaken / 100)`, where `damageTaken`
 * is the hit's damage after every reduction (recoil comes from the damage taken after
 * reduction). Thorns use [flatDamage] and land even on 0-damage hits; recoil uses
 * [percentOfDamageTaken]. A retaliation that resolves to 0 damage is not queued.
 *
 * Retaliation is dealt when the incoming hit actually lands on the player (a hit that never lands,
 * for example because the player teleported away, deals none), and hits the attacker one tick
 * later. It is not dealt if the attacker is dead or gone by then. Retaliation hits go through the
 * npc's damage cap and are reported to [CombatProcListener.onHitDealt].
 *
 * @property source The tag of the hit; must be a retaliation source such as [HitSource.Thorns].
 * @property hitType The hitsplat type of the retaliation.
 */
public class RetaliationHit(
    public val source: HitSource,
    public val flatDamage: Int = 0,
    public val percentOfDamageTaken: Double = 0.0,
    public val hitType: HitType = HitType.Typeless,
) {
    init {
        require(source.isRetaliation) { "Retaliation hits need a retaliation source: $source" }
        require(flatDamage >= 0) { "`flatDamage` must not be negative: $flatDamage" }
        require(percentOfDamageTaken >= 0.0) { "Percentage must not be negative." }
    }

    /** Resolves the damage of this retaliation for a hit that dealt [damageTaken]. */
    public fun damage(damageTaken: Int): Int =
        flatDamage + ModifierMath.percentOf(damageTaken, percentOfDamageTaken)

    override fun toString(): String =
        "RetaliationHit(" +
            "source=$source, " +
            "flatDamage=$flatDamage, " +
            "percentOfDamageTaken=$percentOfDamageTaken, " +
            "hitType=$hitType" +
            ")"
}

/**
 * A resource about to be consumed. See [CombatProcListener.onResourceConsumed].
 *
 * @property context What is being consumed and by whom.
 * @property regenerated `true` if the summed [ResourceModifiers.regenerateChancePercent] roll
 *   already succeeded, in which case the resource is refunded whatever listeners answer. Listeners
 *   can use this to trigger "on Regenerate" effects.
 */
public class ResourceConsumedEvent(
    public val context: ResourceContext,
    public val regenerated: Boolean,
) {
    override fun toString(): String =
        "ResourceConsumedEvent(context=$context, regenerated=$regenerated)"
}

/** A listener's answer to [ResourceConsumedEvent]. */
public enum class ResourceDecision {
    /** Consume the resource as normal. */
    Consume,

    /** Regenerate: the resource is not consumed. */
    Refund,
}
