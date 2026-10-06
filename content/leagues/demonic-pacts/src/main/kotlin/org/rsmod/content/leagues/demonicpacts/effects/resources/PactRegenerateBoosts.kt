package org.rsmod.content.leagues.demonicpacts.effects.resources

import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.stat.StatBoostProvider
import org.rsmod.api.player.stat.StatBoosts
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.BOOST_DURATION_TICKS
import org.rsmod.game.entity.Player

/** The timed boosts the Regenerate pacts give. */
enum class PactRegenerateBoost(val stat: String, val timer: String) {
    /** B1: Magic. */
    Magic("stat.magic", "timer.demonic_pacts_magic_boost"),

    /** C4: Defence. */
    Defence("stat.defence", "timer.demonic_pacts_defence_boost");

    internal val amount: AttributeKey<Int> = AttributeKey(temp = true)
}

/**
 * The B1 and C4 boosts: timed, capped boosts that raise the stat's resting level ([StatBoosts]),
 * so they don't decay while they last. Each trigger adds to the boost up to its cap and restarts
 * its [BOOST_DURATION_TICKS] timer; when the timer fires the boost ends and the stat drops back.
 *
 * The amounts are runtime state: a logout ends them, and the levels then decay as normal boosts.
 * [StatBoosts] is injected through a [Provider] because it asks this provider for its boosts.
 */
@Singleton
class PactRegenerateBoosts @Inject constructor(private val statBoosts: Provider<StatBoosts>) :
    StatBoostProvider {
    override fun permanentBoosts(player: Player): Map<String, Int> {
        var boosts: MutableMap<String, Int>? = null
        for (boost in PactRegenerateBoost.entries) {
            val levels = amount(player, boost)
            if (levels > 0) {
                val map = boosts ?: LinkedHashMap<String, Int>().also { boosts = it }
                map[boost.stat] = levels
            }
        }
        return boosts ?: emptyMap()
    }

    /** The current levels of [boost] on [player]. */
    fun amount(player: Player, boost: PactRegenerateBoost): Int = player.attr[boost.amount] ?: 0

    /**
     * Adds [levels] to [boost] without passing [cap] and restarts its timer, even at the cap.
     *
     * @return The boost's new amount.
     */
    fun add(player: Player, boost: PactRegenerateBoost, levels: Int, cap: Int): Int {
        val current = amount(player, boost)
        val boosted = StatBoosts.timedBoost(current, levels, cap)
        if (boosted != current) {
            player.attr[boost.amount] = boosted
        }
        player.softTimer(boost.timer, BOOST_DURATION_TICKS)
        statBoosts.get().refresh(player)
        return boosted
    }

    /** Ends [boost] on [player] and lowers the stat back to its resting level. */
    fun end(player: Player, boost: PactRegenerateBoost) {
        player.clearSoftTimer(boost.timer)
        if (player.attr.has(boost.amount)) {
            player.attr.remove(boost.amount)
            statBoosts.get().refresh(player)
        }
    }
}
