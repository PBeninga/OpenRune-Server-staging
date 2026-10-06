package org.rsmod.content.leagues.demonicpacts.effects.resources

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.specials.energy.SpecialAttackEnergyHook
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.FREE_SPECIAL
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.entity.Player

/** G2: special attacks have a 20% chance to consume no energy, against npcs and players alike. */
@Singleton
class PactFreeSpecials @Inject constructor(private val pacts: DemonicPacts) :
    SpecialAttackEnergyHook {
    override fun freeChancePercent(player: Player, energy: Int): Double =
        pacts.effects(player)[FREE_SPECIAL].toDouble()
}
