package org.rsmod.content.leagues.demonicpacts.effects.melee

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.leagues.demonicpacts.PactsActiveFor
import org.rsmod.content.leagues.demonicpacts.effects.OPPONENT_HITPOINTS
import org.rsmod.content.leagues.demonicpacts.effects.ranged.setLevels
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/**
 * The melee pacts: D2, D3, J3, M3, G4, M2, G8, G5, G10, G6, G7, B3, J4, D4, M4, J2 and H4. Each
 * case owns its node alone with `::pactset`. In PvP the speed, range and stat pacts apply; the min
 * hit, Blindbag and extra hit pacts don't.
 */
class MeleePactsTest {
    private var Player.specialEnergy by intVarp("varp.sa_energy")

    @Test
    fun GameTestState.`D2 hits again with 40% of the rolled damage with a weapon under 1kg`() =
        meleePactTest {
            own("D2")
            wield("obj.abyssal_whip")
            val npc = target()

            queueRolls(ROLL_HIT, 10, ROLL_HIT, 12)
            meleeAttack(npc)
            assertEquals(listOf(4), recorder.dealt(HitSource.DoubleStrike).map { it.damage })

            queueRolls(ROLL_HIT, 10, ROLL_HIT, 1)
            meleeAttack(npc)
            assertEquals(listOf(4, 1), recorder.dealt(HitSource.DoubleStrike).map { it.damage })
            assertEquals(MELEE_TARGET_HP - 10 - 4 - 10 - 1, npc.hitpoints)
        }

    @Test
    fun GameTestState.`D2 needs a light weapon and doesn't trigger on special attacks`() =
        meleePactTest {
            own("D2")
            wield("obj.dragon_scimitar")
            val npc = target()
            queueRolls(ROLL_HIT, 10)
            meleeAttack(npc)

            wield("obj.dragon_dagger")
            queueRolls(ROLL_HIT, 10)
            meleeAttack(npc, special = true)
            assertTrue(recorder.dealt(HitSource.DoubleStrike).isEmpty())
        }

    @Test
    fun GameTestState.`D3 Blindbag attacks with a heavy weapon from the inventory`() =
        meleePactTest {
            own("D3")
            wield("obj.dragon_scimitar")
            carry("obj.granite_maul", "obj.coins")
            with(scope) { setLevels("stat.attack", 1) }
            val npc = target()
            assertEquals(15.0, modifiers(npc).procChancePercent(HitSource.Blindbag))

            // Base hit, Blindbag chance (hit), weapon pick, Blindbag hit, chained chance (miss).
            queueRolls(ROLL_HIT, 10, 1_499, 0, ROLL_HIT, 17, 1_500)
            meleeAttack(npc)

            assertEquals(listOf(17), recorder.dealt(HitSource.Blindbag).map { it.damage })
            assertEquals(MELEE_TARGET_HP - 10 - 17, npc.hitpoints)
            assertEquals(obj("obj.granite_maul"), bagWeapon())
        }

    @Test
    fun GameTestState.`D3 Blindbag attacks chain into more Blindbag attacks`() = meleePactTest {
        own("D3")
        wield("obj.dragon_scimitar")
        carry("obj.granite_maul", "obj.elder_maul")
        val npc = target()

        queueRolls(ROLL_HIT, 10, ROLL_HIT, 1, ROLL_HIT, 5, ROLL_HIT, 0, ROLL_HIT, 6, ROLL_MISS)
        meleeAttack(npc)

        val blindbags = recorder.rolled.filter { it.source == HitSource.Blindbag }
        assertEquals(listOf(1, 2), blindbags.map { it.chainDepth })
        assertEquals(listOf(5, 6), recorder.dealt(HitSource.Blindbag).map { it.damage })
        assertEquals(obj("obj.granite_maul"), bagWeapon())
    }

    @Test
    fun GameTestState.`D3 Blindbag uses the bag weapon's stats`() = meleePactTest {
        own("D3")
        wield("obj.dragon_scimitar")
        carry("obj.granite_maul")
        val npc = target()
        queueRolls(ROLL_HIT, 10, ROLL_HIT, 0, ROLL_HIT, 3, ROLL_MISS)
        meleeAttack(npc)

        val scimitar = obj("obj.dragon_scimitar")
        val maul = obj("obj.granite_maul")
        val bestMaulAttack =
            maxOf(
                maul.param(params.attack_stab),
                maul.param(params.attack_slash),
                maul.param(params.attack_crush),
            )
        val modifiers = modifiers(npc, source = HitSource.Blindbag)
        val attack = bestMaulAttack - scimitar.param(params.attack_slash)
        assertEquals(attack, modifiers.attackBonusFlat)
        val strength = maul.param(params.melee_strength) - scimitar.param(params.melee_strength)
        assertEquals(strength, modifiers.meleeStrengthFlat)
    }

    @Test
    fun GameTestState.`D3 Blindbag needs a heavy weapon, one in the inventory and no special`() =
        meleePactTest {
            own("D3")
            wield("obj.dragon_scimitar")
            val npc = target()
            assertEquals(0.0, modifiers(npc).procChancePercent(HitSource.Blindbag))

            carry("obj.granite_maul")
            assertEquals(0.0, modifiers(npc, special = true).procChancePercent(HitSource.Blindbag))
            queueRolls(ROLL_HIT, 10)
            meleeAttack(npc, special = true)

            wield("obj.abyssal_whip")
            assertEquals(0.0, modifiers(npc).procChancePercent(HitSource.Blindbag))
            queueRolls(ROLL_HIT, 10)
            meleeAttack(npc)
            assertTrue(recorder.dealt(HitSource.Blindbag).isEmpty())
        }

    @Test
    fun GameTestState.`J3 adds 2% Blindbag chance per unique heavy weapon, up to 5`() =
        meleePactTest {
            own("D3", "J3")
            wield("obj.dragon_scimitar")
            val npc = target()
            carry("obj.granite_maul", "obj.granite_maul", "obj.elder_maul")
            assertEquals(19.0, modifiers(npc).procChancePercent(HitSource.Blindbag))

            carry(*HEAVY_WEAPONS.toTypedArray())
            assertEquals(25.0, modifiers(npc).procChancePercent(HitSource.Blindbag))
        }

    @Test
    fun GameTestState.`M3 adds 2% Blindbag max hit per unique heavy weapon, up to 5`() =
        meleePactTest {
            own("M3")
            wield("obj.dragon_scimitar")
            val npc = target()
            carry("obj.granite_maul", "obj.elder_maul")
            assertEquals(4.0, modifiers(npc, source = HitSource.Blindbag).maxHitPercent)
            assertEquals(0.0, modifiers(npc).maxHitPercent)

            carry(*HEAVY_WEAPONS.toTypedArray())
            assertEquals(10.0, modifiers(npc, source = HitSource.Blindbag).maxHitPercent)
        }

    @Test
    fun GameTestState.`G4 restores 2% special energy for an attack from 2 tiles, any style`() =
        meleePactTest {
            own("G4")
            wield("obj.dragon_halberd")
            scope.player.specialEnergy = 500
            val close = target(tiles = 1)
            queueRolls(ROLL_HIT, 5)
            meleeAttack(close)
            assertEquals(500, scope.player.specialEnergy)

            val far = target(tiles = 2)
            queueRolls(ROLL_MISS)
            meleeAttack(far)
            assertEquals(520, scope.player.specialEnergy)

            rangedHit(far, damage = 3)
            assertEquals(540, scope.player.specialEnergy)
        }

    @Test
    fun GameTestState.`M2 restores 2% special energy per melee hit that deals damage`() =
        meleePactTest {
            own("M2")
            wield("obj.abyssal_whip")
            scope.player.specialEnergy = 500
            val npc = target()
            queueRolls(ROLL_MISS)
            meleeAttack(npc)
            assertEquals(500, scope.player.specialEnergy)

            queueRolls(ROLL_HIT, 5)
            meleeAttack(npc)
            assertEquals(520, scope.player.specialEnergy)

            rangedHit(npc, damage = 3)
            assertEquals(520, scope.player.specialEnergy)
        }

    @Test
    fun GameTestState.`M2 counts extra hits too`() = meleePactTest {
        own("M2", "D2")
        wield("obj.abyssal_whip")
        scope.player.specialEnergy = 500
        val npc = target()
        queueRolls(ROLL_HIT, 5, ROLL_HIT, 5)
        meleeAttack(npc)
        assertEquals(540, scope.player.specialEnergy)
    }

    @Test
    fun GameTestState.`G8 spends 5 overhealed hitpoints for +5 min hit`() = meleePactTest {
        own("G8")
        wield("obj.abyssal_whip")
        val npc = target()
        with(scope) { player.setCurrentLevel("stat.hitpoints", 103) }
        assertEquals(0, modifiers(npc).minHitFlat)

        with(scope) { player.setCurrentLevel("stat.hitpoints", 105) }
        assertEquals(1 + 5, modifiers(npc).minHitFlat)
        assertEquals(0, modifiers(npc, source = HitSource.DoubleStrike).minHitFlat)
        queueRolls(ROLL_HIT, 7)
        meleeAttack(npc)
        assertEquals(100, scope.player.hitpoints)
        assertEquals(0, modifiers(npc).minHitFlat)

        queueRolls(ROLL_HIT, 7)
        meleeAttack(npc)
        assertEquals(100, scope.player.hitpoints)
    }

    @Test
    fun GameTestState.`G5 heals 1 per tile to the target on a 10% roll`() =
        distanceHealingCase("G5")

    @Test
    fun GameTestState.`G10 heals 1 per tile to the target on a 10% roll`() =
        distanceHealingCase("G10")

    @Test
    fun GameTestState.`G5 and G10 stack to 20%`() = meleePactTest {
        own("G5", "G10")
        wield("obj.dragon_halberd")
        with(scope) { player.setCurrentLevel("stat.hitpoints", 50) }
        val npc = target(tiles = 2)
        queueRolls(ROLL_HIT, 5, 1_999)
        meleeAttack(npc)
        assertEquals(52, scope.player.hitpoints)

        queueRolls(ROLL_HIT, 5, 2_000)
        meleeAttack(npc)
        assertEquals(52, scope.player.hitpoints)
    }

    @Test
    fun GameTestState.`G5 also heals on Thorns`() = meleePactTest {
        own("G5", "D1")
        wear(Wearpos.LeftHand, "obj.rune_kiteshield")
        with(scope) { player.setCurrentLevel("stat.hitpoints", 50) }
        val npc = target()
        // The player's defend sound pick, then the healing chance.
        queueRolls(0, 999)
        scope.player.queueHit(npc, 1, HitType.Melee, 4, deps.playerHitModifier)
        scope.advance(MELEE_SETTLE_TICKS)
        assertEquals(50 - 4 + 1, scope.player.hitpoints)
        assertEquals(MELEE_TARGET_HP - 3, npc.hitpoints)
    }

    @Test
    fun GameTestState.`G6 adds 20% of the Strength level with a light or one-handed weapon`() =
        meleePactTest {
            own("G6")
            val npc = target()
            for (weapon in listOf("obj.abyssal_whip", "obj.dragon_claws", "obj.dragon_scimitar")) {
                wield(weapon)
                assertEquals(19, modifiers(npc).meleeStrengthFlat) { weapon }
            }
            with(scope) { player.setCurrentLevel("stat.strength", 118) }
            assertEquals(23, modifiers(npc).meleeStrengthFlat)

            wield("obj.granite_maul")
            assertEquals(0, modifiers(npc).meleeStrengthFlat)
            wield("obj.abyssal_whip")
            assertEquals(0, modifiers(npc, CombatStyle.Ranged).rangedStrengthFlat)
        }

    @Test
    fun GameTestState.`G7 adds 50% of the worn prayer bonus to melee strength`() = meleePactTest {
        own("G7")
        wield("obj.granite_maul")
        wear(Wearpos.Hat, "obj.blessedstar")
        wear(Wearpos.Torso, "obj.monkrobetop")
        val npc = target()
        val prayer = deps.wornBonuses.prayerBonus(scope.player)
        assertTrue(prayer >= 2) { "prayer bonus: $prayer" }
        assertEquals(prayer / 2, modifiers(npc).meleeStrengthFlat)
        assertEquals(0, modifiers(npc, CombatStyle.Magic).meleeStrengthFlat)
    }

    @Test
    fun GameTestState.`B3 raises the melee min hit by 3 per tile to the target`() = meleePactTest {
        own("B3")
        wield("obj.dragon_halberd")
        assertEquals(1 + 3, modifiers(target(tiles = 1)).minHitFlat)
        assertEquals(1 + 6, modifiers(target(tiles = 2)).minHitFlat)
        assertEquals(0, modifiers(target(tiles = 2), CombatStyle.Ranged).minHitFlat)
    }

    @Test
    fun GameTestState.`J4 raises the melee max hit by 4% and 4% more per 3 tiles`() =
        meleePactTest {
            own("J4")
            wield("obj.dragon_halberd")
            assertEquals(4.0, modifiers(target(tiles = 1)).maxHitPercent)
            assertEquals(4.0, modifiers(target(tiles = 2)).maxHitPercent)
            assertEquals(8.0, modifiers(target(tiles = 3)).maxHitPercent)
            assertEquals(12.0, modifiers(target(tiles = 7)).maxHitPercent)
            assertEquals(0.0, modifiers(target(tiles = 3), CombatStyle.Magic).maxHitPercent)
        }

    @Test
    fun GameTestState.`D4 doubles the range of two-handed melee weapons`() = meleePactTest {
        own("D4")
        val npc = target()
        wield("obj.granite_maul")
        assertEquals(2, attackRange(npc, 1))
        wield("obj.dragon_halberd")
        assertEquals(HALBERD_RANGE, obj("obj.dragon_halberd").param(params.attackrange))
        assertEquals(HALBERD_RANGE * 2, attackRange(npc, HALBERD_RANGE))
        wield("obj.dragon_scimitar")
        assertEquals(1, attackRange(npc, 1))
    }

    @Test
    fun GameTestState.`M4 makes a range of 4 or more 7 and halberds attack every 5 ticks`() =
        meleePactTest {
            own("M4")
            val npc = target()
            wield("obj.dragon_halberd")
            val halberdSpeed = obj("obj.dragon_halberd").param(params.attackrate)
            assertTrue(halberdSpeed > 5) { "halberd speed: $halberdSpeed" }
            assertEquals(HALBERD_RANGE, attackRange(npc, HALBERD_RANGE))
            assertEquals(5, attackDelay(npc, halberdSpeed))

            own("M4", "D4")
            assertEquals(7, attackRange(npc, HALBERD_RANGE))
            wield("obj.granite_maul")
            assertEquals(2, attackRange(npc, 1))
            assertEquals(7, attackDelay(npc, 7))
        }

    @Test
    fun GameTestState.`J2 makes melee weapons under 1kg attack 1 tick faster`() = meleePactTest {
        own("J2")
        val npc = target()
        wield("obj.abyssal_whip")
        assertEquals(3, attackDelay(npc, 4))
        wield("obj.dragon_scimitar")
        assertEquals(4, attackDelay(npc, 4))
    }

    @Test
    fun GameTestState.`H4 gives strength to every style with an off-hand`() = meleePactTest {
        own("H4")
        wield("obj.abyssal_whip")
        val npc = target()
        wear(Wearpos.LeftHand, "obj.rune_kiteshield")
        assertEquals(AttackModifiers.NONE, modifiers(npc))

        wear(Wearpos.LeftHand, "obj.rune_parryingdagger")
        assertEquals(5, modifiers(npc).meleeStrengthFlat)
        assertEquals(5, modifiers(npc, CombatStyle.Ranged).rangedStrengthFlat)
        assertEquals(2.0, modifiers(npc, CombatStyle.Magic).magicDamagePercent)
    }

    @Test
    fun GameTestState.`in PvP the speed, range and stat pacts apply`() = meleePactTest {
        own("J2", "D4", "J4", "G7")
        wield("obj.abyssal_whip")
        wear(Wearpos.Torso, "obj.monkrobetop")
        val opponent = opponent()
        assertEquals(3, attackDelay(opponent, 4))
        assertEquals(4.0, modifiers(opponent).maxHitPercent)
        val prayer = deps.wornBonuses.prayerBonus(scope.player)
        assertEquals(prayer / 2, modifiers(opponent).meleeStrengthFlat)
        wield("obj.granite_maul")
        assertEquals(2, attackRange(opponent, 1))
    }

    @Test
    fun GameTestState.`in PvP the min hit, Blindbag and extra hit pacts don't apply`() =
        meleePactTest {
            own("B3", "D2", "D3")
            wield("obj.dragon_scimitar")
            carry("obj.granite_maul")
            val npc = target(tiles = 2)
            val opponent = opponent(tiles = 2)
            assertEquals(15.0, modifiers(npc).procChancePercent(HitSource.Blindbag))
            assertEquals(1 + 6, modifiers(npc).minHitFlat)
            assertEquals(AttackModifiers.NONE, modifiers(opponent))

            // Only the accuracy and damage rolls are made: no Blindbag chance, no D2 second hit.
            queueRolls(ROLL_HIT, 10)
            pvpMeleeAttack(opponent)
            assertEquals(OPPONENT_HITPOINTS - 10, opponent.hitpoints)

            wield("obj.abyssal_whip")
            queueRolls(ROLL_HIT, 10)
            pvpMeleeAttack(opponent)
            assertEquals(OPPONENT_HITPOINTS - 20, opponent.hitpoints)
            assertTrue(recorder.rolled.isEmpty())
        }

    @Test
    fun GameTestState.`no melee pact applies once the player's pacts are inactive`() {
        val activation = PactsActiveFor()
        meleePactTest(activation) {
            activation.players += scope.player
            own(*NODES.toTypedArray())
            activation.players.clear()
            wield("obj.dragon_scimitar")
            wear(Wearpos.LeftHand, "obj.rune_parryingdagger")
            carry("obj.granite_maul")
            val npc = target(tiles = 2)
            assertEquals(AttackModifiers.NONE, modifiers(npc))
            assertEquals(1, attackRange(npc, 1))
            assertEquals(4, attackDelay(npc, 4))
        }
    }

    private fun GameTestState.distanceHealingCase(id: String) = meleePactTest {
        own(id)
        wield("obj.dragon_halberd")
        with(scope) { player.setCurrentLevel("stat.hitpoints", 50) }
        val npc = target(tiles = 2)
        queueRolls(ROLL_MISS, 999)
        meleeAttack(npc)
        assertEquals(52, scope.player.hitpoints)

        queueRolls(ROLL_HIT, 5, 1_000)
        meleeAttack(npc)
        assertEquals(52, scope.player.hitpoints)
    }

    private fun MeleePactsTestScope.bagWeapon(): ItemServerType? =
        deps.blindbag.weapon(scope.player)

    private fun obj(internal: String): ItemServerType =
        checkNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) { internal }

    private companion object {
        const val HALBERD_RANGE = 2

        val HEAVY_WEAPONS: List<String> =
            listOf(
                "obj.granite_maul",
                "obj.elder_maul",
                "obj.dragon_warhammer",
                "obj.ghrazi_rapier",
                "obj.inquisitors_mace",
                "obj.saradomin_sword",
            )

        val NODES: List<String> =
            listOf(
                "D2", "D3", "J3", "M3", "G4", "M2", "G8", "G5", "G10", "G6", "G7", "B3", "J4",
                "D4", "M4", "J2", "H4",
            )
    }
}
