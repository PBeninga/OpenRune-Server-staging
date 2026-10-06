package org.rsmod.content.leagues.demonicpacts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState

/** The league's vars as the server packs them: what is saved, what reaches the client, widths. */
class DemonicPactsVarsTest {
    @Test
    fun GameTestState.`the pact state is saved and sent to the client`() =
        runGameTest {
            val state =
                (0..4).map { "varp.combat_mastery_perm_$it" } +
                    listOf(
                        "varp.talent_points_earned",
                        "varp.talent_points_spent",
                        "varp.talent_perm_1",
                    )
            for (name in state) {
                val varp = ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP)) ?: error(name)
                assertEquals(VarpLifetime.Perm, varp.scope)
                assertFalse(varp.transmit.never)
            }
        }

    @Test
    fun GameTestState.`the effect totals are sent but not saved`() =
        runGameTest {
            for (index in 0..8) {
                val name = "varp.talent_active_temp_$index"
                val varp = ServerCacheManager.getVarp(name.asRSCM(RSCMType.VARP)) ?: error(name)
                assertEquals(VarpLifetime.Temp, varp.scope)
                assertFalse(varp.transmit.never)
            }
        }

    @Test
    fun GameTestState.`varbit widths match the cache`() =
        runGameTest {
            val widths =
                mapOf(
                    "varbit.talent_resets_available" to 6,
                    "varbit.league_type" to 5,
                    "varbit.league_initial_points_spent" to 1,
                    "varbit.talent_regen_ammo_chance" to 10,
                    "varbit.talent_all_style_accuracy" to 10,
                    "varbit.talent_percentage_melee_damage" to 8,
                    "varbit.talent_percentage_ranged_damage" to 8,
                    "varbit.talent_percentage_magic_damage" to 8,
                    "varbit.talent_defence_boost" to 7,
                    "varbit.talent_prayer_pen_all" to 7,
                    "varbit.talent_thorns_damage" to 4,
                )
            for ((name, width) in widths) {
                val varbit = ServerCacheManager.getVarbit(name.asRSCM(RSCMType.VARBIT)) ?: error(name)
                assertEquals(width, varbit.endBit - varbit.startBit + 1)
            }
        }
}
