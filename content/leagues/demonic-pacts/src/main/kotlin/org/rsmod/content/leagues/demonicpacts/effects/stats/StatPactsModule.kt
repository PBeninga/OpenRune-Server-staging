package org.rsmod.content.leagues.demonicpacts.effects.stats

import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.player.stat.StatBoostProvider
import org.rsmod.content.leagues.demonicpacts.state.PactEffectsListener
import org.rsmod.plugin.module.PluginModule

/** Binds the flat stat pacts into the combat modifier pipeline and the stat boost hook. */
class StatPactsModule : PluginModule() {
    override fun bind() {
        addSetBinding<CombatModifierProvider>(StatPactModifiers::class.java)
        addSetBinding<StatBoostProvider>(PactDefenceBoost::class.java)
        addSetBinding<PactEffectsListener>(PactDefenceBoostRefresh::class.java)
    }
}
