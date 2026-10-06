package org.rsmod.content.leagues.demonicpacts.effects.melee

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitDealtEvent
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.modifiers.ModifierMath
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.api.player.stat.PlayerHealSource
import org.rsmod.api.player.stat.PlayerHealing
import org.rsmod.api.random.GameRandom
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.content.leagues.demonicpacts.effects.defence.PactThornsListener
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.DISTANCE_HEALING
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.DISTANCE_SPECIAL_ENERGY
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.DISTANCE_SPECIAL_ENERGY_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.DISTANCE_SPECIAL_MIN_TILES
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.DOUBLE_HIT_DAMAGE_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.ENERGY_PER_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.HIT_SPECIAL_ENERGY
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.LIGHT_WEAPON_DOUBLE_HIT
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.OVERHEAL_COST
import org.rsmod.content.leagues.demonicpacts.effects.melee.MeleePactEffects.OVERHEAL_MIN_HIT
import org.rsmod.content.leagues.demonicpacts.effects.tilesTo
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** `true` for the first base hit of an attack, which the once-per-attack pacts trigger on. */
private val HitRolledEvent.isAttack: Boolean
    get() = source == HitSource.Base && !isChained && baseHitIndex == 0

/**
 * D2: every melee attack with a weapon under 1kg hits again ([HitSource.DoubleStrike]), rolling
 * accuracy again and dealing 40% of what it rolls (at least 1 when it hits). Not on special
 * attacks.
 */
@Singleton
class LightWeaponDoubleHit @Inject constructor(private val pacts: DemonicPacts) :
    CombatProcListener {
    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (!event.isAttack || context.isSpecial || context.style != CombatStyle.Melee) {
            return emptyList()
        }
        if (LIGHT_WEAPON_DOUBLE_HIT !in pacts.effects(context.attacker)) {
            return emptyList()
        }
        val classes = context.weapon?.let(WeaponClasses::of) ?: return emptyList()
        if (WeaponClass.Melee !in classes || WeaponClass.Light !in classes) {
            return emptyList()
        }
        return listOf(ExtraHit.reroll(HitSource.DoubleStrike, DOUBLE_HIT_DAMAGE_PERCENT))
    }
}

/**
 * The special energy pacts:
 * - **G4:** an attack, any style, from at least 2 tiles away restores 2% special energy.
 * - **M2:** each melee hit that deals damage restores 2% (the node's value) special energy,
 *   extra hits (D2, Blindbag) included.
 */
@Singleton
class SpecialEnergyPacts
@Inject
constructor(private val pacts: DemonicPacts, private val energy: SpecialAttackEnergy) :
    CombatProcListener {
    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (!event.isAttack || DISTANCE_SPECIAL_ENERGY !in pacts.effects(context.attacker)) {
            return emptyList()
        }
        if (context.attacker.tilesTo(context.target) >= DISTANCE_SPECIAL_MIN_TILES) {
            energy.addSpecialEnergy(
                context.attacker,
                DISTANCE_SPECIAL_ENERGY_PERCENT * ENERGY_PER_PERCENT,
            )
        }
        return emptyList()
    }

    override fun onHitDealt(event: HitDealtEvent) {
        if (event.style != CombatStyle.Melee || event.damage <= 0) {
            return
        }
        val percent = pacts.effects(event.attacker)[HIT_SPECIAL_ENERGY]
        if (percent > 0) {
            energy.addSpecialEnergy(event.attacker, percent * ENERGY_PER_PERCENT)
        }
    }
}

/**
 * G5 and G10: each melee attack, and each Thorns hit, has the nodes' summed chance (10% each) to
 * heal the player 1 hitpoint per tile between them and the target. It is pact healing, so it can
 * overheal (F2, F13, H9).
 */
@Singleton
class DistanceHealing
@Inject
constructor(
    private val pacts: DemonicPacts,
    private val healing: PlayerHealing,
    private val random: GameRandom,
) : CombatProcListener, PactThornsListener {
    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (event.isAttack && context.style == CombatStyle.Melee) {
            heal(context.attacker, event.target)
        }
        return emptyList()
    }

    override fun onThorns(player: Player, attacker: Npc) {
        heal(player, attacker)
    }

    private fun heal(player: Player, target: Npc) {
        val chance = pacts.effects(player)[DISTANCE_HEALING]
        if (chance <= 0 || !roll(chance)) {
            return
        }
        healing.heal(player, player.tilesTo(target), PlayerHealSource.Pact)
    }

    private fun roll(chancePercent: Int): Boolean {
        val basisPoints = ModifierMath.chanceBasisPoints(chancePercent.toDouble())
        if (basisPoints >= ModifierMath.CHANCE_SCALE) {
            return true
        }
        return random.of(maxExclusive = ModifierMath.CHANCE_SCALE) < basisPoints
    }
}

/**
 * G8's cost: a melee attack spends 5 overhealed hitpoints when the player has at least 5, and
 * [MeleePactModifiers] gives that attack +5 min hit. The hitpoints are spent once the attack's
 * first hit is rolled, hit or miss.
 */
@Singleton
class OverhealStrike
@Inject
constructor(private val pacts: DemonicPacts, private val healing: PlayerHealing) :
    CombatProcListener {
    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (!event.isAttack || context.style != CombatStyle.Melee) {
            return emptyList()
        }
        if (OVERHEAL_MIN_HIT in pacts.effects(context.attacker)) {
            healing.consumeOverheal(context.attacker, OVERHEAL_COST)
        }
        return emptyList()
    }
}
