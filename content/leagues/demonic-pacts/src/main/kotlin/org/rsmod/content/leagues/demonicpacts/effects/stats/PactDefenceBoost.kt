package org.rsmod.content.leagues.demonicpacts.effects.stats

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.rsmod.api.player.stat.StatBoostProvider
import org.rsmod.api.player.stat.StatBoosts
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.DEFENCE_BOOST
import org.rsmod.content.leagues.demonicpacts.effects.stats.StatPactEffects.DEFENCE_STAT
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactEffects
import org.rsmod.content.leagues.demonicpacts.state.PactEffectsListener
import org.rsmod.game.entity.Player

/**
 * The Defence nodes' permanent boost: their summed levels raise the Defence resting level, so the
 * boost never decays, comes back after restores and death, and leaves the base level (and so the
 * combat level) alone. See [StatBoosts].
 */
@Singleton
class PactDefenceBoost @Inject constructor(private val pacts: DemonicPacts) : StatBoostProvider {
    override fun permanentBoosts(player: Player): Map<String, Int> {
        val levels = pacts.effects(player)[DEFENCE_BOOST]
        return if (levels > 0) mapOf(DEFENCE_STAT to levels) else emptyMap()
    }
}

/**
 * Applies a changed [PactDefenceBoost] to the Defence level after every pact refresh (commit,
 * reset, admin set). [StatBoosts] comes through a [Provider] because it depends on every
 * [StatBoostProvider], and so on [DemonicPacts], which depends on this listener.
 */
@Singleton
class PactDefenceBoostRefresh @Inject constructor(private val boosts: Provider<StatBoosts>) :
    PactEffectsListener {
    override fun onEffectsRefreshed(player: Player, effects: PactEffects) {
        boosts.get().refresh(player)
    }
}
