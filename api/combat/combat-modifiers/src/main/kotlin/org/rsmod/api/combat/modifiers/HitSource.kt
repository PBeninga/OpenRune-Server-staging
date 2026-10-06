package org.rsmod.api.combat.modifiers

/**
 * Tags every hit that goes through the combat modifier pipeline with where it came from.
 *
 * Guard rail: only [Base] hits can trigger extra-hit procs ([CombatProcListener.onHitRolled]) or
 * critical hits. Every other source is an extra hit and never chains, unless its source has a chain
 * cap ([AttackModifiers.chainCaps]). Lifesteal-style effects ([CombatProcListener.onHitDealt])
 * still see every source.
 */
public enum class HitSource {
    /** The attack's own hit, rolled by the normal combat formulas. */
    Base,

    /** A second hit of the same attack that rolls accuracy separately. */
    DoubleStrike,

    /** An extra ranged attack (echo). */
    Echo,

    /** Melee damage splashed onto an adjacent enemy. */
    Cleave,

    /** A spell hit bouncing onto a nearby enemy. */
    Chain,

    /** The same attack made against an additional nearby enemy. */
    ExtraTarget,

    /** An instant attack with a random heavy melee weapon (blindbag). */
    Blindbag,

    /** Flat damage dealt back to an attacker for each hitsplat taken. */
    Thorns,

    /** A share of the damage taken dealt back to the attacker. */
    Recoil,

    /** All of a hit's damage reflected back to the attacker . */
    Reflect;

    /** `true` for every source except [Base]. Extra hits never trigger other procs. */
    public val isExtra: Boolean
        get() = this != Base

    /** `true` for sources dealt back to an attacker in response to damage taken. */
    public val isRetaliation: Boolean
        get() = this == Thorns || this == Recoil || this == Reflect
}
