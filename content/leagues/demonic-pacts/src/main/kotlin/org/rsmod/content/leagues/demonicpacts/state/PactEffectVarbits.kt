package org.rsmod.content.leagues.demonicpacts.state

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpServerType
import dev.openrune.types.varp.baseVar
import jakarta.inject.Inject
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.resyncVar
import org.rsmod.game.entity.Player

/**
 * Shows [PactEffects] in the tree's "Active Pact Info" panel, which reads one `talent_*` varbit per
 * effect id (`[proc,script9458]`). Each total is clamped to its varbit's width, so a sum past it
 * (for example 9 prayer penetration nodes, 225 in a 7-bit varbit) shows the largest value the
 * varbit holds; effect code uses the unclamped [PactEffects].
 */
class PactEffectVarbits @Inject constructor() {
    private val maxValues: Map<Int, Int> by lazy {
        VARBITS.mapValues { (_, varbit) -> PactState.maxValue(varbit) }
    }

    private val baseVarps: List<VarpServerType> by lazy {
        VARBITS.values
            .map { varbit ->
                val type =
                    ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))
                        ?: error("Missing $varbit in the cache.")
                type.baseVar
            }
            .distinctBy(VarpServerType::id)
    }

    fun publish(player: Player, effects: PactEffects) {
        for ((effectId, varbit) in VARBITS) {
            val value = effects[effectId].coerceIn(0, maxValues.getValue(effectId))
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }
    }

    fun resync(player: Player) {
        for (varp in baseVarps) {
            player.resyncVar(varp)
        }
    }

    companion object {
        /** Effect id -> its `talent_*` varbit, for the 73 effect ids the tree's nodes use. */
        val VARBITS: Map<Int, String> =
            mapOf(
                1 to "varbit.talent_regen_ammo_chance",
                3 to "varbit.talent_airrune_regen_prayer_restore",
                4 to "varbit.talent_waterrune_regen_healing",
                5 to "varbit.talent_firerune_regen_damage_boost",
                6 to "varbit.talent_earthrune_regen_defence_boost",
                7 to "varbit.talent_percentage_magic_damage",
                8 to "varbit.talent_crossbow_echo_reproc_chance",
                9 to "varbit.talent_bow_echo_pass_accuracy",
                10 to "varbit.talent_thrown_maxhit_echoes",
                11 to "varbit.talent_ranged_regen_echo_chance",
                12 to "varbit.talent_all_style_accuracy",
                13 to "varbit.talent_defence_boost",
                14 to "varbit.talent_percentage_melee_damage",
                15 to "varbit.talent_percentage_ranged_damage",
                16 to "varbit.talent_offhand_stat_boost",
                17 to "varbit.talent_thrown_weapon_melee_str_scale",
                18 to "varbit.talent_overhealing_via_talents",
                19 to "varbit.talent_thorns_damage",
                20 to "varbit.talent_melee_range_multiplier",
                23 to "varbit.talent_shield_reflect",
                24 to "varbit.talent_spec_for_free",
                25 to "varbit.talent_distance_melee_minhit",
                26 to "varbit.talent_melee_distance_healing_chance",
                29 to "varbit.talent_water_spell_damage_high_hp",
                30 to "varbit.talent_air_spell_max_hit_prayer_bonus",
                31 to "varbit.talent_water_spell_bouce_heal",
                32 to "varbit.talent_fire_hp_consume_for_damage",
                33 to "varbit.talent_fire_spell_burn_bounce",
                34 to "varbit.talent_magic_attack_speed_traditional",
                35 to "varbit.talent_regen_stave_charges_water",
                36 to "varbit.talent_regen_stave_charges_fire",
                40 to "varbit.talent_prayer_pen_all",
                41 to "varbit.talent_earth_scale_defence_stat",
                42 to "varbit.talent_earth_reduce_defence",
                43 to "varbit.talent_light_weapon_doublehit",
                44 to "varbit.talent_free_random_weapon_attack_chance",
                45 to "varbit.talent_smoke_counts_as_air",
                46 to "varbit.talent_ice_counts_as_water",
                47 to "varbit.talent_blood_counts_as_fire",
                48 to "varbit.talent_shadow_counts_as_earth",
                49 to "varbit.talent_ranged_strength_hp_difference",
                50 to "varbit.talent_crossbow_slow_big_hits",
                51 to "varbit.talent_crossbow_max_hit",
                52 to "varbit.talent_ranged_echo_cyclical",
                53 to "varbit.talent_bow_fast_hits",
                55 to "varbit.talent_thrown_weapon_accuracy",
                56 to "varbit.talent_thrown_weapon_multi",
                57 to "varbit.talent_max_accuracy_roll_from_range",
                58 to "varbit.talent_buffed_ranged_prayers",
                59 to "varbit.talent_bow_min_hit_stacking_increase",
                60 to "varbit.talent_bow_max_hit_stacking_increase",
                61 to "varbit.talent_prayer_restore_no_overhead",
                62 to "varbit.talent_restore_sa_energy_from_distance",
                64 to "varbit.talent_defence_recoil_scaling",
                65 to "varbit.talent_light_weapon_faster",
                66 to "varbit.talent_hit_restore_spec_energy",
                67 to "varbit.talent_overheal_consumption_boost",
                68 to "varbit.talent_unique_blindbag_damage",
                69 to "varbit.talent_melee_range_conditional_boost",
                70 to "varbit.talent_percentage_melee_maxhit_distance",
                71 to "varbit.talent_2h_melee_echos",
                72 to "varbit.talent_unique_blindbag_chance",
                73 to "varbit.talent_melee_strength_prayer_bonus",
                75 to "varbit.talent_shield_block_heal",
                76 to "varbit.talent_max_hit_style_swap",
                77 to "varbit.talent_multi_hit_str_increase",
                78 to "varbit.talent_regen_magic_level_boost",
                79 to "varbit.talent_regen_stave_charges_air",
                80 to "varbit.talent_regen_stave_charges_earth",
                81 to "varbit.talent_magic_attack_speed_powered",
                82 to "varbit.talent_crossbow_double_accuracy_roll",
                83 to "varbit.talent_air_spell_damage_active_prayers",
                84 to "varbit.talent_thorns_double_hit",
            )
    }
}
