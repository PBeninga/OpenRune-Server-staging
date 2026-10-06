package org.rsmod.content.leagues.demonicpacts.effects.ranged

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.mapMultiway
import org.rsmod.api.route.RayCastValidator
import org.rsmod.content.leagues.demonicpacts.effects.ranged.RangedPactEffects.THROWN_EXTRA_TARGET
import org.rsmod.content.leagues.demonicpacts.effects.tilesTo
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp

/**
 * N9: a thrown weapon attack is made again, with its own accuracy and damage rolls, against the
 * closest other attackable npc within [NEARBY_TILES] of the target ([ExtraHit.retarget], tagged
 * [HitSource.ExtraTarget]).
 *
 * Like chinchompas and ancient bursts, the second throw needs both npcs in a multi-combat area and
 * a clear line from the target to the second npc. Special attacks don't throw twice.
 */
@Singleton
class ThrownExtraTarget
@Inject
constructor(
    private val pacts: DemonicPacts,
    private val hunt: Hunt,
    private val areaChecker: AreaChecker,
    private val rayCast: RayCastValidator,
    private val attackValidators: Set<NpcAttackValidateHook>,
) : CombatProcListener {
    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (event.source != HitSource.Base || event.baseHitIndex != 0 || context.isSpecial) {
            return emptyList()
        }
        if (context.style != CombatStyle.Ranged) {
            return emptyList()
        }
        if (THROWN_EXTRA_TARGET !in pacts.effects(context.attacker)) {
            return emptyList()
        }
        val weapon = context.weapon ?: return emptyList()
        if (WeaponClass.Thrown !in WeaponClasses.of(weapon)) {
            return emptyList()
        }
        val second = findTarget(context.attacker, event.target) ?: return emptyList()
        return listOf(ExtraHit.retarget(HitSource.ExtraTarget, second))
    }

    /** The npc a thrown weapon's second throw would hit after [player] attacks [primary]. */
    fun findTarget(player: Player, primary: Npc): Npc? {
        if (!primary.mapMultiway(areaChecker)) {
            return null
        }
        val radius = NEARBY_TILES + primary.size + LARGEST_NPC_SIZE
        return hunt
            .findNpcs(primary.coords, radius, HuntVis.Off)
            .filter { it !== primary && primary.tilesTo(it) <= NEARBY_TILES }
            .filter { canHit(player, primary, it) }
            .minByOrNull { primary.tilesTo(it) }
    }

    private fun canHit(player: Player, primary: Npc, npc: Npc): Boolean {
        if (!npc.isValidTarget() || !npc.visType.hasOp(InteractionOp.Op2.slot)) {
            return false
        }
        if (!npc.mapMultiway(areaChecker) || npc.coords.level != primary.coords.level) {
            return false
        }
        val lineOfSight =
            rayCast.hasLineOfSight(
                source = primary.coords,
                destination = npc.coords,
                srcWidth = primary.size,
                srcLength = primary.size,
                destWidth = npc.size,
                destLength = npc.size,
            )
        if (!lineOfSight) {
            return false
        }
        return attackValidators.none { it.validate(player, npc) is NpcAttackValidateResult.Deny }
    }

    companion object {
        /** How far, in tiles from the target, the second throw reaches. */
        const val NEARBY_TILES: Int = 3

        private const val LARGEST_NPC_SIZE = 5
    }
}
