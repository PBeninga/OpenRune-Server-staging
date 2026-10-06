package org.rsmod.content.leagues.demonicpacts.effects.resources

import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.player.stat.StatBoostProvider
import org.rsmod.api.specials.energy.SpecialAttackEnergyHook
import org.rsmod.plugin.module.PluginModule

/** Binds the Regenerate and resource pacts into the generic hooks they use. */
class ResourcePactsModule : PluginModule() {
    override fun bind() {
        addSetBinding<CombatModifierProvider>(ResourcePactModifiers::class.java)
        addSetBinding<CombatProcListener>(ResourcePactProcs::class.java)
        addSetBinding<StatBoostProvider>(PactRegenerateBoosts::class.java)
        addSetBinding<SpecialAttackEnergyHook>(PactFreeSpecials::class.java)
    }
}
