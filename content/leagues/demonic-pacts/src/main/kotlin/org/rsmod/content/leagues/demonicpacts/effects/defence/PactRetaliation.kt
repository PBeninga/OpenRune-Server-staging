package org.rsmod.content.leagues.demonicpacts.effects.defence

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.stat.stat
import org.rsmod.api.random.GameRandom
import org.rsmod.content.leagues.demonicpacts.effects.defence.DefencePactEffects.RETALIATION_DEFENCE_SCALING
import org.rsmod.content.leagues.demonicpacts.effects.defence.DefencePactEffects.SHIELD_REFLECT
import org.rsmod.content.leagues.demonicpacts.effects.defence.DefencePactEffects.THORNS
import org.rsmod.content.leagues.demonicpacts.effects.defence.DefencePactEffects.THORNS_DOUBLE_HIT
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactEffects
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType

/**
 * The pacts that hit back while a shield is equipped, on every hitsplat an npc deals to the player:
 * - **Thorns** (D1): hits the npc for the node's damage (3) as melee, also when the hit was a 0.
 * - **Double Thorns** (M1): Thorns hits a second time for half its damage, rounded down.
 * - **Reflect** (G1): a 0.1% chance per Defence level to deal the hit's damage back, as typeless
 *   damage. The player still takes the hit.
 * - **Defence scaling** (J1): Thorns and reflect hits deal 1% of the player's total defence
 *   bonuses (all five styles) more.
 *
 * Mechanic hits ([Hit.isMechanic]) and hits from players never trigger them: Jagex's league
 * turned Thorns off in PvP. The retaliation lands on the next tick, through the npc's normal hit
 * queue, so it counts as the player's damage on the npc. Each Thorns is reported to the
 * [PactThornsListener]s.
 */
@Singleton
class PactRetaliation
@Inject
constructor(
    private val pacts: DemonicPacts,
    private val npcList: NpcList,
    private val npcHitModifier: NpcHitModifier,
    private val wornBonuses: WornBonuses,
    private val random: GameRandom,
    private val thornsListeners: Set<PactThornsListener>,
) {
    fun onHitTaken(player: Player, hit: Hit) {
        if (!hit.isFromNpc || hit.isMechanic) {
            return
        }
        val effects = pacts.effects(player)
        if (THORNS !in effects && SHIELD_REFLECT !in effects) {
            return
        }
        if (WeaponClass.Shield !in WeaponClasses.worn(player)) {
            return
        }
        val attacker = hit.resolveNpcSource(npcList) ?: return
        if (!attacker.isSlotAssigned || attacker.hitpoints <= 0) {
            return
        }
        if (THORNS in effects) {
            thorns(player, attacker, effects)
        }
        if (SHIELD_REFLECT in effects && hit.damage > 0 && rollReflect(player, effects)) {
            val damage = hit.damage + defenceScaling(player, effects)
            attacker.queueHit(player, RETALIATION_DELAY, HitType.Typeless, damage, npcHitModifier)
        }
    }

    /** The extra damage J1 adds to recoil-style hits: 1% of the total defence bonuses per value. */
    fun defenceScaling(player: Player, effects: PactEffects): Int {
        val percent = effects[RETALIATION_DEFENCE_SCALING]
        if (percent <= 0) {
            return 0
        }
        val bonuses = wornBonuses.calculate(player)
        val total =
            bonuses.defStab + bonuses.defSlash + bonuses.defCrush + bonuses.defMagic +
                bonuses.defRange
        return (total * percent / 100).coerceAtLeast(0)
    }

    private fun thorns(player: Player, attacker: Npc, effects: PactEffects) {
        val damage = effects[THORNS] + defenceScaling(player, effects)
        attacker.queueHit(player, RETALIATION_DELAY, HitType.Melee, damage, npcHitModifier)
        if (THORNS_DOUBLE_HIT in effects) {
            attacker.queueHit(player, RETALIATION_DELAY, HitType.Melee, damage / 2, npcHitModifier)
        }
        for (listener in thornsListeners) {
            listener.onThorns(player, attacker)
        }
    }

    private fun rollReflect(player: Player, effects: PactEffects): Boolean {
        val perMille = player.stat(DEFENCE) * effects[SHIELD_REFLECT]
        return random.of(REFLECT_ROLL) < perMille
    }

    private companion object {
        const val RETALIATION_DELAY = 1
        const val REFLECT_ROLL = 1000
        const val DEFENCE = "stat.defence"
    }
}
