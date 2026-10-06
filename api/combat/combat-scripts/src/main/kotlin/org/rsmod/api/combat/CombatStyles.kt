package org.rsmod.api.combat

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.AttackType
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.player.autocastSpell
import org.rsmod.api.player.protect.ProtectedAccess

internal fun CombatAttack.PlayerAttack.combatStyle(): CombatStyle =
    when (this) {
        is CombatAttack.Melee -> CombatStyle.Melee
        is CombatAttack.Ranged -> CombatStyle.Ranged
        is CombatAttack.Spell -> CombatStyle.Magic
        is CombatAttack.Staff -> CombatStyle.Magic
    }

/** The style of the attack [type] (or an autocast spell) makes, for attack-range modifiers. */
internal fun ProtectedAccess.combatStyle(type: AttackType?): CombatStyle =
    when {
        autocastSpell > 0 || type?.isMagic == true -> CombatStyle.Magic
        type?.isRanged == true -> CombatStyle.Ranged
        else -> CombatStyle.Melee
    }
