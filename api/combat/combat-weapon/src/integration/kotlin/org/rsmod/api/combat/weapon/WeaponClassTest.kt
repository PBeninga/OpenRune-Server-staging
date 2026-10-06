package org.rsmod.api.combat.weapon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.WeaponClass.Atlatl
import org.rsmod.api.combat.weapon.WeaponClass.Bow
import org.rsmod.api.combat.weapon.WeaponClass.Chinchompa
import org.rsmod.api.combat.weapon.WeaponClass.Crossbow
import org.rsmod.api.combat.weapon.WeaponClass.Halberd
import org.rsmod.api.combat.weapon.WeaponClass.Heavy
import org.rsmod.api.combat.weapon.WeaponClass.Light
import org.rsmod.api.combat.weapon.WeaponClass.Magic
import org.rsmod.api.combat.weapon.WeaponClass.Melee
import org.rsmod.api.combat.weapon.WeaponClass.OffHand
import org.rsmod.api.combat.weapon.WeaponClass.OneHanded
import org.rsmod.api.combat.weapon.WeaponClass.PoweredStaff
import org.rsmod.api.combat.weapon.WeaponClass.Ranged
import org.rsmod.api.combat.weapon.WeaponClass.Shield
import org.rsmod.api.combat.weapon.WeaponClass.Staff
import org.rsmod.api.combat.weapon.WeaponClass.Thrown
import org.rsmod.api.combat.weapon.WeaponClass.TwoHanded
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.righthand
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.inv.InvObj

class WeaponClassTest {
    @Test
    fun GameTestState.`sampled weapons and shield-slot items are classified`() = runGameTest {
        val mismatches =
            EXPECTED.mapNotNull { (obj, expected) ->
                val actual = WeaponClasses.of(objType(obj))
                if (actual == expected) null else "$obj: expected $expected, was $actual"
            }
        assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
    }

    @Test
    fun GameTestState.`non-weapons have no class`() = runGameTest {
        assertEquals(emptySet<WeaponClass>(), WeaponClasses.of(objType("obj.rune_platebody")))
        assertEquals(emptySet<WeaponClass>(), WeaponClasses.of(objType("obj.coins")))
    }

    @Test
    fun GameTestState.`worn classes combine the weapon and the shield slot`() = runGameTest {
        assertEquals(emptySet<WeaponClass>(), WeaponClasses.worn(player))
        assertEquals(emptySet<WeaponClass>(), WeaponClasses.weapon(player))

        player.righthand = InvObj("obj.abyssal_whip")
        player.lefthand = InvObj("obj.infernal_defender")
        assertEquals(setOf(Melee, Light, OneHanded, OffHand), WeaponClasses.worn(player))
        assertEquals(setOf(Melee, Light, OneHanded), WeaponClasses.weapon(player))

        player.lefthand = InvObj("obj.dragonfire_shield")
        assertEquals(setOf(Melee, Light, OneHanded, Shield), WeaponClasses.worn(player))
    }

    private companion object {
        val EXPECTED: Map<String, Set<WeaponClass>> =
            mapOf(
                // Melee, light (< 1 kg).
                "obj.abyssal_whip" to setOf(Melee, Light, OneHanded),
                "obj.abyssal_tentacle" to setOf(Melee, Light, OneHanded),
                "obj.dragon_dagger" to setOf(Melee, Light, OneHanded),
                "obj.dragon_claws" to setOf(Melee, Light, TwoHanded),
                "obj.dramen_staff" to setOf(Melee, Light, OneHanded),
                // Melee, heavy (>= 1 kg).
                "obj.ghrazi_rapier" to setOf(Melee, Heavy, OneHanded),
                "obj.osmumtens_fang" to setOf(Melee, Heavy, OneHanded),
                "obj.dragon_scimitar" to setOf(Melee, Heavy, OneHanded),
                "obj.dragon_warhammer" to setOf(Melee, Heavy, OneHanded),
                "obj.inquisitors_mace" to setOf(Melee, Heavy, OneHanded),
                "obj.dragonhunter_lance" to setOf(Melee, Heavy, OneHanded),
                "obj.scythe_of_vitur" to setOf(Melee, Heavy, TwoHanded),
                "obj.ancient_godsword" to setOf(Melee, Heavy, TwoHanded),
                "obj.saradomin_sword" to setOf(Melee, Heavy, TwoHanded),
                "obj.elder_maul" to setOf(Melee, Heavy, TwoHanded),
                "obj.granite_maul" to setOf(Melee, Heavy, TwoHanded),
                "obj.abyssal_bludgeon" to setOf(Melee, Heavy, TwoHanded),
                "obj.zamorak_spear" to setOf(Melee, Heavy, TwoHanded),
                "obj.dual_macuahuitl" to setOf(Melee, Heavy, TwoHanded),
                "obj.dinhs_bulwark" to setOf(Melee, Heavy, TwoHanded),
                // Halberds.
                "obj.bronze_halberd" to setOf(Melee, Heavy, TwoHanded, Halberd),
                "obj.dragon_halberd" to setOf(Melee, Heavy, TwoHanded, Halberd),
                "obj.crystal_halberd" to setOf(Melee, Heavy, TwoHanded, Halberd),
                "obj.noxious_halberd" to setOf(Melee, Heavy, TwoHanded, Halberd),
                // Bows; the Eclipse atlatl is thrown, not a bow.
                "obj.twisted_bow" to setOf(Ranged, Heavy, TwoHanded, Bow),
                "obj.magic_shortbow" to setOf(Ranged, Heavy, TwoHanded, Bow),
                "obj.bow_of_faerdhinen" to setOf(Ranged, Heavy, TwoHanded, Bow),
                "obj.venator_bow" to setOf(Ranged, Heavy, TwoHanded, Bow),
                "obj.eclipse_atlatl" to setOf(Ranged, Heavy, TwoHanded, Thrown, Atlatl),
                // Crossbows, including the ballistae.
                "obj.xbows_crossbow_runite" to setOf(Ranged, Heavy, OneHanded, Crossbow),
                "obj.dragonhunter_xbow" to setOf(Ranged, Heavy, OneHanded, Crossbow),
                "obj.zaryte_xbow" to setOf(Ranged, Heavy, OneHanded, Crossbow),
                "obj.barrows_karil_weapon" to setOf(Ranged, Heavy, TwoHanded, Crossbow),
                "obj.heavy_ballista" to setOf(Ranged, Heavy, TwoHanded, Crossbow),
                // Thrown.
                "obj.toxic_blowpipe" to setOf(Ranged, Light, TwoHanded, Thrown),
                "obj.rune_knife" to setOf(Ranged, Light, OneHanded, Thrown),
                "obj.dragon_dart" to setOf(Ranged, Light, OneHanded, Thrown),
                "obj.tzhaar_throwingring" to setOf(Ranged, Light, OneHanded, Thrown),
                "obj.rune_thrownaxe" to setOf(Ranged, Heavy, OneHanded, Thrown),
                "obj.morrigans_javelin" to setOf(Ranged, Heavy, OneHanded, Thrown),
                "obj.chinchompa_black" to setOf(Ranged, Light, OneHanded, Chinchompa),
                // Magic.
                "obj.kodai_wand" to setOf(Magic, Light, OneHanded, Staff),
                "obj.nightmare_staff" to setOf(Magic, Heavy, OneHanded, Staff),
                "obj.staff_of_balance" to setOf(Melee, Magic, Heavy, OneHanded, Staff),
                "obj.tumekens_shadow" to setOf(Magic, Heavy, TwoHanded, PoweredStaff),
                "obj.sanguinesti_staff" to setOf(Magic, Heavy, OneHanded, PoweredStaff),
                "obj.warped_sceptre" to setOf(Magic, Heavy, OneHanded, PoweredStaff),
                "obj.tots_charged" to setOf(Magic, Heavy, OneHanded, PoweredStaff),
                // Shield slot.
                "obj.rune_kiteshield" to setOf(Shield),
                "obj.dragonfire_shield" to setOf(Shield),
                "obj.dragonfire_ward" to setOf(Shield),
                "obj.elidinis_ward" to setOf(Shield),
                "obj.twisted_buckler" to setOf(Shield),
                "obj.spirit_shield" to setOf(Shield),
                "obj.crystal_shield" to setOf(Shield),
                "obj.tzhaar_spikeshield" to setOf(Shield),
                "obj.infernal_defender" to setOf(OffHand),
                "obj.dragon_parryingdagger" to setOf(OffHand),
                "obj.book_of_the_dead" to setOf(OffHand),
                "obj.tome_of_fire" to setOf(OffHand),
                "obj.abyssal_lantern" to setOf(OffHand),
            )
    }
}
