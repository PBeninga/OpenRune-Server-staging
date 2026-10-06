package org.rsmod.api.combat.formulas.accuracy.melee

import jakarta.inject.Inject
import java.util.EnumSet
import org.rsmod.api.combat.accuracy.player.PlayerMeleeAccuracy
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.accuracy.AccuracyOperations
import org.rsmod.api.combat.formulas.accuracy.AccuracyRollModifier
import org.rsmod.api.combat.formulas.attributes.CombatMeleeAttributes
import org.rsmod.api.combat.formulas.attributes.CombatNpcAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatMeleeAttributeCollector
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.game.entity.Player

public class PvPMeleeAccuracy
@Inject
constructor(
    private val bonuses: WornBonuses,
    private val attackStyles: AttackStyles,
    private val meleeAttributes: CombatMeleeAttributeCollector,
) {
    public fun getHitChance(
        player: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specialMultiplier: Double,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int =
        computeHitChance(
            source = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            blockType = blockType,
            specialMultiplier = specialMultiplier,
            rollModifier = rollModifier,
        )

    public fun computeHitChance(
        source: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        blockType: MeleeAttackType?,
        specialMultiplier: Double,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int {
        val npcAttributes = EnumSet.noneOf(CombatNpcAttributes::class.java)
        val meleeAttributes = meleeAttributes.collect(source, attackType)

        val baseAttackRoll =
            computeAttackRoll(
                source,
                attackType,
                attackStyle,
                meleeAttributes,
                npcAttributes,
                rollModifier,
            )
        val specAttackRoll = (baseAttackRoll * specialMultiplier).toInt()
        val attackRoll = rollModifier.modifyAttackRoll(specAttackRoll)
        val defenceRoll = rollModifier.modifyDefenceRoll(computeDefenceRoll(target, blockType))

        val hitChance = AccuracyOperations.calculateHitChance(attackRoll, defenceRoll)
        val meleeHitChance =
            MeleeAccuracyOperations.modifyHitChance(
                hitChance = hitChance,
                attackRoll = attackRoll,
                defenceRoll = defenceRoll,
                meleeAttributes = meleeAttributes,
                npcAttributes = npcAttributes,
            )
        return rollModifier.modifyHitChance(meleeHitChance, attackRoll, defenceRoll)
    }

    public fun computeAttackRoll(
        source: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        meleeAttributes: EnumSet<CombatMeleeAttributes>,
        npcAttributes: EnumSet<CombatNpcAttributes>,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int {
        val effectiveAttack =
            MeleeAccuracyOperations.calculateEffectiveAttack(source, attackStyle, rollModifier)
        val attackBonus = rollModifier.modifyAttackBonus(source.getAttackBonus(attackType))
        val attackRoll = PlayerMeleeAccuracy.calculateBaseAttackRoll(effectiveAttack, attackBonus)
        return MeleeAccuracyOperations.modifyAttackRoll(attackRoll, meleeAttributes, npcAttributes)
    }

    private fun Player.getAttackBonus(attackType: MeleeAttackType?): Int =
        when (attackType) {
            MeleeAttackType.Stab -> bonuses.offensiveStabBonus(this)
            MeleeAttackType.Slash -> bonuses.offensiveSlashBonus(this)
            MeleeAttackType.Crush -> bonuses.offensiveCrushBonus(this)
            null -> 0
        }

    public fun computeDefenceRoll(target: Player, attackType: MeleeAttackType?): Int {
        val targetAttackStyle = attackStyles.get(target)
        val effectiveDefence =
            MeleeAccuracyOperations.calculateEffectiveDefence(target, targetAttackStyle)
        val defenceBonus = target.getDefenceBonus(attackType)
        return PlayerMeleeAccuracy.calculateBaseDefenceRoll(effectiveDefence, defenceBonus)
    }

    private fun Player.getDefenceBonus(attackType: MeleeAttackType?): Int =
        when (attackType) {
            MeleeAttackType.Stab -> bonuses.defensiveStabBonus(this)
            MeleeAttackType.Slash -> bonuses.defensiveSlashBonus(this)
            MeleeAttackType.Crush -> bonuses.defensiveCrushBonus(this)
            null -> 0
        }
}
