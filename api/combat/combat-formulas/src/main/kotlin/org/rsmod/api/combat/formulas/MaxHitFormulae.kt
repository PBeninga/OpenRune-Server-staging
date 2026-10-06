package org.rsmod.api.combat.formulas

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.maxhit.MaxHitModifier
import org.rsmod.api.combat.formulas.maxhit.magic.NvNMagicMaxHit
import org.rsmod.api.combat.formulas.maxhit.magic.NvPMagicMaxHit
import org.rsmod.api.combat.formulas.maxhit.magic.PvNMagicMaxHit
import org.rsmod.api.combat.formulas.maxhit.magic.PvPMagicMaxHit
import org.rsmod.api.combat.formulas.maxhit.melee.NvNMeleeMaxHit
import org.rsmod.api.combat.formulas.maxhit.melee.NvPMeleeMaxHit
import org.rsmod.api.combat.formulas.maxhit.melee.PvNMeleeMaxHit
import org.rsmod.api.combat.formulas.maxhit.melee.PvPMeleeMaxHit
import org.rsmod.api.combat.formulas.maxhit.ranged.NvNRangedMaxHit
import org.rsmod.api.combat.formulas.maxhit.ranged.NvPRangedMaxHit
import org.rsmod.api.combat.formulas.maxhit.ranged.PvNRangedMaxHit
import org.rsmod.api.combat.formulas.maxhit.ranged.PvPRangedMaxHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public class MaxHitFormulae
@Inject
constructor(
    private val pvnMagicMaxHit: PvNMagicMaxHit,
    private val pvpMagicMaxHit: PvPMagicMaxHit,
    private val nvpMagicMaxHit: NvPMagicMaxHit,
    private val nvnMagicMaxHit: NvNMagicMaxHit,
    private val pvnMeleeMaxHit: PvNMeleeMaxHit,
    private val pvpMeleeMaxHit: PvPMeleeMaxHit,
    private val nvnMeleeMaxHit: NvNMeleeMaxHit,
    private val nvpMeleeMaxHit: NvPMeleeMaxHit,
    private val pvnRangedMaxHit: PvNRangedMaxHit,
    private val pvpRangedMaxHit: PvPRangedMaxHit,
    private val nvpRangedMaxHit: NvPRangedMaxHit,
    private val nvnRangedMaxHit: NvNRangedMaxHit,
) {
    /** @see [PvNMeleeMaxHit.getMaxHit] */
    public fun getMeleeMaxHit(
        player: Player,
        target: Npc,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
        roundUp: Boolean = false,
        maxHitModifier: MaxHitModifier = MaxHitModifier.NONE,
    ): Int =
        pvnMeleeMaxHit.getMaxHit(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
            roundUp = roundUp,
            maxHitModifier = maxHitModifier,
        )

    /** @see [PvPMeleeMaxHit.getMaxHit] */
    public fun getMeleeMaxHit(
        player: Player,
        target: Player,
        attackType: MeleeAttackType?,
        attackStyle: MeleeAttackStyle?,
        specMultiplier: Double,
        roundUp: Boolean = false,
        maxHitModifier: MaxHitModifier = MaxHitModifier.NONE,
    ): Int =
        pvpMeleeMaxHit.getMaxHit(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
            roundUp = roundUp,
            maxHitModifier = maxHitModifier,
        )

    /** @see [NvPMeleeMaxHit.getMaxHit] */
    public fun getMeleeMaxHit(npc: Npc, target: Player, attackType: MeleeAttackType?): Int =
        nvpMeleeMaxHit.getMaxHit(npc, target, attackType)

    /** @see [NvNMeleeMaxHit.getMaxHit] */
    public fun getMeleeMaxHit(npc: Npc): Int = nvnMeleeMaxHit.getMaxHit(npc)

    /** @see [PvNRangedMaxHit.getMaxHit] */
    public fun getRangedMaxHit(
        player: Player,
        target: Npc,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
        boltSpecDamage: Int,
        maxHitModifier: MaxHitModifier = MaxHitModifier.NONE,
    ): Int =
        pvnRangedMaxHit.getMaxHit(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
            boltSpecDamage = boltSpecDamage,
            maxHitModifier = maxHitModifier,
        )

    /** @see [PvPRangedMaxHit.getMaxHit] */
    public fun getRangedMaxHit(
        player: Player,
        target: Player,
        attackType: RangedAttackType?,
        attackStyle: RangedAttackStyle?,
        specMultiplier: Double,
        boltSpecDamage: Int,
        maxHitModifier: MaxHitModifier = MaxHitModifier.NONE,
    ): Int =
        pvpRangedMaxHit.getMaxHit(
            player = player,
            target = target,
            attackType = attackType,
            attackStyle = attackStyle,
            specialMultiplier = specMultiplier,
            boltSpecDamage = boltSpecDamage,
            maxHitModifier = maxHitModifier,
        )

    /** @see [NvPRangedMaxHit.getMaxHit] */
    public fun getRangedMaxHit(npc: Npc, target: Player): Int =
        nvpRangedMaxHit.getMaxHit(npc, target)

    /** @see [NvNRangedMaxHit.getMaxHit] */
    public fun getRangedMaxHit(npc: Npc): Int = nvnRangedMaxHit.getMaxHit(npc)

    /** @see [PvNMagicMaxHit.getSpellMaxHit] */
    public fun getSpellMaxHitRange(
        player: Player,
        target: Npc,
        spell: ItemServerType,
        spellbook: Spellbook?,
        baseMaxHit: Int,
        attackRate: Int,
        usedSunfireRune: Boolean,
        maxHitModifier: MaxHitModifier = MaxHitModifier.NONE,
    ): IntRange =
        pvnMagicMaxHit.getSpellMaxHit(
            player = player,
            target = target,
            spellbook = spellbook,
            spell = spell,
            baseMaxHit = baseMaxHit,
            attackRate = attackRate,
            usedSunfireRune = usedSunfireRune,
            maxHitModifier = maxHitModifier,
        )

    /** @see [PvPMagicMaxHit.getSpellMaxHit] */
    public fun getSpellMaxHitRange(
        player: Player,
        target: Player,
        spell: ItemServerType,
        spellbook: Spellbook?,
        baseMaxHit: Int,
        usedSunfireRune: Boolean,
        maxHitModifier: MaxHitModifier = MaxHitModifier.NONE,
    ): IntRange =
        pvpMagicMaxHit.getSpellMaxHit(
            player = player,
            target = target,
            spellbook = spellbook,
            spell = spell,
            baseMaxHit = baseMaxHit,
            usedSunfireRune = usedSunfireRune,
            maxHitModifier = maxHitModifier,
        )

    /** @see [PvNMagicMaxHit.getStaffMaxHit] */
    public fun getStaffMaxHit(
        player: Player,
        target: Npc,
        baseMaxHit: Int,
        specialMultiplier: Double,
        maxHitModifier: MaxHitModifier = MaxHitModifier.NONE,
    ): Int =
        pvnMagicMaxHit.getStaffMaxHit(
            player = player,
            target = target,
            baseMaxHit = baseMaxHit,
            specialMultiplier = specialMultiplier,
            maxHitModifier = maxHitModifier,
        )

    /** @see [PvPMagicMaxHit.getStaffMaxHit] */
    public fun getStaffMaxHit(
        player: Player,
        target: Player,
        baseMaxHit: Int,
        specialMultiplier: Double,
        maxHitModifier: MaxHitModifier = MaxHitModifier.NONE,
    ): Int =
        pvpMagicMaxHit.getStaffMaxHit(
            player = player,
            target = target,
            baseMaxHit = baseMaxHit,
            specialMultiplier = specialMultiplier,
            maxHitModifier = maxHitModifier,
        )

    /** @see [NvPMagicMaxHit.getMaxHit] */
    public fun getMagicMaxHit(npc: Npc, target: Player): Int = nvpMagicMaxHit.getMaxHit(npc, target)

    /** @see [NvNMagicMaxHit.getMaxHit] */
    public fun getMagicMaxHit(npc: Npc): Int = nvnMagicMaxHit.getMaxHit(npc)
}
