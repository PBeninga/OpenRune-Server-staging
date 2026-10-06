package org.rsmod.api.combat.modifiers

import com.google.inject.Scopes
import com.google.inject.multibindings.OptionalBinder
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.plugin.module.PluginModule

/**
 * Declares the combat modifier set bindings, binds the pipeline as a singleton and decorates
 * OpenRune's `PlayerHitModifier` binding with [CombatModifierPlayerHitModifier].
 *
 * Feature modules contribute with `addSetBinding<CombatModifierProvider>(...)`,
 * `addSetBinding<CombatProcListener>(...)` and `addSetBinding<NpcCombatRules>(...)` from their own
 * `PluginModule`.
 */
public class CombatModifiersModule : PluginModule() {
    override fun bind() {
        newSetBinding<CombatModifierProvider>()
        newSetBinding<CombatProcListener>()
        newSetBinding<NpcCombatRules>()
        bindInstance<CombatModifierRegistry>()
        bindInstance<CombatModifierPipeline>()
        OptionalBinder.newOptionalBinder(binder(), PlayerHitModifier::class.java)
            .setBinding()
            .to(CombatModifierPlayerHitModifier::class.java)
            .`in`(Scopes.SINGLETON)
    }
}
