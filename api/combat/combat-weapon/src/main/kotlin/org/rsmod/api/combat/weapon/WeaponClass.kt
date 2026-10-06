package org.rsmod.api.combat.weapon

import dev.openrune.types.ItemServerType
import dev.openrune.util.WeaponCategory
import dev.openrune.util.Wearpos
import java.util.EnumSet
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj

/**
 * Broad weapon and shield-slot classes, as named by effects such as "melee weapons under 1kg",
 * "while a crossbow is equipped" or "while you have a shield equipped".
 *
 * Use [WeaponClasses.of] to classify an obj, or [WeaponClasses.worn] for a player's equipment.
 */
public enum class WeaponClass {
    /** A weapon whose combat category attacks with a melee style. */
    Melee,

    /** A weapon whose combat category attacks with a ranged style. */
    Ranged,

    /** A weapon that casts spells: staves, bladed staves, powered staves and salamanders. */
    Magic,

    /** A weapon that weighs less than [WeaponClasses.HEAVY_GRAMS]. */
    Light,

    /** A weapon that weighs at least [WeaponClasses.HEAVY_GRAMS]. */
    Heavy,

    /** A weapon that leaves the shield slot free. */
    OneHanded,

    /** A weapon that also occupies the shield slot. */
    TwoHanded,

    /** A bow. The Eclipse atlatl is not a bow, even though it fires ammunition. */
    Bow,

    Crossbow,

    /** Darts, knives, thrownaxes, the blowpipes and the Eclipse atlatl. */
    Thrown,

    Chinchompa,

    /** A staff, wand or bladed staff that casts spells from a spellbook. */
    Staff,

    /** A powered staff, wand or sceptre with a built-in spell. */
    PoweredStaff,

    /** A polearm: the halberds, including the crystal and noxious halberds. */
    Halberd,

    /** The Eclipse atlatl, which counts as a thrown weapon but not as a bow. */
    Atlatl,

    /** A shield-slot item that is a shield (kiteshields, wards, bucklers, ...). */
    Shield,

    /** A shield-slot item that is not a shield: defenders, books, tomes, lanterns, ... */
    OffHand,
}

/** Classifies objs into [WeaponClass]es from their cache data. */
public object WeaponClasses {
    /** The weight (in grams) from which a weapon is heavy: 1 kg. */
    public const val HEAVY_GRAMS: Int = 1_000

    private val meleeCategories: Set<WeaponCategory> =
        EnumSet.of(
            WeaponCategory.Axe,
            WeaponCategory.Blunt,
            WeaponCategory.Claw,
            WeaponCategory.SlashSword,
            WeaponCategory.TwoHandedSword,
            WeaponCategory.GodSword,
            WeaponCategory.Pickaxe,
            WeaponCategory.Polearm,
            WeaponCategory.Polestaff,
            WeaponCategory.Scythe,
            WeaponCategory.Spear,
            WeaponCategory.Spiked,
            WeaponCategory.StabSword,
            WeaponCategory.Whip,
            WeaponCategory.BladedStaff,
            WeaponCategory.Banner,
            WeaponCategory.Bludgeon,
            WeaponCategory.Bulwark,
            WeaponCategory.Salamander,
        )

    private val rangedCategories: Set<WeaponCategory> =
        EnumSet.of(
            WeaponCategory.Bow,
            WeaponCategory.Crossbow,
            WeaponCategory.Thrown,
            WeaponCategory.Chinchompas,
            WeaponCategory.Salamander,
        )

    private val magicCategories: Set<WeaponCategory> =
        EnumSet.of(
            WeaponCategory.Staff,
            WeaponCategory.BladedStaff,
            WeaponCategory.PoweredStaff,
            WeaponCategory.Salamander,
        )

    private val shieldNameWords: List<String> =
        listOf("shield", "kiteshield", "ward", "buckler", "toktz-ket-xil")

    /**
     * Objs whose weight in the cache does not match how they are classed. The Dramen staff is not a
     * heavy weapon (OSRS Wiki, Demonic Pacts, 6 May 2026).
     */
    private val lightOverrides: Set<String> = setOf("obj.dramen_staff")

    /** Returns every [WeaponClass] of [obj]; empty for objs that are neither weapons nor shields. */
    public fun of(obj: ItemServerType): Set<WeaponClass> {
        val classes = EnumSet.noneOf(WeaponClass::class.java)
        when (obj.wearpos1) {
            Wearpos.RightHand.slot -> addWeaponClasses(obj, classes)
            Wearpos.LeftHand.slot -> classes += shieldSlotClass(obj)
        }
        return classes
    }

    /** Returns the classes of the player's worn weapon and shield-slot item, combined. */
    public fun worn(player: Player): Set<WeaponClass> {
        val classes = EnumSet.noneOf(WeaponClass::class.java)
        player.worn[Wearpos.RightHand.slot]?.let { classes += of(getInvObj(it)) }
        player.worn[Wearpos.LeftHand.slot]?.let { classes += of(getInvObj(it)) }
        return classes
    }

    /** Returns the classes of the player's worn weapon only (empty when unarmed). */
    public fun weapon(player: Player): Set<WeaponClass> {
        val weapon = player.worn[Wearpos.RightHand.slot] ?: return emptySet()
        return of(getInvObj(weapon))
    }

    public fun isTwoHanded(obj: ItemServerType): Boolean =
        obj.wearpos2 == Wearpos.LeftHand.slot || obj.wearpos3 == Wearpos.LeftHand.slot

    public fun isHeavy(obj: ItemServerType): Boolean =
        obj.weight >= HEAVY_GRAMS && lightOverrides.none(obj::isType)

    private fun addWeaponClasses(obj: ItemServerType, classes: EnumSet<WeaponClass>) {
        val category = obj.weaponCategory
        if (category == WeaponCategory.Unarmed) {
            return
        }
        if (category in meleeCategories) classes += WeaponClass.Melee
        if (category in rangedCategories) classes += WeaponClass.Ranged
        if (category in magicCategories) classes += WeaponClass.Magic

        classes += if (isHeavy(obj)) WeaponClass.Heavy else WeaponClass.Light
        classes += if (isTwoHanded(obj)) WeaponClass.TwoHanded else WeaponClass.OneHanded

        val atlatl = obj.isType("obj.eclipse_atlatl")
        when {
            atlatl -> classes += setOf(WeaponClass.Atlatl, WeaponClass.Thrown)
            category == WeaponCategory.Bow -> classes += WeaponClass.Bow
            category == WeaponCategory.Crossbow -> classes += WeaponClass.Crossbow
            category == WeaponCategory.Thrown -> classes += WeaponClass.Thrown
            category == WeaponCategory.Chinchompas -> classes += WeaponClass.Chinchompa
            category == WeaponCategory.PoweredStaff -> classes += WeaponClass.PoweredStaff
            category == WeaponCategory.Staff -> classes += WeaponClass.Staff
            category == WeaponCategory.BladedStaff -> classes += WeaponClass.Staff
        }
        if (category == WeaponCategory.Polearm || obj.isCategoryType("category.halberd")) {
            classes += WeaponClass.Halberd
        }
    }

    private fun shieldSlotClass(obj: ItemServerType): WeaponClass {
        val name = obj.name.lowercase()
        val words = name.split(' ', '\'', '(', ')')
        val isShield = shieldNameWords.any { it in words }
        return if (isShield) WeaponClass.Shield else WeaponClass.OffHand
    }
}
