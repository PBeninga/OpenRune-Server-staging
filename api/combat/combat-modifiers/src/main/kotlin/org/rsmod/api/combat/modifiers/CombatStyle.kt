package org.rsmod.api.combat.modifiers

import org.rsmod.game.hit.HitType

/**
 * The combat style of an attack, as seen by the combat modifier pipeline.
 *
 * This is deliberately coarser than RSMod's attack types and styles (stab, rapid, ...): most combat
 * effects key on "melee", "ranged" or "magic". Providers that need the finer details can read
 * them from the attacker (for example its worn weapon).
 */
public enum class CombatStyle {
    Melee,
    Ranged,
    Magic;

    /** The [HitType] used for hitsplats dealt with this style. */
    public val hitType: HitType
        get() =
            when (this) {
                Melee -> HitType.Melee
                Ranged -> HitType.Ranged
                Magic -> HitType.Magic
            }

    public companion object {
        /** Returns the [CombatStyle] for [type], or `null` for [HitType.Typeless]. */
        public fun from(type: HitType): CombatStyle? =
            when (type) {
                HitType.Melee -> Melee
                HitType.Ranged -> Ranged
                HitType.Magic -> Magic
                HitType.Typeless -> null
            }
    }
}
