package org.rsmod.api.specials.energy

import jakarta.inject.Inject
import kotlin.math.roundToInt
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

public class SpecialAttackEnergy
@Inject
constructor(
    private val hooks: Set<SpecialAttackEnergyHook>,
    private val random: GameRandom,
) {
    private var Player.specialEnergy by intVarp("varp.sa_energy")

    public fun hasSpecialEnergy(
        player: Player,
        energyInHundreds: Int,
    ): Boolean {
        val cost =
            SpecialAttackEnergyModifier.adjustedCost(
                player = player,
                baseCost = energyInHundreds,
            )

        return player.specialEnergy >= cost
    }

    public fun takeSpecialEnergy(
        player: Player,
        energyInHundreds: Int,
    ) {
        val cost =
            SpecialAttackEnergyModifier.adjustedCost(
                player = player,
                baseCost = energyInHundreds,
            )

        require(player.specialEnergy >= cost) {
            "Not enough special energy to take. " +
                "Use `hasSpecialEnergy` first for validation."
        }

        if (isFree(player, cost)) {
            return
        }

        player.specialEnergy -= cost
    }

    public fun addSpecialEnergy(player: Player, energyInHundreds: Int) {
        player.specialEnergy = (player.specialEnergy + energyInHundreds).coerceAtMost(MAX_ENERGY)
    }

    private fun isFree(player: Player, cost: Int): Boolean {
        if (hooks.isEmpty()) {
            return false
        }
        val chance = hooks.sumOf { it.freeChancePercent(player, cost) }
        val basisPoints = (chance * 100).roundToInt()
        return when {
            basisPoints <= 0 -> false
            basisPoints >= CHANCE_SCALE -> true
            else -> random.of(maxExclusive = CHANCE_SCALE) < basisPoints
        }
    }

    public fun isSpecializedRequirement(energyInHundreds: Int): Boolean {
        return energyInHundreds < 10
    }

    public companion object {
        public const val MAX_ENERGY: Int = 1000

        private const val CHANCE_SCALE: Int = 10_000
    }
}
