package org.rsmod.api.combat.modifiers

import jakarta.inject.Inject
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerHit
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Forwards landed hits to the combat modifier pipeline: hits on npcs to
 * [CombatModifierPipeline.onNpcHitImpact] ([CombatProcListener.onHitDealt]) and hits on players to
 * [CombatModifierPipeline.onPlayerHitImpact] (retaliation such as thorns and recoil).
 */
public class CombatModifierHitImpactScript
@Inject
constructor(private val pipeline: CombatModifierPipeline) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcHitEvents.AnyImpact> { pipeline.onNpcHitImpact(npc, hit) }
        onPlayerHit { pipeline.onPlayerHitImpact(player, hit) }
    }
}
