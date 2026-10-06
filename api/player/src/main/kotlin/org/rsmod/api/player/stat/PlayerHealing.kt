package org.rsmod.api.player.stat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import org.rsmod.game.entity.Player

/**
 * Heals players for effects that may overheal: raise hitpoints above the resting level, up to a
 * percentage of the base level given by the [OverhealProvider]s.
 *
 * Hitpoints above the resting level decay by 1 per stat boost restore interval, like any boosted
 * stat (`StatRegenScript`). Food and potions never overheal.
 */
@Singleton
public class PlayerHealing @Inject constructor(providers: Set<OverhealProvider>) {
    private val providers = providers.toList()

    /** The overheal percentage [source] grants [player]: the highest of every provider. */
    public fun overhealPercent(player: Player, source: PlayerHealSource): Double {
        if (source == PlayerHealSource.Standard || providers.isEmpty()) {
            return 0.0
        }
        return max(0.0, providers.maxOf { it.overhealPercent(player, source) })
    }

    /** The highest hitpoints level [source] may heal [player] to (see [overhealCap]). */
    public fun healCap(player: Player, source: PlayerHealSource): Int =
        overhealCap(
            restingLevel = player.statRestingLevel(HITPOINTS),
            baseLevel = player.baseHitpointsLvl,
            overhealPercent = overhealPercent(player, source),
        )

    /**
     * Heals [player] by up to [amount] hitpoints without passing [healCap]. Hitpoints already at
     * or above the cap are left unchanged.
     *
     * @return The hitpoints actually healed.
     */
    public fun heal(player: Player, amount: Int, source: PlayerHealSource): Int {
        if (amount <= 0) {
            return 0
        }
        val healed = min(amount, healCap(player, source) - player.hitpoints)
        if (healed <= 0) {
            return 0
        }
        player.statAdd(HITPOINTS, healed, 0)
        return healed
    }

    /** The hitpoints [player] has above the resting level (overheal or boosts). */
    public fun overhealed(player: Player): Int =
        max(0, player.hitpoints - player.statRestingLevel(HITPOINTS))

    /**
     * Spends [amount] overhealed hitpoints. Does nothing unless [player] has at least
     * [amount] hitpoints above the resting level.
     *
     * @return `true` if the hitpoints were spent.
     */
    public fun consumeOverheal(player: Player, amount: Int): Boolean {
        if (amount <= 0 || overhealed(player) < amount) {
            return false
        }
        player.statSub(HITPOINTS, amount, 0)
        return true
    }

    public companion object {
        private const val HITPOINTS = "stat.hitpoints"

        /**
         * The overheal cap: `restingLevel + floor(baseLevel × overhealPercent / 100)`. Negative
         * percentages count as `0`.
         */
        public fun overhealCap(restingLevel: Int, baseLevel: Int, overhealPercent: Double): Int {
            if (overhealPercent <= 0.0 || baseLevel <= 0) {
                return restingLevel
            }
            val basisPoints = Math.round(overhealPercent * 100)
            return restingLevel + (baseLevel.toLong() * basisPoints / 10_000).toInt()
        }
    }
}
