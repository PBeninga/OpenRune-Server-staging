package org.rsmod.content.leagues.demonicpacts.effects.magic

/**
 * The effect ids (the `effect` column of `dbtable.talent_tree`) of the magic pacts, and the
 * constants their tooltips state but the tree doesn't store. The value each node gives is read
 * from the tree, so a server that overrides a node's value changes the effect too.
 */
object MagicPactEffects {
    /** I1: air spells deal this % more damage for each active prayer. */
    const val AIR_DAMAGE_PER_PRAYER: Int = 83

    /** I2: water spells deal up to [WATER_DAMAGE_PERCENT]% more damage, scaled by Hitpoints. */
    const val WATER_DAMAGE_HIGH_HITPOINTS: Int = 29

    /** I3: fire spell casts burn up to 6% of the Hitpoints level for twice that in damage. */
    const val FIRE_HITPOINTS_FOR_DAMAGE: Int = 32

    /** I4: earth spell hits lower the npc's Defence and Magic levels by [EARTH_DRAIN]. */
    const val EARTH_DEFENCE_DRAIN: Int = 42

    /** L1: air spells get this % chance to max hit per prayer bonus (doubled on air weakness). */
    const val AIR_MAX_HIT_PER_PRAYER_BONUS: Int = 30

    /** L2: water spell hits heal [WATER_HEAL_PERCENT]% of the damage dealt. */
    const val WATER_HEAL: Int = 31

    /** L3: fire spell hits burn the npc and bounce to [FIRE_BOUNCE_TARGETS] nearby npcs. */
    const val FIRE_BURN_BOUNCE: Int = 33

    /** L4: earth spells deal 1 more damage per this many current Defence levels. */
    const val EARTH_DAMAGE_PER_DEFENCE: Int = 41

    /** L5: smoke spells count as air spells. */
    const val SMOKE_AS_AIR: Int = 45

    /** L6: ice spells count as water spells. */
    const val ICE_AS_WATER: Int = 46

    /** L7: blood spells count as fire spells. */
    const val BLOOD_AS_FIRE: Int = 47

    /** L8: shadow spells count as earth spells. */
    const val SHADOW_AS_EARTH: Int = 48

    /** F7: spellbook combat spells attack this many ticks faster. */
    const val SPELL_SPEED: Int = 34

    /** F8: powered staves attack this many ticks faster; one-handed ones lose max hit. */
    const val POWERED_STAFF_SPEED: Int = 81

    const val WATER_DAMAGE_PERCENT: Double = 20.0
    const val FIRE_BURN_HITPOINTS_PERCENT: Int = 6
    const val FIRE_BURN_DAMAGE_MULTIPLIER: Int = 2
    const val EARTH_DRAIN: Int = 2
    const val WATER_HEAL_PERCENT: Int = 60
    const val FIRE_BOUNCE_TARGETS: Int = 2
    const val SPELL_SPEED_FLOOR: Int = 2
    const val POWERED_STAFF_SPEED_FLOOR: Int = 1
    const val ONE_HANDED_STAFF_MAX_HIT: Int = -8

    val ids: Set<Int> =
        setOf(
            AIR_DAMAGE_PER_PRAYER,
            WATER_DAMAGE_HIGH_HITPOINTS,
            FIRE_HITPOINTS_FOR_DAMAGE,
            EARTH_DEFENCE_DRAIN,
            AIR_MAX_HIT_PER_PRAYER_BONUS,
            WATER_HEAL,
            FIRE_BURN_BOUNCE,
            EARTH_DAMAGE_PER_DEFENCE,
            SMOKE_AS_AIR,
            ICE_AS_WATER,
            BLOOD_AS_FIRE,
            SHADOW_AS_EARTH,
            SPELL_SPEED,
            POWERED_STAFF_SPEED,
        )
}
