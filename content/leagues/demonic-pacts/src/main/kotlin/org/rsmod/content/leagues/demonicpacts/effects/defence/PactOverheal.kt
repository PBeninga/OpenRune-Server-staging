package org.rsmod.content.leagues.demonicpacts.effects.defence

import jakarta.inject.Inject
import org.rsmod.api.player.stat.OverhealProvider
import org.rsmod.api.player.stat.PlayerHealSource
import org.rsmod.content.leagues.demonicpacts.effects.defence.DefencePactEffects.OVERHEAL
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.entity.Player

/**
 * F2, F13 and H9: healing from pacts overheals up to +30% of the base Hitpoints level per node. The
 * nodes stack like every other repeated effect (+90% with all three), which is the total the tree's
 * "Active Pact Info" panel shows. Only [PlayerHealSource.Pact] healing overheals.
 */
class PactOverheal @Inject constructor(private val pacts: DemonicPacts) : OverhealProvider {
    override fun overhealPercent(player: Player, source: PlayerHealSource): Double {
        if (source != PlayerHealSource.Pact) {
            return 0.0
        }
        return pacts.effects(player)[OVERHEAL].toDouble()
    }
}
