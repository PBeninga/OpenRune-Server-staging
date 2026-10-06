package org.rsmod.api.combat.modifiers

import jakarta.inject.Inject

/**
 * Every registered [CombatModifierProvider], [CombatProcListener] and [NpcCombatRules], as
 * collected by their Guice `Multibinder`s (declared by [CombatModifiersModule]).
 *
 * Tests can bind an instance of this class directly to run combat with test-only providers and
 * listeners.
 */
public class CombatModifierRegistry
@Inject
constructor(
    public val providers: Set<CombatModifierProvider>,
    public val listeners: Set<CombatProcListener>,
    public val npcRules: Set<NpcCombatRules>,
) {
    /** `true` when nothing is registered and the pipeline is a no-op. */
    public val isEmpty: Boolean
        get() = providers.isEmpty() && listeners.isEmpty() && npcRules.isEmpty()

    override fun toString(): String =
        "CombatModifierRegistry(" +
            "providers=${providers.size}, " +
            "listeners=${listeners.size}, " +
            "npcRules=${npcRules.size}" +
            ")"

    public companion object {
        /** A registry with nothing registered. */
        public val EMPTY: CombatModifierRegistry =
            CombatModifierRegistry(emptySet(), emptySet(), emptySet())

        /** Builds a registry from explicit instances, mostly for tests. */
        public fun of(
            providers: Collection<CombatModifierProvider> = emptyList(),
            listeners: Collection<CombatProcListener> = emptyList(),
            npcRules: Collection<NpcCombatRules> = emptyList(),
        ): CombatModifierRegistry =
            CombatModifierRegistry(
                providers = LinkedHashSet(providers),
                listeners = LinkedHashSet(listeners),
                npcRules = LinkedHashSet(npcRules),
            )
    }
}
