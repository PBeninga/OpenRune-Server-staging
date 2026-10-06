package org.rsmod.api.combat.modifiers

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modify
import org.rsmod.api.player.righthand
import org.rsmod.api.random.GameRandom
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.type.getOrNull

/**
 * Lets content change PvN, NvP and PvP combat through registered [CombatModifierProvider]s,
 * [CombatProcListener]s and [NpcCombatRules] (for example Leagues pacts and relics), instead
 * of special cases in the combat scripts. Providers and listeners are asked per attack with the
 * player in their context, so an effect can apply to some players and not to others.
 *
 * The combat code calls into this pipeline at a handful of seams:
 * - `PvNCombat` opens an [AttackScope] around every PvN attack ([withAttack]) and flags special
 *   attacks ([withSpecialAttack]).
 * - `PlayerAttackManager` applies [AttackModifiers] to the PvN accuracy roll, max hit and min hit,
 *   the attack delay ([modifyAttackDelay]), and sends every PvN hit through [resolveOutgoing] and
 *   [onBaseHitQueued]. Special attacks (`SpecialAttackManager`), weapon handlers
 *   (`WeaponAttackManager`) and spells (`SpellAttackManager`) all go through it.
 * - `PvNCombatScript` applies [modifyAttackRange].
 * - [CombatModifierPlayerHitModifier] decorates OpenRune's `PlayerHitModifier` binding and calls
 *   [receiveHit] for every npc hit on a player: generic NvP combat and every boss DSL hit.
 * - `RangedAmmoManager`, `MagicRuneManager` and `CombatChargeManager` ask [isRefunded] before
 *   consuming ammo, runes and charges.
 * - The PvN accuracy and max hit formulas take the extra accuracy rolls, max-roll chance, prayer
 *   effect and strength terms of [AttackModifiers] through `AccuracyRollModifier` and
 *   `MaxHitModifier`, and `PlayerAttackManager` rolls [AttackModifiers.maxHitChancePercent] with
 *   [roll].
 * - [CombatModifierHitImpactScript] reports landed hits to [onNpcHitImpact] and
 *   [onPlayerHitImpact].
 * - PvP (`PvPCombat`, `PvPCombatScript` and the PvP paths of `PlayerAttackManager`) mirrors the PvN
 *   stat seams: an [AttackScope] around every attack, accuracy, max hit, attack delay and range,
 *   and the prayer penetration of [pvpHitModifier]. See "PvP" below.
 *
 * ### Order of operations for a player's hit on an npc
 * 1. Accuracy: [AttackModifiers.accuracyPercent] scales the final attack roll and
 *    [AttackModifiers.defenceRollIgnorePercent] the target's final defence roll.
 * 2. Damage: [AttackModifiers.maxHitPercent] then [AttackModifiers.maxHitFlat] modify the max hit;
 *    a successful hit rolls uniformly in `minHit..maxHit` ([AttackModifiers.minHitFlat]).
 * 3. Crit: base hits with damage roll [AttackModifiers.criticalChancePercent] and deal
 *    [ModifierMath.CRITICAL_HIT_MULTIPLIER]× damage.
 * 4. Npc rules ([NpcCombatRules]), last: the protection prayer (reduced by
 *    [AttackModifiers.prayerPenetrationPercent]) and then the hard damage cap.
 *
 * Then [CombatProcListener.onHitRolled] may add extra hits; they skip step 3 and never trigger
 * further procs, except extra hits of a source with a chain cap ([AttackModifiers.chainCaps]),
 * which may trigger more hits of the same source. Upstream npc hit modifiers (flat armour,
 * npc-specific `Modify` events such as the boss DSL's incoming caps) still run when the hit is
 * queued, after these steps.
 *
 * ### Order of operations for an npc's hit on a player
 * 1. Upstream (`StandardPlayerHitModifier`): admin god mode, protection prayers (with the hit's
 *    penetration) and absorption.
 * 2. Listeners' [CombatProcListener.onDamageReceived] responses are collected.
 * 3. Mitigable hits only ([DefenceContext.isMitigable]): dodge (any listener dodge, or the summed
 *    [DefenceModifiers.dodgeChancePercent] roll) sets the damage to 0; otherwise the summed damage
 *    reduction applies (rounded down).
 * 4. When the hit lands, retaliation hits are queued on the npc from the damage taken.
 *
 * ### PvP
 * Against a player, [attackModifiers] keeps only the PvP stats ([AttackModifiers.pvp]): accuracy,
 * max hit, prayer penetration, attack speed and attack range. Nothing else of the PvN pipeline
 * runs: no crit, min hit or max-hit chance, no [CombatProcListener] event (so no extra hits,
 * echoes or Blindbag) and no npc rules, and [receiveHit] only handles npc hits (so no Thorns,
 * recoil or reflect).
 *
 * ### Neutrality
 * With nothing registered in the [CombatModifierRegistry], every function returns its input
 * unchanged, consumes no random values and queues nothing, so combat is unchanged.
 * Random values are also only consumed for chances strictly between 0% and 100%.
 */
@Singleton
public class CombatModifierPipeline
@Inject
constructor(
    registry: CombatModifierRegistry,
    private val random: GameRandom,
    private val mapClock: MapClock,
    private val playerList: PlayerList,
    private val npcList: NpcList,
    private val npcHitModifier: NpcHitModifier,
) {
    private val providers = registry.providers.toList()
    private val listeners = registry.listeners.toList()
    private val npcRules = registry.npcRules.toList()
    private val scopes = IdentityHashMap<Player, AttackScope>()
    private val pendingHits = PendingHitTracker()
    private val pendingRetaliations = PendingRetaliationTracker()

    /** `false` when nothing is registered and the pipeline is a no-op. */
    public val isActive: Boolean = !registry.isEmpty

    /* Attack scopes. */

    /**
     * Runs [block] as an attack from [attacker] against [target] (an npc or a player) with [style].
     * Scopes nest: the previous scope of [attacker] (if any) is restored afterwards.
     *
     * @param spell The spell obj of a spell cast from a spellbook, so that every [AttackContext] of
     *   the attack names it, attack-speed queries included. `null` for every other attack, powered
     *   staves' built-in spells included.
     */
    public inline fun <T> withAttack(
        attacker: Player,
        target: PathingEntity,
        style: CombatStyle,
        spell: ItemServerType? = null,
        block: () -> T,
    ): T {
        val scope = beginAttack(attacker, target, style, spell)
        try {
            return block()
        } finally {
            endAttack(scope)
        }
    }

    /**
     * Runs [block] as a special attack of [attacker]'s current [AttackScope]: every [AttackContext]
     * built meanwhile has [AttackContext.isSpecial] set. Does nothing special without an open
     * scope.
     */
    public inline fun <T> withSpecialAttack(attacker: Player, block: () -> T): T {
        val previous = setSpecialAttack(attacker, true)
        try {
            return block()
        } finally {
            setSpecialAttack(attacker, previous)
        }
    }

    /** Opens an [AttackScope]. Prefer [withAttack], which always closes it. */
    public fun beginAttack(
        attacker: Player,
        target: PathingEntity,
        style: CombatStyle,
        spell: ItemServerType? = null,
    ): AttackScope {
        val scope = AttackScope(attacker, target, style, spell, previous = scopes[attacker])
        scopes[attacker] = scope
        return scope
    }

    /** Closes [scope] and restores the scope that was open before it. */
    public fun endAttack(scope: AttackScope) {
        val previous = scope.previous
        if (previous != null) {
            scopes[scope.attacker] = previous
        } else {
            scopes.remove(scope.attacker)
        }
    }

    /** Returns [attacker]'s open [AttackScope], if any. */
    public fun currentAttack(attacker: Player): AttackScope? = scopes[attacker]

    /**
     * Marks [attacker]'s current scope as a special attack (or not) and returns the previous value.
     * Prefer [withSpecialAttack].
     */
    public fun setSpecialAttack(attacker: Player, special: Boolean): Boolean {
        val scope = scopes[attacker] ?: return false
        val previous = scope.isSpecial
        scope.isSpecial = special
        return previous
    }

    /** `true` while [attacker] is performing a special attack. */
    public fun isSpecialAttack(attacker: Player): Boolean = scopes[attacker]?.isSpecial == true

    /* Attack (PvN and PvP) stat modifiers. */

    /**
     * Builds the [AttackContext] of [attacker]'s current attack against [target]. Without a
     * [spell], the context names the spell of the open [AttackScope], if any.
     */
    public fun attackContext(
        attacker: Player,
        target: PathingEntity,
        style: CombatStyle,
        spell: ItemServerType? = null,
    ): AttackContext {
        val scope = scopes[attacker]
        return AttackContext(
            attacker = attacker,
            target = target,
            style = style,
            weapon = getOrNull(attacker.righthand),
            isSpecial = scope?.isSpecial == true,
            spell = spell ?: scope?.spell,
            source = scope?.rerollSource ?: HitSource.Base,
        )
    }

    /**
     * Sums every provider's [AttackModifiers] for [context]; for an attack on a player, only their
     * PvP stats ([AttackModifiers.pvp]).
     */
    public fun attackModifiers(context: AttackContext): AttackModifiers {
        var total = AttackModifiers.NONE
        for (provider in providers) {
            total += provider.attackModifiers(context)
        }
        return if (context.isPvp) total.pvp() else total
    }

    /**
     * Sums every provider's [AttackModifiers] for [attacker]'s attack on [target]. Returns
     * [AttackModifiers.NONE] without building a context when no provider is registered.
     */
    public fun attackModifiers(
        attacker: Player,
        target: PathingEntity,
        style: CombatStyle,
        spell: ItemServerType? = null,
    ): AttackModifiers {
        if (providers.isEmpty()) {
            return AttackModifiers.NONE
        }
        return attackModifiers(attackContext(attacker, target, style, spell))
    }

    /**
     * Applies attack-speed modifiers to an attack delay of [cycles] ticks set during [attacker]'s
     * current [AttackScope] (see [ModifierMath.attackDelay]). Returns [cycles] unchanged outside a
     * scope.
     */
    public fun modifyAttackDelay(attacker: Player, cycles: Int): Int {
        if (providers.isEmpty()) {
            return cycles
        }
        val scope = scopes[attacker] ?: return cycles
        val modifiers = attackModifiers(attackContext(attacker, scope.target, scope.style))
        return ModifierMath.attackDelay(
            baseDelay = cycles,
            delta = modifiers.attackSpeedDelta,
            floor = modifiers.attackSpeedFloor,
        )
    }

    /** Applies attack-range modifiers to [range] (see [ModifierMath.attackRange]). */
    public fun modifyAttackRange(
        attacker: Player,
        target: PathingEntity,
        style: CombatStyle,
        range: Int,
    ): Int {
        if (providers.isEmpty()) {
            return range
        }
        val modifiers = attackModifiers(attacker, target, style)
        return ModifierMath.attackRange(range, modifiers.attackRangeDelta)
    }

    /* Re-roll bookkeeping for extra hits. */

    /**
     * Remembers how the current attack rolled accuracy against [target], so that [ExtraHit.reroll]
     * and [ExtraHit.retarget] hits can roll it again. [roll] takes the npc to roll against. No-op
     * outside an [AttackScope], without listeners, or while an extra hit is re-rolling.
     */
    public fun recordAccuracyRoll(attacker: Player, target: Npc, roll: (Npc) -> Boolean) {
        val scope = rerollScope(attacker, target) ?: return
        scope.accuracyRoll = roll
    }

    /**
     * Remembers how the current attack rolled damage (for a successful accuracy roll) against
     * [target]. See [recordAccuracyRoll].
     */
    public fun recordDamageRoll(attacker: Player, target: Npc, roll: (Npc) -> Int) {
        val scope = rerollScope(attacker, target) ?: return
        scope.damageRoll = roll
    }

    private fun rerollScope(attacker: Player, target: Npc): AttackScope? {
        if (listeners.isEmpty()) {
            return null
        }
        val scope = scopes[attacker] ?: return null
        if (scope.isRerolling) {
            return null
        }
        if (scope.rollTarget !== target) {
            scope.rollTarget = target
            scope.accuracyRoll = null
            scope.damageRoll = null
        }
        return scope
    }

    /* Outgoing (PvN) hits. */

    /**
     * Resolves a player's base hit on an npc (crit, then protection prayer and damage cap). Call
     * before queueing the hit and pass the result to [onBaseHitQueued] afterwards.
     *
     * @param damage The damage rolled for the hit.
     * @param spell The spell obj of magic hits, when known.
     */
    public fun resolveOutgoing(
        attacker: Player,
        target: Npc,
        style: CombatStyle,
        damage: Int,
        spell: ItemServerType? = null,
    ): OutgoingHit {
        if (!isActive) {
            return OutgoingHit(damage, damage, critical = false, context = null)
        }
        val context = attackContext(attacker, target, style, spell)
        val modifiers = attackModifiers(context)

        val critical = damage > 0 && rollChance(modifiers.criticalChancePercent)
        val critDamage = if (critical) damage * ModifierMath.CRITICAL_HIT_MULTIPLIER else damage

        val npcContext = NpcDamageContext(attacker, target, style, HitSource.Base)
        val penetration = modifiers.prayerPenetrationPercent
        val finalDamage = applyNpcRules(npcContext, critDamage, penetration)
        return OutgoingHit(damage, finalDamage, critical, context, modifiers)
    }

    /**
     * Tracks the queued base [hit] for [CombatProcListener.onHitDealt] and asks listeners for extra
     * hits ([CombatProcListener.onHitRolled]), which are queued immediately.
     *
     * @param hitDelay The delay, in ticks, the base hit was queued with.
     */
    public fun onBaseHitQueued(outgoing: OutgoingHit, hit: Hit, hitDelay: Int) {
        val context = outgoing.context ?: return
        if (listeners.isEmpty()) {
            return
        }
        val target = context.targetNpc ?: return
        trackHit(target, hit, HitSource.Base, context.style, hitDelay)

        val scope = scopes[context.attacker]
        val baseHitIndex = if (scope != null) scope.baseHits++ else 0
        val event =
            HitRolledEvent(
                context = context,
                rolledDamage = outgoing.rolledDamage,
                damage = outgoing.damage,
                maxHit = context.attacker.vars["varp.com_maxhit"],
                critical = outgoing.critical,
                hitDelay = hitDelay,
                modifiers = outgoing.modifiers,
                baseHitIndex = baseHitIndex,
            )
        val extraHits = ArrayList<ExtraHit>()
        for (listener in listeners) {
            extraHits += listener.onHitRolled(event)
        }
        for (extraHit in extraHits) {
            queueExtraHit(context, target, outgoing.modifiers, extraHit, hitDelay, baseHitIndex, 1)
        }
    }

    /**
     * Queues [extraHit], the [depth]-th hit of its source in a chain started by the base hit of
     * [context] on [baseTarget], and lets it chain while [depth] is within its source's chain cap.
     */
    private fun queueExtraHit(
        context: AttackContext,
        baseTarget: Npc,
        modifiers: AttackModifiers,
        extraHit: ExtraHit,
        parentDelay: Int,
        baseHitIndex: Int,
        depth: Int,
    ) {
        val attacker = context.attacker
        val source = extraHit.source
        val target = extraHit.target ?: baseTarget
        val rolled =
            extraHit.fixedDamage
                ?: rerollDamage(attacker, baseTarget, target, source)?.let(extraHit::scaleRolled)
                ?: return

        val npcContext = NpcDamageContext(attacker, target, context.style, source)
        val damage = applyNpcRules(npcContext, rolled, modifiers.prayerPenetrationPercent)

        val delay = parentDelay + extraHit.delayOffset
        val type = extraHit.hitType ?: context.style.hitType
        val hit = target.queueHit(attacker, delay, type, damage, npcHitModifier)
        trackHit(target, hit, source, context.style, delay)

        if (depth > modifiers.chainCap(source)) {
            return
        }
        val chainContext =
            AttackContext(
                attacker = attacker,
                target = target,
                style = context.style,
                weapon = context.weapon,
                isSpecial = context.isSpecial,
                spell = context.spell,
                source = source,
            )
        val event =
            HitRolledEvent(
                context = chainContext,
                rolledDamage = rolled,
                damage = damage,
                maxHit = attacker.vars["varp.com_maxhit"],
                critical = false,
                hitDelay = delay,
                modifiers = modifiers,
                baseHitIndex = baseHitIndex,
                chainDepth = depth,
            )
        val chained = ArrayList<ExtraHit>()
        for (listener in listeners) {
            chained += listener.onHitRolled(event).filter { it.source == source }
        }
        for (next in chained) {
            val inherited = next.withTarget(extraHit.target)
            queueExtraHit(context, baseTarget, modifiers, inherited, delay, baseHitIndex, depth + 1)
        }
    }

    /**
     * Replays the current attack's rolls (made against [baseTarget]) against [target], tagged with
     * [source]. Returns `null` if the attack made no rolls against [baseTarget].
     */
    private fun rerollDamage(
        attacker: Player,
        baseTarget: Npc,
        target: Npc,
        source: HitSource,
    ): Int? {
        val scope = scopes[attacker] ?: return null
        if (scope.rollTarget !== baseTarget) {
            return null
        }
        val damageRoll = scope.damageRoll ?: return null
        val accuracyRoll = scope.accuracyRoll
        scope.rerollSource = source
        try {
            val accurate = accuracyRoll?.invoke(target) ?: true
            return if (accurate) damageRoll(target) else 0
        } finally {
            scope.rerollSource = null
        }
    }

    /* Outgoing (PvP) hits. */

    /**
     * Returns the hit modifier for [attacker]'s hit on the player [target]: [base] (OpenRune's
     * `PlayerHitModifier`), with the attack's [AttackModifiers.prayerPenetrationPercent] set as the
     * hit's penetration first, so that the target's protection prayer blocks that much less of its
     * [ModifierMath.PVP_PROTECTION_PERCENT] ([ModifierMath.pvpPrayerPenetration]). Returns [base]
     * itself when the attack has no penetration.
     *
     * @param spell The spell obj of magic hits, when known.
     */
    public fun pvpHitModifier(
        attacker: Player,
        target: Player,
        style: CombatStyle,
        base: PlayerHitModifier,
        spell: ItemServerType? = null,
    ): PlayerHitModifier {
        if (providers.isEmpty()) {
            return base
        }
        val modifiers = attackModifiers(attacker, target, style, spell)
        val points = ModifierMath.pvpPrayerPenetration(modifiers.prayerPenetrationPercent)
        if (points <= 0) {
            return base
        }
        return PlayerHitModifier { player ->
            penetration = max(penetration, points)
            base.modify(this, player)
        }
    }

    /* Incoming (NvP) hits. */

    /**
     * Applies the damage-received pipeline to [builder], an npc's hit on [defender], after
     * the standard player hit modifier (protection prayers) has run on it: dodge and damage
     * reduction for mitigable hits, and retaliation (thorns, recoil, reflect) once the hit lands.
     *
     * Called by [CombatModifierPlayerHitModifier]. Hits that do not come from an npc are left
     * untouched.
     *
     * @param rolledDamage The damage of the hit before the standard modifier ran.
     */
    public fun receiveHit(builder: HitBuilder, defender: Player, rolledDamage: Int) {
        if (!isActive || !builder.isFromNpc) {
            return
        }
        val attacker = builder.sourceUid?.let { NpcUid(it).resolve(npcList) }
        val context = DefenceContext(defender, attacker, builder.type, builder.isMechanic)
        val incoming = builder.damage

        val event = DamageReceivedEvent(context, rolledDamage, incoming)
        val responses = listeners.map { it.onDamageReceived(event) }

        var damage = incoming
        if (context.isMitigable && damage > 0) {
            val modifiers = defenceModifiers(context)
            val dodged =
                responses.any(DamageReceivedResponse::dodge) ||
                    rollChance(modifiers.dodgeChancePercent)
            damage =
                if (dodged) {
                    0
                } else {
                    val reduction =
                        modifiers.damageReductionPercent + responses.sumOf { it.reductionPercent }
                    ModifierMath.reduceDamage(damage, reduction)
                }
        }
        builder.damage = damage

        val retaliations = responses.flatMap(DamageReceivedResponse::retaliation)
        if (attacker != null && retaliations.isNotEmpty()) {
            val pending =
                PendingRetaliation(attacker, builder.type, damage, retaliations, mapClock.cycle)
            pendingRetaliations.add(defender, pending)
        }
    }

    /**
     * Deals the retaliation (thorns, recoil, reflect) collected by [receiveHit] for [hit], now that
     * it has landed on [player]. Called by [CombatModifierHitImpactScript].
     */
    public fun onPlayerHitImpact(player: Player, hit: Hit) {
        if (!hit.isFromNpc || pendingRetaliations.isEmpty) {
            return
        }
        val attacker = hit.resolveNpcSource(npcList) ?: return
        val pending = pendingRetaliations.take(player, attacker, hit, mapClock.cycle) ?: return
        if (!attacker.isSlotAssigned || attacker.hitpoints <= 0) {
            return
        }
        for (retaliation in pending.retaliations) {
            queueRetaliation(player, attacker, retaliation, hit.damage)
        }
    }

    private fun defenceModifiers(context: DefenceContext): DefenceModifiers {
        var total = DefenceModifiers.NONE
        for (provider in providers) {
            total += provider.defenceModifiers(context)
        }
        return total
    }

    private fun queueRetaliation(
        defender: Player,
        attacker: Npc,
        retaliation: RetaliationHit,
        damageTaken: Int,
    ) {
        val rawDamage = retaliation.damage(damageTaken)
        if (rawDamage <= 0) {
            return
        }
        val npcContext = NpcDamageContext(defender, attacker, null, retaliation.source)
        val damage = applyNpcRules(npcContext, rawDamage, 0.0)
        val delay = RETALIATION_DELAY
        val hit = attacker.queueHit(defender, delay, retaliation.hitType, damage, npcHitModifier)
        trackHit(attacker, hit, retaliation.source, null, delay)
    }

    /* Resources. */

    /**
     * Decides whether [count] units of a resource are refunded (Regenerate) instead of consumed:
     * rolls the summed [ResourceModifiers.regenerateChancePercent], then asks every listener
     * ([CombatProcListener.onResourceConsumed]).
     *
     * @return `true` to skip consuming the resource.
     */
    public fun isRefunded(
        player: Player,
        kind: ResourceKind,
        obj: ItemServerType?,
        count: Int,
    ): Boolean {
        if (!isActive) {
            return false
        }
        val context = ResourceContext(player, kind, obj, count)
        var modifiers = ResourceModifiers.NONE
        for (provider in providers) {
            modifiers += provider.resourceModifiers(context)
        }
        val regenerated = rollChance(modifiers.regenerateChancePercent)

        var refund = regenerated
        val event = ResourceConsumedEvent(context, regenerated)
        for (listener in listeners) {
            if (listener.onResourceConsumed(event) == ResourceDecision.Refund) {
                refund = true
            }
        }
        return refund
    }

    /* Hit impacts. */

    /**
     * Reports a hit landing on [npc] to [CombatProcListener.onHitDealt], if the hit was queued
     * through this pipeline. Called by [CombatModifierHitImpactScript].
     */
    public fun onNpcHitImpact(npc: Npc, hit: Hit) {
        if (listeners.isEmpty() || !hit.isFromPlayer) {
            return
        }
        val pending = pendingHits.take(npc, hit, mapClock.cycle) ?: return
        val attacker = hit.resolvePlayerSource(playerList) ?: return
        val event = HitDealtEvent(attacker, npc, pending.source, pending.style, hit.damage, hit)
        for (listener in listeners) {
            listener.onHitDealt(event)
        }
    }

    private fun trackHit(
        target: Npc,
        hit: Hit,
        source: HitSource,
        style: CombatStyle?,
        hitDelay: Int,
    ) {
        if (listeners.isEmpty()) {
            return
        }
        val pending = PendingHit(hit, source, style, mapClock.cycle + hitDelay)
        pendingHits.add(target, pending)
    }

    /* Shared helpers. */

    private fun applyNpcRules(context: NpcDamageContext, damage: Int, penetration: Double): Int {
        if (npcRules.isEmpty() || damage <= 0) {
            return damage
        }
        var result = damage
        if (context.style != null) {
            var protection = 0.0
            for (rule in npcRules) {
                protection = max(protection, rule.protectionPercent(context))
            }
            result = ModifierMath.applyProtection(result, protection, penetration)
        }
        var cap: Int? = null
        for (rule in npcRules) {
            val ruleCap = rule.damageCap(context) ?: continue
            cap = if (cap == null) ruleCap else min(cap, ruleCap)
        }
        if (cap != null) {
            result = min(result, max(0, cap))
        }
        return result
    }

    /**
     * Rolls a chance of [chancePercent] percentage points with the game random. Chances of 0% or
     * less, and of 100% or more, are decided without consuming a random value.
     */
    public fun roll(chancePercent: Double): Boolean = rollChance(chancePercent)

    private fun rollChance(chancePercent: Double): Boolean {
        val basisPoints = ModifierMath.chanceBasisPoints(chancePercent)
        return when {
            basisPoints <= 0 -> false
            basisPoints >= ModifierMath.CHANCE_SCALE -> true
            else -> random.of(maxExclusive = ModifierMath.CHANCE_SCALE) < basisPoints
        }
    }

    private companion object {
        /** Retaliation is queued when the incoming hit lands and hits the npc on the next tick. */
        private const val RETALIATION_DELAY = 1
    }
}

/**
 * A player's base hit on an npc after [CombatModifierPipeline.resolveOutgoing].
 *
 * @property rolledDamage The damage as rolled.
 * @property damage The damage to queue (after crit, protection prayer and damage cap).
 * @property critical `true` if the hit is a critical hit.
 */
public class OutgoingHit
internal constructor(
    public val rolledDamage: Int,
    public val damage: Int,
    public val critical: Boolean,
    internal val context: AttackContext?,
    internal val modifiers: AttackModifiers = AttackModifiers.NONE,
) {
    override fun toString(): String =
        "OutgoingHit(rolledDamage=$rolledDamage, damage=$damage, critical=$critical)"
}
