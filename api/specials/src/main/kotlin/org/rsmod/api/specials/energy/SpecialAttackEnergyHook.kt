package org.rsmod.api.specials.energy

import org.rsmod.game.entity.Player

/**
 * Changes how much special attack energy a special attack spends.
 *
 * Register implementations with a Guice `Multibinder` (`addSetBinding<SpecialAttackEnergyHook>`).
 * Chances from all hooks are summed and rolled once by [SpecialAttackEnergy.takeSpecialEnergy].
 */
public interface SpecialAttackEnergyHook {
    /**
     * Returns the chance, in percentage points (`20.0` = 20%), that [player]'s special attack
     * costing [energy] (tenths of a percent) consumes no energy at all.
     */
    public fun freeChancePercent(player: Player, energy: Int): Double = 0.0
}
