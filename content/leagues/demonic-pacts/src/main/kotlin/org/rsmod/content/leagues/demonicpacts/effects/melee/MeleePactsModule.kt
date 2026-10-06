package org.rsmod.content.leagues.demonicpacts.effects.melee

import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.content.leagues.demonicpacts.effects.defence.PactThornsListener
import org.rsmod.plugin.module.PluginModule

/** Binds the melee pacts into the combat modifier pipeline and the Thorns hook. */
class MeleePactsModule : PluginModule() {
    override fun bind() {
        addSetBinding<CombatModifierProvider>(MeleePactModifiers::class.java)
        addSetBinding<CombatProcListener>(OverhealStrike::class.java)
        addSetBinding<CombatProcListener>(LightWeaponDoubleHit::class.java)
        addSetBinding<CombatProcListener>(Blindbag::class.java)
        addSetBinding<CombatProcListener>(SpecialEnergyPacts::class.java)
        addSetBinding<CombatProcListener>(DistanceHealing::class.java)
        addSetBinding<PactThornsListener>(DistanceHealing::class.java)
    }
}
