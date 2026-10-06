package org.rsmod.api.player.stat

/**
 * Where healing comes from, as handed to [OverhealProvider]. Food, potions and regeneration are
 * [Standard] and stop at the hitpoints resting level; the other sources may overheal when a
 * provider allows it.
 */
public enum class PlayerHealSource {
    /** Healing that never overheals. */
    Standard,

    /** Healing from a Demonic Pact effect (Leagues). */
    Pact,

    /** Healing from a relic effect (Leagues). */
    Relic,
}
