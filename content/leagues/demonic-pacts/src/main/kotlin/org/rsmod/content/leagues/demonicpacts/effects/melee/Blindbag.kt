package org.rsmod.content.leagues.demonicpacts.effects.melee

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.modifiers.ModifierMath
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

/**
 * D3's Blindbag: on a melee attack with a heavy melee weapon, a chance (the attack's summed
 * [HitSource.Blindbag] proc chance, from [MeleePactModifiers]) to attack the same target at once
 * with a random heavy melee weapon from the inventory, as though it were equipped. The Blindbag
 * attack is an extra hit ([ExtraHit.reroll]) whose stats [MeleePactModifiers] swaps to the bag
 * weapon's while it rolls ([weapon]). It ignores the bag weapon's requirements and doesn't delay
 * the next attack.
 *
 * Blindbag attacks roll the same chance again, up to [MeleePactEffects.BLINDBAG_CHAIN_CAP] in a
 * row, each with a new random weapon. Special attacks never trigger it, and like every pipeline
 * proc it only happens against npcs.
 */
@Singleton
class Blindbag @Inject constructor(private val random: GameRandom) : CombatProcListener {
    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (context.isSpecial || context.style != CombatStyle.Melee) {
            return emptyList()
        }
        val triggers =
            if (event.isChained) {
                event.source == HitSource.Blindbag
            } else {
                event.source == HitSource.Base && event.baseHitIndex == 0
            }
        if (!triggers || !rollChance(event.procChancePercent(HitSource.Blindbag))) {
            return emptyList()
        }
        val weapons = heavyMeleeWeapons(context.attacker)
        val weapon = random.pickOrNull(weapons) ?: return emptyList()
        context.attacker.attr[BAG_WEAPON] = weapon
        return listOf(ExtraHit.reroll(HitSource.Blindbag))
    }

    /** The weapon of [player]'s Blindbag attack being rolled now, or of the last one. */
    fun weapon(player: Player): ItemServerType? = player.attr[BAG_WEAPON]

    private fun rollChance(chancePercent: Double): Boolean {
        val basisPoints = ModifierMath.chanceBasisPoints(chancePercent)
        return when {
            basisPoints <= 0 -> false
            basisPoints >= ModifierMath.CHANCE_SCALE -> true
            else -> random.of(maxExclusive = ModifierMath.CHANCE_SCALE) < basisPoints
        }
    }

    companion object {
        private val BAG_WEAPON: AttributeKey<ItemServerType> = AttributeKey()

        /** The distinct heavy (1kg or more) melee weapons in [player]'s inventory. */
        fun heavyMeleeWeapons(player: Player): List<ItemServerType> {
            val weapons = LinkedHashMap<Int, ItemServerType>()
            for (obj in player.inv.objs) {
                if (obj == null || obj.id in weapons) {
                    continue
                }
                val type = getInvObj(obj)
                if (isHeavyMelee(type)) {
                    weapons[obj.id] = type
                }
            }
            return weapons.values.toList()
        }

        fun isHeavyMelee(type: ItemServerType): Boolean {
            val classes = WeaponClasses.of(type)
            return WeaponClass.Melee in classes && WeaponClass.Heavy in classes
        }
    }
}
