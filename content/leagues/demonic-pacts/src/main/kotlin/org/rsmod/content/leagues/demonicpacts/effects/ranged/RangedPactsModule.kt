package org.rsmod.content.leagues.demonicpacts.effects.ranged

import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.content.leagues.demonicpacts.state.PactResetListener
import org.rsmod.plugin.module.PluginModule

/** Binds the ranged and echo pacts into the combat modifier pipeline. */
class RangedPactsModule : PluginModule() {
    override fun bind() {
        addSetBinding<CombatModifierProvider>(RangedPactModifiers::class.java)
        addSetBinding<CombatProcListener>(StyleSwap::class.java)
        addSetBinding<CombatProcListener>(BowStacks::class.java)
        addSetBinding<CombatProcListener>(RangedEchoes::class.java)
        addSetBinding<CombatProcListener>(ThrownExtraTarget::class.java)
        addSetBinding<PactResetListener>(RangedPactResets::class.java)
    }
}
