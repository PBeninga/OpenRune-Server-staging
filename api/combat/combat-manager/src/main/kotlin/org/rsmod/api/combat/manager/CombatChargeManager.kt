package org.rsmod.api.combat.manager

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.combat.modifiers.CombatModifierPipeline
import org.rsmod.api.combat.modifiers.ResourceKind
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getOrNull

public class CombatChargeManager
@Inject
constructor(
    private val manager: ObjChargeManager,
    private val modifiers: CombatModifierPipeline,
) {
    /**
     * Returns the number of charges on the player's weapon.
     *
     * Internally calls [ObjChargeManager.getCharges] with `obj = player.righthand`.
     */
    public fun getWeaponCharges(player: Player, varobj: String): Int =
        manager.getCharges(player.righthand, varobj)

    /**
     * Attempts to reduce the charges on the player's weapon by [decrement].
     *
     * Internally calls [ObjChargeManager.reduceWornCharges] with `wearpos = Wearpos.RightHand`.
     */
    public fun attemptDetractWeapon(
        player: Player,
        varobj: String,
        decrement: Int = 1,
    ): ObjChargeManager.Uncharge {
        val charges = getWeaponCharges(player, varobj)
        if (charges >= decrement) {
            val weapon = getOrNull(player.righthand)
            if (modifiers.isRefunded(player, ResourceKind.Charge, weapon, decrement)) {
                return ObjChargeManager.Uncharge.Success(chargesLeft = charges)
            }
        }
        return manager.reduceWornCharges(player, Wearpos.RightHand, varobj, decrement)
    }
}
