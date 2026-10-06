package org.rsmod.content.leagues.demonicpacts.effects.magic

import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.plugin.module.PluginModule

/** Binds the magic pacts into the combat modifier pipeline. */
class MagicPactsModule : PluginModule() {
    override fun bind() {
        addSetBinding<CombatModifierProvider>(MagicPactModifiers::class.java)
        addSetBinding<CombatProcListener>(MagicPactProcs::class.java)
    }
}
