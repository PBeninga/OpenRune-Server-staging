package org.rsmod.content.leagues.demonicpacts.effects.defence

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.api.player.stat.PlayerHealSource
import org.rsmod.api.player.stat.PlayerHealing
import org.rsmod.api.player.stat.statHeal
import org.rsmod.content.leagues.demonicpacts.effects.defence.DefencePactEffects.BLOCK_HEAL
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

/**
 * J8: when an attack deals 0 damage to the player while a two-handed weapon or an off-hand is
 * equipped, the player heals 2 Hitpoints (pact healing, so it can overheal) and restores 2 Prayer
 * points. "Two-handed shield" in the node's text is read as a two-handed weapon.
 */
@Singleton
class PactBlockHeal
@Inject
constructor(private val pacts: DemonicPacts, private val healing: PlayerHealing) {
    fun onHitTaken(player: Player, hit: Hit) {
        if (hit.damage != 0 || !(hit.isFromNpc || hit.isFromPlayer)) {
            return
        }
        if (BLOCK_HEAL !in pacts.effects(player)) {
            return
        }
        val worn = WeaponClasses.worn(player)
        if (WeaponClass.TwoHanded !in worn && WeaponClass.OffHand !in worn) {
            return
        }
        healing.heal(player, HEAL, PlayerHealSource.Pact)
        player.statHeal(PRAYER, PRAYER_RESTORE, 0)
    }

    private companion object {
        const val HEAL = 2
        const val PRAYER_RESTORE = 2
        const val PRAYER = "stat.prayer"
    }
}
