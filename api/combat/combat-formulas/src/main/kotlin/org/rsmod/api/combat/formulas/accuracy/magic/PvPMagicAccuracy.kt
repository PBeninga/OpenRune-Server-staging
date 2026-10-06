package org.rsmod.api.combat.formulas.accuracy.magic

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import java.util.EnumSet
import org.rsmod.api.combat.accuracy.player.PlayerMagicAccuracy
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.commons.styles.MagicAttackStyle
import org.rsmod.api.combat.formulas.accuracy.AccuracyOperations
import org.rsmod.api.combat.formulas.accuracy.AccuracyRollModifier
import org.rsmod.api.combat.formulas.attributes.CombatSpellAttributes
import org.rsmod.api.combat.formulas.attributes.collector.CombatMagicAttributeCollector
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

public class PvPMagicAccuracy
@Inject
constructor(
    private val random: GameRandom,
    private val bonuses: WornBonuses,
    private val attackStyles: AttackStyles,
    private val magicAttributes: CombatMagicAttributeCollector,
) {
    public fun getSpellHitChance(
        player: Player,
        target: Player,
        spell: ItemServerType,
        spellbook: Spellbook?,
        usedSunfireRune: Boolean,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int =
        computeSpellHitChance(
            source = player,
            target = target,
            spell = spell,
            spellbook = spellbook,
            usedSunfireRune = usedSunfireRune,
            rollModifier = rollModifier,
        )

    public fun computeSpellHitChance(
        source: Player,
        target: Player,
        spell: ItemServerType,
        spellbook: Spellbook?,
        usedSunfireRune: Boolean,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int {
        val spellAttributes =
            magicAttributes.spellCollect(source, spell, spellbook, usedSunfireRune, random)
        val attackRoll =
            rollModifier.modifyAttackRoll(
                computeSpellAttackRoll(source, spellAttributes, rollModifier)
            )
        val defenceRoll = rollModifier.modifyDefenceRoll(computeDefenceRoll(target))
        val hitChance = AccuracyOperations.calculateHitChance(attackRoll, defenceRoll)
        return rollModifier.modifyHitChance(hitChance, attackRoll, defenceRoll)
    }

    public fun computeSpellAttackRoll(
        source: Player,
        spellAttributes: EnumSet<CombatSpellAttributes>,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int {
        val effectiveMagic =
            MagicAccuracyOperations.calculateEffectiveMagic(source, null, rollModifier)
        val magicBonus = rollModifier.modifyAttackBonus(bonuses.offensiveMagicBonus(source))
        val attackRoll = PlayerMagicAccuracy.calculateBaseAttackRoll(effectiveMagic, magicBonus)
        return MagicAccuracyOperations.modifySpellAttackRoll(attackRoll, spellAttributes)
    }

    public fun getStaffHitChance(
        player: Player,
        target: Player,
        attackStyle: MagicAttackStyle?,
        specialMultiplier: Double,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int = computeStaffHitChance(player, target, attackStyle, specialMultiplier, rollModifier)

    public fun computeStaffHitChance(
        source: Player,
        target: Player,
        attackStyle: MagicAttackStyle?,
        specialMultiplier: Double,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int {
        val baseAttackRoll = computeStaffAttackRoll(source, attackStyle, rollModifier)
        val specAttackRoll = (baseAttackRoll * specialMultiplier).toInt()
        val attackRoll = rollModifier.modifyAttackRoll(specAttackRoll)
        val defenceRoll = rollModifier.modifyDefenceRoll(computeDefenceRoll(target))
        val hitChance = AccuracyOperations.calculateHitChance(attackRoll, defenceRoll)
        return rollModifier.modifyHitChance(hitChance, attackRoll, defenceRoll)
    }

    public fun computeStaffAttackRoll(
        source: Player,
        attackStyle: MagicAttackStyle?,
        rollModifier: AccuracyRollModifier = AccuracyRollModifier.NONE,
    ): Int {
        val effectiveMagic =
            MagicAccuracyOperations.calculateEffectiveMagic(source, attackStyle, rollModifier)
        val magicBonus = rollModifier.modifyAttackBonus(bonuses.offensiveMagicBonus(source))
        val attackRoll = PlayerMagicAccuracy.calculateBaseAttackRoll(effectiveMagic, magicBonus)
        return MagicAccuracyOperations.modifyStaffAttackRoll(attackRoll)
    }

    public fun computeDefenceRoll(target: Player): Int {
        val targetAttackStyle = attackStyles.get(target)
        val effectiveDefence =
            MagicAccuracyOperations.calculateEffectiveDefence(target, targetAttackStyle)
        val defenceBonus = bonuses.defensiveMagicBonus(target)
        return PlayerMagicAccuracy.calculateBaseDefenceRoll(effectiveDefence, defenceBonus)
    }
}
