package org.rsmod.content.leagues.demonicpacts.effects.ranged

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.modifiers.ModifierMath
import org.rsmod.api.combat.modifiers.ResourceConsumedEvent
import org.rsmod.api.combat.modifiers.ResourceDecision
import org.rsmod.api.combat.modifiers.ResourceKind
import org.rsmod.api.random.GameRandom
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * Fires ranged echoes: extra hits tagged [HitSource.Echo] that roll the attack's accuracy and
 * damage again ([ExtraHit.reroll]). The chance is the attack's summed
 * [HitSource.Echo] proc chance, which [RangedPactModifiers] fills in:
 * - **Ranged attacks** (B2, K9, K10, plus E2 with a crossbow) roll it only when the attack's ammo
 *   was Regenerated ([ResourceConsumedEvent.regenerated]), once per attack.
 * - **Two-handed melee attacks** (G9) roll it on every base hit that lands, as if a bow, crossbow
 *   and thrown weapon were equipped (so E2's chance is added).
 * - **Chained echoes** (K3) roll half that chance on every echo, up to
 *   [RangedPactEffects.ECHO_CHAIN_CAP] times in a row.
 *
 * Special attacks never fire echoes. Like every pipeline proc, echoes only happen against npcs.
 */
@Singleton
class RangedEchoes
@Inject
constructor(
    private val pacts: DemonicPacts,
    private val random: GameRandom,
    private val mapClock: MapClock,
) : CombatProcListener {
    override fun onResourceConsumed(event: ResourceConsumedEvent): ResourceDecision {
        val context = event.context
        if (event.regenerated && context.kind == ResourceKind.Ammo) {
            if (!pacts.effects(context.player).isEmpty) {
                context.player.attr[REGENERATED_AT] = mapClock.cycle
            }
        }
        return ResourceDecision.Consume
    }

    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (context.isSpecial) {
            return emptyList()
        }
        val chance = event.procChancePercent(HitSource.Echo)
        if (chance <= 0.0) {
            return emptyList()
        }
        if (event.isChained) {
            if (event.source != HitSource.Echo) {
                return emptyList()
            }
            return echoOn(chance / 2)
        }
        if (event.source != HitSource.Base || event.baseHitIndex != 0) {
            return emptyList()
        }
        return when (context.style) {
            CombatStyle.Ranged -> if (takeRegenerated(context.attacker)) echoOn(chance) else NONE
            CombatStyle.Melee -> if (event.rolledDamage > 0) echoOn(chance) else NONE
            CombatStyle.Magic -> NONE
        }
    }

    private fun takeRegenerated(player: Player): Boolean {
        val cycle = player.attr[REGENERATED_AT] ?: return false
        player.attr.remove(REGENERATED_AT)
        return cycle == mapClock.cycle
    }

    private fun echoOn(chancePercent: Double): List<ExtraHit> {
        val basisPoints = ModifierMath.chanceBasisPoints(chancePercent)
        val echo =
            when {
                basisPoints <= 0 -> false
                basisPoints >= ModifierMath.CHANCE_SCALE -> true
                else -> random.of(maxExclusive = ModifierMath.CHANCE_SCALE) < basisPoints
            }
        return if (echo) listOf(ExtraHit.reroll(HitSource.Echo)) else NONE
    }

    private companion object {
        val REGENERATED_AT: AttributeKey<Int> = AttributeKey()
        val NONE: List<ExtraHit> = emptyList()
    }
}
