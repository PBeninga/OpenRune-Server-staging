package org.rsmod.content.leagues.demonicpacts.effects.defence

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlin.math.max
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.stat.statHeal
import org.rsmod.content.leagues.demonicpacts.effects.defence.DefencePactEffects.PRAYER_RESTORE
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactEffects
import org.rsmod.content.leagues.demonicpacts.state.PactEffectsListener
import org.rsmod.game.entity.Player

/**
 * I6: while no protection prayer is active, restores 1 Prayer point every 15 ticks, 1 tick sooner
 * for every 7 worn prayer bonus (at least every tick).
 *
 * A soft timer, [TIMER], runs while the player owns I6: it starts when the effects are refreshed
 * (login, commit, `::pactset`) and stops on the first tick after I6 is gone. Each restore picks the
 * next interval from the prayer bonus worn at that moment. [DemonicPacts] is injected through a
 * [Provider] because it calls this listener.
 */
@Singleton
class PactPrayerRestore
@Inject
constructor(private val pacts: Provider<DemonicPacts>, private val wornBonuses: WornBonuses) :
    PactEffectsListener {
    override fun onEffectsRefreshed(player: Player, effects: PactEffects) {
        if (PRAYER_RESTORE !in effects) {
            player.clearSoftTimer(TIMER)
            return
        }
        if (!isRunning(player)) {
            player.softTimer(TIMER, interval(player))
        }
    }

    fun onTimer(player: Player) {
        if (PRAYER_RESTORE !in pacts.get().effects(player)) {
            player.clearSoftTimer(TIMER)
            return
        }
        if (!protectionPrayerActive(player)) {
            player.statHeal(PRAYER, 1, 0)
        }
        player.softTimer(TIMER, interval(player))
    }

    fun interval(player: Player): Int = interval(wornBonuses.prayerBonus(player))

    @OptIn(InternalApi::class)
    private fun isRunning(player: Player): Boolean =
        player.softTimerMap[TIMER.asRSCM(RSCMType.TIMER).toShort()] != null

    private fun protectionPrayerActive(player: Player): Boolean =
        PROTECTION_PRAYERS.any { player.vars[it] != 0 }

    companion object {
        const val TIMER: String = "timer.demonic_pacts_prayer_restore"
        const val BASE_INTERVAL: Int = 15
        const val PRAYER_BONUS_PER_TICK: Int = 7

        private const val PRAYER = "stat.prayer"

        private val PROTECTION_PRAYERS =
            listOf(
                "varbit.prayer_protectfrommelee",
                "varbit.prayer_protectfrommissiles",
                "varbit.prayer_protectfrommagic",
            )

        /** The ticks between restores with [prayerBonus] worn; a negative bonus counts as 0. */
        fun interval(prayerBonus: Int): Int =
            max(1, BASE_INTERVAL - max(0, prayerBonus) / PRAYER_BONUS_PER_TICK)
    }
}
