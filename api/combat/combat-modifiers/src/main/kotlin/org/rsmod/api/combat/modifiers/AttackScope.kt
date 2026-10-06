package org.rsmod.api.combat.modifiers

import dev.openrune.types.ItemServerType
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The PvN or PvP attack a player is currently performing, opened by
 * [CombatModifierPipeline.withAttack].
 *
 * While a scope is open the pipeline knows the attack's target, style and spell (for attack-speed
 * modifiers), whether it is a special attack, and, against an npc, the rolls made for it (for
 * [ExtraHit.reroll]).
 * Outside of a scope, stat modifiers and hit resolution still work, but attack speed is not
 * modified and extra hits can not re-roll.
 *
 * The recorded rolls take the npc to roll against, so that [ExtraHit.retarget] can make the same
 * attack against another npc.
 */
public class AttackScope
internal constructor(
    public val attacker: Player,
    public val target: PathingEntity,
    public val style: CombatStyle,
    public val spell: ItemServerType?,
    internal val previous: AttackScope?,
) {
    /** `true` while a special attack is being performed in this scope. */
    public var isSpecial: Boolean = false
        internal set

    internal var rollTarget: Npc? = null
    internal var accuracyRoll: ((Npc) -> Boolean)? = null
    internal var damageRoll: ((Npc) -> Int)? = null
    internal var rerollSource: HitSource? = null
    internal var baseHits: Int = 0

    internal val isRerolling: Boolean
        get() = rerollSource != null

    override fun toString(): String =
        "AttackScope(" +
            "attacker=${attacker.username}, " +
            "target=${target.describe()}, " +
            "style=$style, " +
            "spell=${spell?.name}, " +
            "isSpecial=$isSpecial" +
            ")"
}
