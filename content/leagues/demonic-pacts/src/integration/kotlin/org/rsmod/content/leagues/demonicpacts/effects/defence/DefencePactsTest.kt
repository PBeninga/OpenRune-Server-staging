package org.rsmod.content.leagues.demonicpacts.effects.defence

import com.google.inject.Module
import dev.openrune.util.Wearpos
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.asMechanic
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.PlayerHealSource
import org.rsmod.api.player.stat.PlayerHealing
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.PactsActiveFor
import org.rsmod.content.leagues.demonicpacts.PactsActiveForEveryone
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.DemonicPactsStateScript
import org.rsmod.content.leagues.demonicpacts.state.PactEffectVarbits
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

/** The defence, overheal and prayer pacts: D1, J1, M1, G1, J8, F2, F13, H9 and I6. */
class DefencePactsTest {
    @Test
    fun GameTestState.`D1 Thorns hits the attacker for 3 on every hitsplat, including 0s`() =
        pactTest { deps ->
            own("D1")
            wear(Wearpos.LeftHand, "obj.rune_kiteshield")
            val npc = setUpAttacker()

            npcHit(deps, npc, damage = 0)
            advance(SETTLE_TICKS)
            assertEquals(NPC_HP - 3, npc.hitpoints)

            npcHit(deps, npc, damage = 5)
            advance(SETTLE_TICKS)
            assertEquals(NPC_HP - 6, npc.hitpoints)
            assertEquals(PLAYER_HP - 5, player.hitpoints)
        }

    @Test
    fun GameTestState.`D1 Thorns needs a shield, not an off-hand`() = pactTest { deps ->
        own("D1")
        wear(Wearpos.LeftHand, "obj.rune_parryingdagger")
        val npc = setUpAttacker()

        npcHit(deps, npc, damage = 4)
        advance(SETTLE_TICKS)
        assertEquals(NPC_HP, npc.hitpoints)
    }

    @Test
    fun GameTestState.`Thorns and reflect skip mechanic damage`() = pactTest { deps ->
        own("D1", "G1")
        wear(Wearpos.LeftHand, "obj.rune_kiteshield")
        setLevels("stat.defence", 99)
        val npc = setUpAttacker()

        npcHit(deps, npc, damage = 6, mechanic = true)
        advance(SETTLE_TICKS)
        assertEquals(PLAYER_HP - 6, player.hitpoints)
        assertEquals(NPC_HP, npc.hitpoints)
    }

    @Test
    fun GameTestState.`Thorns doesn't work against players`() = pactTest { deps ->
        own("D1")
        wear(Wearpos.LeftHand, "obj.rune_kiteshield")
        setUpAttacker()
        val attacker = registerPlayer(coords = TEST_COORDS.translateX(-1))
        setLevels("stat.hitpoints", PLAYER_HP, attacker)

        player.queueHit(attacker, 1, HitType.Melee, 2, deps.playerHitModifier)
        advance(SETTLE_TICKS)
        assertEquals(PLAYER_HP - 2, player.hitpoints)
        assertEquals(PLAYER_HP, attacker.hitpoints)
    }

    @Test
    fun GameTestState.`no effect applies once the player's pacts are inactive`() {
        val activation = PactsActiveFor()
        pactTest(activation) { deps ->
            activation.players += player
            own("D1", "J8", "F2", "I6")
            wear(Wearpos.LeftHand, "obj.rune_kiteshield")
            val npc = setUpAttacker()
            setLevels("stat.prayer", 50)
            player.setCurrentLevel("stat.prayer", 10)
            activation.players.clear()

            npcHit(deps, npc, damage = 0)
            advance(PactPrayerRestore.BASE_INTERVAL * 2 + 1)
            assertEquals(NPC_HP, npc.hitpoints)
            assertEquals(10, player.prayerLvl)
            assertEquals(0, deps.healing.heal(player, 5, PlayerHealSource.Pact))
        }
    }

    @Test
    fun GameTestState.`J1 adds 1% of the total defence bonuses to Thorns`() = pactTest { deps ->
        own("D1", "J1")
        wear(Wearpos.LeftHand, "obj.rune_kiteshield")
        wear(Wearpos.Torso, "obj.rune_platebody")
        val npc = setUpAttacker()
        val bonuses = deps.wornBonuses.calculate(player)
        val total =
            bonuses.defStab + bonuses.defSlash + bonuses.defCrush + bonuses.defMagic +
                bonuses.defRange
        assertTrue(total >= 200) { "total defence bonuses: $total" }

        npcHit(deps, npc, damage = 0)
        advance(SETTLE_TICKS)
        assertEquals(NPC_HP - (3 + total / 100), npc.hitpoints)
    }

    @Test
    fun GameTestState.`J1 alone deals no damage`() = pactTest { deps ->
        own("J1")
        wear(Wearpos.LeftHand, "obj.rune_kiteshield")
        val npc = setUpAttacker()

        npcHit(deps, npc, damage = 0)
        advance(SETTLE_TICKS)
        assertEquals(NPC_HP, npc.hitpoints)
    }

    @Test
    fun GameTestState.`M1 makes Thorns hit twice, the second for half`() = pactTest { deps ->
        own("D1", "M1")
        wear(Wearpos.LeftHand, "obj.rune_kiteshield")
        val npc = setUpAttacker()

        npcHit(deps, npc, damage = 0)
        advance(SETTLE_TICKS)
        assertEquals(NPC_HP - (3 + 1), npc.hitpoints)
    }

    @Test
    fun GameTestState.`G1 reflects a hit at 0,1% per Defence level`() = pactTest { deps ->
        own("G1")
        wear(Wearpos.LeftHand, "obj.rune_kiteshield")
        setLevels("stat.defence", 99)
        val npc = setUpAttacker()

        // Each hit rolls its defend sound, then the reflect: 99 Defence reflects rolls 0..98.
        random.next = 0
        random.then = 98
        npcHit(deps, npc, damage = 10)
        advance(SETTLE_TICKS)
        assertEquals(PLAYER_HP - 10, player.hitpoints)
        assertEquals(NPC_HP - 10, npc.hitpoints)

        random.then = 0
        random.then = 99
        npcHit(deps, npc, damage = 7)
        advance(SETTLE_TICKS)
        assertEquals(PLAYER_HP - 17, player.hitpoints)
        assertEquals(NPC_HP - 10, npc.hitpoints)
    }

    @Test
    fun GameTestState.`G1 needs a shield`() = pactTest { deps ->
        own("G1")
        setLevels("stat.defence", 99)
        val npc = setUpAttacker()

        npcHit(deps, npc, damage = 10)
        advance(SETTLE_TICKS)
        assertEquals(NPC_HP, npc.hitpoints)
    }

    @Test
    fun GameTestState.`J8 heals 2 and restores 2 prayer on a 0 with a two-handed weapon`() =
        pactTest { deps ->
            own("J8")
            wear(Wearpos.RightHand, "obj.rune_2h_sword")
            val npc = setUpAttacker()
            player.setCurrentLevel("stat.hitpoints", 50)
            setLevels("stat.prayer", 50)
            player.setCurrentLevel("stat.prayer", 10)

            npcHit(deps, npc, damage = 0)
            advance(SETTLE_TICKS)
            assertEquals(52, player.hitpoints)
            assertEquals(12, player.prayerLvl)

            npcHit(deps, npc, damage = 3)
            advance(SETTLE_TICKS)
            assertEquals(49, player.hitpoints)
            assertEquals(12, player.prayerLvl)
        }

    @Test
    fun GameTestState.`J8 works with an off-hand but not a shield`() = pactTest { deps ->
        own("J8")
        wear(Wearpos.LeftHand, "obj.rune_parryingdagger")
        val npc = setUpAttacker()
        player.setCurrentLevel("stat.hitpoints", 50)

        npcHit(deps, npc, damage = 0)
        advance(SETTLE_TICKS)
        assertEquals(52, player.hitpoints)

        wear(Wearpos.LeftHand, "obj.rune_kiteshield")
        npcHit(deps, npc, damage = 0)
        advance(SETTLE_TICKS)
        assertEquals(52, player.hitpoints)
    }

    @Test
    fun GameTestState.`J8's heal overheals with an overheal pact`() = pactTest { deps ->
        own("J8", "F2")
        wear(Wearpos.RightHand, "obj.rune_2h_sword")
        val npc = setUpAttacker()

        npcHit(deps, npc, damage = 0)
        advance(SETTLE_TICKS)
        assertEquals(PLAYER_HP + 2, player.hitpoints)
    }

    @Test
    fun GameTestState.`F2 lets pact healing overheal up to 30% of base hitpoints`() =
        overhealCase("F2")

    @Test
    fun GameTestState.`F13 lets pact healing overheal up to 30% of base hitpoints`() =
        overhealCase("F13")

    @Test
    fun GameTestState.`H9 lets pact healing overheal up to 30% of base hitpoints`() =
        overhealCase("H9")

    @Test
    fun GameTestState.`overheal nodes stack and need pact healing`() = pactTest { deps ->
        own("F2", "F13")
        setLevels("stat.hitpoints", 99)
        assertEquals(0, deps.healing.heal(player, 50, PlayerHealSource.Standard))
        assertEquals(0, deps.healing.heal(player, 50, PlayerHealSource.Relic))
        assertEquals(59, deps.healing.heal(player, 100, PlayerHealSource.Pact))
        assertEquals(99 + 59, player.hitpoints)

        own("F2", "F13", "H9")
        assertEquals(30, deps.healing.heal(player, 100, PlayerHealSource.Pact))
        assertEquals(99 + 89, player.hitpoints)
    }

    @Test
    fun GameTestState.`the panel's overheal total is the overheal the server gives`() = pactTest {
        deps ->
        own("F2", "F13", "H9")
        setLevels("stat.hitpoints", 100)
        assertEquals(90, player.vars[OVERHEAL_VARBIT])
        assertEquals(90, deps.healing.heal(player, 200, PlayerHealSource.Pact))
        assertEquals(100 + player.vars[OVERHEAL_VARBIT], player.hitpoints)
    }

    @Test
    fun GameTestState.`without a pact, pact healing stops at the base level`() = pactTest {
        deps ->
        setLevels("stat.hitpoints", 99)
        player.setCurrentLevel("stat.hitpoints", 90)
        assertEquals(9, deps.healing.heal(player, 50, PlayerHealSource.Pact))
        assertEquals(99, player.hitpoints)
    }

    @Test
    fun GameTestState.`I6 restores a prayer point every 15 ticks`() = pactTest { _ ->
        setLevels("stat.prayer", 50)
        player.setCurrentLevel("stat.prayer", 10)
        own("I6")

        assertEquals(PactPrayerRestore.BASE_INTERVAL, ticksUntilPrayerRestored())
        assertEquals(11, player.prayerLvl)
        assertEquals(PactPrayerRestore.BASE_INTERVAL, ticksUntilPrayerRestored())
        assertEquals(12, player.prayerLvl)
    }

    @Test
    fun GameTestState.`I6's interval drops a tick per 7 prayer bonus`() = pactTest { deps ->
        wear(Wearpos.Hat, "obj.blessedstar")
        wear(Wearpos.Torso, "obj.monkrobetop")
        val bonus = deps.wornBonuses.prayerBonus(player)
        assertTrue(bonus >= 14) { "prayer bonus: $bonus" }
        val expected = PactPrayerRestore.interval(bonus)
        assertEquals(PactPrayerRestore.BASE_INTERVAL - bonus / 7, expected)

        setLevels("stat.prayer", 50)
        player.setCurrentLevel("stat.prayer", 10)
        own("I6")
        ticksUntilPrayerRestored()
        assertEquals(expected, ticksUntilPrayerRestored())
    }

    @Test
    fun GameTestState.`I6 doesn't restore while a protection prayer is active`() = pactTest { _ ->
        setLevels("stat.prayer", 50)
        player.setCurrentLevel("stat.prayer", 10)
        player.setVarBit("varbit.prayer_protectfrommelee", 1)
        own("I6")

        advance(PactPrayerRestore.BASE_INTERVAL * 2 + 1)
        assertEquals(10, player.prayerLvl)

        player.setVarBit("varbit.prayer_protectfrommelee", 0)
        advance(PactPrayerRestore.BASE_INTERVAL)
        assertEquals(11, player.prayerLvl)
    }

    @Test
    fun GameTestState.`I6 stops when the pact is gone`() = pactTest { _ ->
        setLevels("stat.prayer", 50)
        player.setCurrentLevel("stat.prayer", 10)
        own("I6")
        own("none")

        advance(PactPrayerRestore.BASE_INTERVAL * 2 + 1)
        assertEquals(10, player.prayerLvl)
    }

    @Test
    fun `the interval formula floors at one tick`() {
        assertEquals(15, PactPrayerRestore.interval(0))
        assertEquals(15, PactPrayerRestore.interval(-20))
        assertEquals(15, PactPrayerRestore.interval(6))
        assertEquals(14, PactPrayerRestore.interval(7))
        assertEquals(1, PactPrayerRestore.interval(98))
        assertEquals(1, PactPrayerRestore.interval(500))
    }

    private fun GameTestState.overhealCase(id: String) = pactTest { deps ->
        own(id)
        setLevels("stat.hitpoints", 99)
        assertEquals(29, deps.healing.heal(player, 50, PlayerHealSource.Pact))
        assertEquals(99 + 29, player.hitpoints)
        assertEquals(0, deps.healing.heal(player, 1, PlayerHealSource.Pact))
    }

    private fun GameTestState.pactTest(
        activation: Module = PactsActiveForEveryone,
        body: GameTestScope.(Deps) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            activation,
            DefencePactsScript::class,
            DemonicPactsStateScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            testBody = body,
        )

    /** Owns exactly [ids] with the admin `::pactset`, as the effect tests do. */
    private fun GameTestScope.own(vararg ids: String) {
        player.modLevel = Rights.ADMINISTRATOR
        player.cheat("pactset", *ids)
    }

    private fun GameTestScope.wear(wearpos: Wearpos, obj: String) {
        player.worn[wearpos.slot] = InvObj(obj)
    }

    private fun GameTestScope.setUpAttacker(): Npc {
        player.placeAt(TEST_COORDS)
        setLevels("stat.hitpoints", PLAYER_HP)
        val coords = TEST_COORDS.translateX(1)
        allocZoneCollision(coords)
        val npc = spawnNpc(coords, "npc.man")
        npc.baseHitpointsLvl = NPC_HP
        npc.hitpoints = NPC_HP
        return npc
    }

    private fun GameTestScope.npcHit(
        deps: Deps,
        npc: Npc,
        damage: Int,
        mechanic: Boolean = false,
    ) {
        val modifier =
            if (mechanic) deps.playerHitModifier.asMechanic() else deps.playerHitModifier
        player.queueHit(npc, 1, HitType.Melee, damage, modifier = modifier)
    }

    private fun GameTestScope.ticksUntilPrayerRestored(): Int {
        val start = player.prayerLvl
        for (tick in 1..PactPrayerRestore.BASE_INTERVAL * 2) {
            advance()
            if (player.prayerLvl != start) {
                return tick
            }
        }
        error("Prayer was not restored.")
    }

    private fun GameTestScope.setLevels(stat: String, level: Int, target: Player = player) {
        target.setBaseLevel(stat, level)
        target.setCurrentLevel(stat, level)
    }

    class Deps
    @Inject
    constructor(
        val pacts: DemonicPacts,
        val tree: PactTree,
        val healing: PlayerHealing,
        val wornBonuses: WornBonuses,
        val playerHitModifier: PlayerHitModifier,
    )

    private companion object {
        const val PLAYER_HP = 99
        const val NPC_HP = 100
        const val SETTLE_TICKS = 4
        val TEST_COORDS: CoordGrid = CoordGrid(0, 50, 50, 22, 18)
        val OVERHEAL_VARBIT: String =
            PactEffectVarbits.VARBITS.getValue(DefencePactEffects.OVERHEAL)
    }
}
