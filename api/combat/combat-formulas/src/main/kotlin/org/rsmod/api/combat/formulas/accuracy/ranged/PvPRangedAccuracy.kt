package org.rsmod.api.combat.formulas.accuracy.ranged

import jakarta.inject.Inject
import java.util.EnumSet
import org.rsmod.api.combat.accuracy.player.PlayerRangedAccuracy
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.accuracy.AccuracyOperations
import org.rsmod.api.combat.formulas.accuracy.AccuracyRollModifier
import org.rsmod.api.combat.formulas.attributes.CombatRangedAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatRangedAttributeCollector
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.game.entity.Player

public class PvPRangedAccuracy
@Inject
constructor(
    private val bonuses: WornBonuses,
    private val attackStyles: AttackStyles,
    private val rangedAttributes: CombatRangedAttributeCollector,
) {
    public fun getHitChance(
        player: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specialMultiplier: Double,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int {
        val targetDistance = player.coords.chebyshevDistance(target.coords)
        return computeHitChance(
            source = player,
            target = target,
            targetDistance = targetDistance,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specialMultiplier,
            rollModifier = rollModifier,
        )
    }

    public fun computeHitChance(
        source: Player,
        target: Player,
        targetDistance: Int,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specialMultiplier: Double,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int {
        val rangeAttributes = rangedAttributes.collect(source, attackType, attackStyle)

        val baseAttackRoll =
            computeAttackRoll(source, targetDistance, attackStyle, rangeAttributes, rollModifier)
        val specAttackRoll = (baseAttackRoll * specialMultiplier).toInt()
        val attackRoll = rollModifier.modifyAttackRoll(specAttackRoll)
        val defenceRoll = rollModifier.modifyDefenceRoll(computeDefenceRoll(target))

        val hitChance = AccuracyOperations.calculateHitChance(attackRoll, defenceRoll)
        return rollModifier.modifyHitChance(hitChance, attackRoll, defenceRoll)
    }

    public fun computeAttackRoll(
        source: Player,
        targetDistance: Int,
        attackStyle: RangedAttackStyle?,
        rangeAttributes: EnumSet<CombatRangedAttributes>,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int {
        val effectiveRanged =
            RangedAccuracyOperations.calculateEffectiveRanged(source, attackStyle, rollModifier)
        val rangedBonus = rollModifier.modifyAttackBonus(bonuses.offensiveRangedBonus(source))
        val attackRoll = PlayerRangedAccuracy.calculateBaseAttackRoll(effectiveRanged, rangedBonus)
        return RangedAccuracyOperations.modifyAttackRoll(
            attackRoll = attackRoll,
            targetDistance = targetDistance,
            rangeAttributes = rangeAttributes,
        )
    }

    public fun computeDefenceRoll(target: Player): Int {
        val targetAttackStyle = attackStyles.get(target)
        val effectiveDefence =
            RangedAccuracyOperations.calculateEffectiveDefence(target, targetAttackStyle)
        val defenceBonus = bonuses.defensiveRangedBonus(target)
        return PlayerRangedAccuracy.calculateBaseDefenceRoll(effectiveDefence, defenceBonus)
    }
}
