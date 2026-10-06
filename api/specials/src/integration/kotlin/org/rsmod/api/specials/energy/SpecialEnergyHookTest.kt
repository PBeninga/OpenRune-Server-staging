package org.rsmod.api.specials.energy

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.Player

/** [SpecialAttackEnergyHook]: free special attacks. */
class SpecialEnergyHookTest {
    @Test
    fun GameTestState.`a free special attack chance skips taking energy`() {
        val hook = TestEnergyHook()
        runInjectedGameTest(EnergyDeps::class, hook.module) { deps ->
            val energy = deps.energy
            player.setVarp("varp.sa_energy", 1_000)

            // Nothing hooked: energy is taken without a random roll.
            energy.takeSpecialEnergy(player, 250)
            assertEquals(750, player.vars["varp.sa_energy"])

            // 20% = 2,000 basis points: a roll of 1,999 is free, 2,000 is not.
            hook.chance = 20.0
            random.next = 1_999
            energy.takeSpecialEnergy(player, 250)
            assertEquals(750, player.vars["varp.sa_energy"])
            random.next = 2_000
            energy.takeSpecialEnergy(player, 250)
            assertEquals(500, player.vars["varp.sa_energy"])
            assertEquals(listOf(250, 250), hook.costs)

            hook.chance = 100.0
            energy.takeSpecialEnergy(player, 500)
            assertEquals(500, player.vars["varp.sa_energy"])
        }
    }

    @Test
    fun GameTestState.`added special energy is capped at full energy`() {
        runInjectedGameTest(EnergyDeps::class) { deps ->
            player.setVarp("varp.sa_energy", 970)
            deps.energy.addSpecialEnergy(player, 20)
            assertEquals(990, player.vars["varp.sa_energy"])
            deps.energy.addSpecialEnergy(player, 20)
            assertEquals(1_000, player.vars["varp.sa_energy"])
        }
    }

    class EnergyDeps @Inject constructor(val energy: SpecialAttackEnergy)

    private class TestEnergyHook : SpecialAttackEnergyHook {
        var chance: Double = 0.0
        val costs: MutableList<Int> = mutableListOf()

        val module =
            object : AbstractModule() {
                override fun configure() {
                    Multibinder.newSetBinder(binder(), SpecialAttackEnergyHook::class.java)
                        .addBinding()
                        .toInstance(this@TestEnergyHook)
                }
            }

        override fun freeChancePercent(player: Player, energy: Int): Double {
            if (chance > 0.0) {
                costs += energy
            }
            return chance
        }
    }
}
