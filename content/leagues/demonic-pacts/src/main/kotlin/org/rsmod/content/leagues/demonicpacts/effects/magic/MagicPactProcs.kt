package org.rsmod.content.leagues.demonicpacts.effects.magic

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.magic.SpellElement
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitDealtEvent
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.mapMultiway
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.stat.PlayerHealSource
import org.rsmod.api.player.stat.PlayerHealing
import org.rsmod.api.route.RayCastValidator
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.EARTH_DEFENCE_DRAIN
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.EARTH_DRAIN
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.FIRE_BOUNCE_TARGETS
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.FIRE_BURN_BOUNCE
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.FIRE_HITPOINTS_FOR_DAMAGE
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.WATER_HEAL
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.WATER_HEAL_PERCENT
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp

/**
 * The magic pacts that react to a spell's hits:
 * - **I3:** a fire spell cast takes the Hitpoints [MagicPactModifiers.fireBurn] says, as a typeless
 *   hitsplat, once per cast, whether the spell lands or splashes.
 * - **I4:** a landed earth spell hit lowers the npc's Defence and Magic levels by 2 (not below 0).
 * - **L2:** a landed water spell hit heals 60% of its damage, rounded down, as pact healing (so it
 *   can overheal with F2, F13 or H9).
 * - **L3:** a landed fire spell hit adds a burn stack ([PactBurns]) and the spell bounces to the 2
 *   closest other attackable npcs within [BOUNCE_TILES] tiles of the target, each with its own
 *   accuracy and damage rolls ([ExtraHit.retarget], tagged [HitSource.Chain]). Like ancient bursts,
 *   a bounce needs both npcs in a multi-combat area and a clear line between them.
 *
 * A spell's element is the one it counts as ([PactSpellElements]). Only a spell's own hits trigger
 * these: bounced hits don't burn, heal, drain or bounce again. The pipeline sends no proc event for
 * hits on players, so none of them work in PvP.
 */
@Singleton
class MagicPactProcs
@Inject
constructor(
    private val pacts: DemonicPacts,
    private val modifiers: MagicPactModifiers,
    private val burns: PactBurns,
    private val healing: PlayerHealing,
    private val hitProcessor: InstantPlayerHitProcessor,
    private val hunt: Hunt,
    private val areaChecker: AreaChecker,
    private val rayCast: RayCastValidator,
    private val attackValidators: Set<NpcAttackValidateHook>,
) : CombatProcListener {
    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (event.source != HitSource.Base || event.baseHitIndex != 0) {
            return emptyList()
        }
        if (context.style != CombatStyle.Magic || context.spell == null) {
            return emptyList()
        }
        val effects = pacts.effects(context.attacker)
        if (PactSpellElements.of(context.spell, effects) != SpellElement.Fire) {
            return emptyList()
        }
        if (FIRE_HITPOINTS_FOR_DAMAGE in effects) {
            burnHitpoints(context.attacker)
        }
        if (FIRE_BURN_BOUNCE !in effects || event.rolledDamage <= 0) {
            return emptyList()
        }
        return bounceTargets(context.attacker, event.target).map {
            ExtraHit.retarget(HitSource.Chain, it)
        }
    }

    override fun onHitDealt(event: HitDealtEvent) {
        if (event.source != HitSource.Base || event.style != CombatStyle.Magic) {
            return
        }
        if (event.damage <= 0) {
            return
        }
        val effects = pacts.effects(event.attacker)
        if (effects.isEmpty) {
            return
        }
        when (PactSpellElements.of(event.hit.secondaryType(), effects)) {
            SpellElement.Water ->
                if (WATER_HEAL in effects) {
                    val heal = event.damage * WATER_HEAL_PERCENT / 100
                    healing.heal(event.attacker, heal, PlayerHealSource.Pact)
                }
            SpellElement.Earth ->
                if (EARTH_DEFENCE_DRAIN in effects) {
                    drain(event.target)
                }
            SpellElement.Fire ->
                if (FIRE_BURN_BOUNCE in effects) {
                    burns.apply(event.attacker, event.target)
                }
            else -> {}
        }
    }

    /** The npcs a fire spell on [primary] bounces to, closest first. */
    fun bounceTargets(player: Player, primary: Npc): List<Npc> {
        if (!primary.mapMultiway(areaChecker)) {
            return emptyList()
        }
        val radius = BOUNCE_TILES + primary.size + LARGEST_NPC_SIZE
        return hunt
            .findNpcs(primary.coords, radius, HuntVis.Off)
            .filter { it !== primary && primary.tilesTo(it) <= BOUNCE_TILES }
            .filter { canHit(player, primary, it) }
            .sortedBy { primary.tilesTo(it) }
            .take(FIRE_BOUNCE_TARGETS)
            .toList()
    }

    private fun burnHitpoints(player: Player) {
        val burn = modifiers.fireBurn(player)
        if (burn > 0) {
            player.takeInstantHit(HitType.Typeless, burn, hitProcessor)
        }
    }

    private fun drain(npc: Npc) {
        npc.defenceLvl = (npc.defenceLvl - EARTH_DRAIN).coerceAtLeast(0)
        npc.magicLvl = (npc.magicLvl - EARTH_DRAIN).coerceAtLeast(0)
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

    private fun Npc.tilesTo(other: Npc): Int = coords.chebyshevDistance(other.coords)

    companion object {
        /** How far, in tiles from the target, a fire spell bounces. */
        const val BOUNCE_TILES: Int = 3

        private const val LARGEST_NPC_SIZE = 5
    }
}
