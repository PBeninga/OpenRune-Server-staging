package org.rsmod.api.combat.modifiers

import jakarta.inject.Inject
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.modify
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder

/**
 * The combat modifier decorator of OpenRune's `PlayerHitModifier` binding, installed by
 * [CombatModifiersModule] over the default `StandardPlayerHitModifier` (see `EntityHitModule`).
 *
 * It applies [StandardPlayerHitModifier] (admin god mode, protection prayers, absorption) and then
 * the damage-received pipeline ([CombatModifierPipeline.receiveHit]). Every hit that is
 * queued with the injected `PlayerHitModifier` goes through it: generic NvP combat (`NvPCombat`),
 * every boss DSL hit (`EffectInterpreter` uses `BossDeps.playerHitModifier`) and boss scripts that
 * wrap that modifier. Hits from players, and hits queued with another modifier (for example
 * `NoopPlayerHitModifier`), are not affected.
 *
 * Mechanic damage is flagged with `HitBuilder.isMechanic` (see `PlayerHitModifier.asMechanic()`),
 * and, like typeless damage, bypasses dodge and damage reduction.
 */
public class CombatModifierPlayerHitModifier
@Inject
constructor(
    private val standard: StandardPlayerHitModifier,
    private val pipeline: CombatModifierPipeline,
) : PlayerHitModifier {
    override fun HitBuilder.modify(target: Player) {
        val rolledDamage = damage
        standard.modify(this, target)
        pipeline.receiveHit(this, target, rolledDamage)
    }
}
