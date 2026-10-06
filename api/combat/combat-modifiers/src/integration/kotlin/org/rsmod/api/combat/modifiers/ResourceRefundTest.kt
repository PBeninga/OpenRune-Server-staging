package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isSuccess
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.quiver
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/** Regenerate: a refunded resource is not consumed. */
class ResourceRefundTest {
    @Test
    fun GameTestState.`ammo is consumed unless a listener refunds it`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            // Arrows that are not regenerated may drop, which needs an observer id.
            player.observerUUID = 1L
            player.quiver = InvObj("obj.bronze_arrow", 10)
            val arrows = objType("obj.bronze_arrow")

            deps.ammo.useQuiverAmmo(player, arrows, TEST_COORDS, dropDelay = 1)
            assertEquals(9, player.quiver?.count)

            hooks.listener.resourceDecision = { ResourceDecision.Refund }
            deps.ammo.useQuiverAmmo(player, arrows, TEST_COORDS, dropDelay = 1)
            assertEquals(9, player.quiver?.count)

            val event = hooks.listener.resources.last()
            assertEquals(ResourceKind.Ammo, event.context.kind)
            assertEquals(1, event.context.count)
            assertTrue(event.context.obj?.isType("obj.bronze_arrow") == true)
            assertFalse(event.regenerated)
        }
    }

    @Test
    fun GameTestState.`regenerate chance is summed from providers and rolled`() {
        val hooks = TestHooks()
        hooks.provider.resource = ResourceModifiers(regenerateChancePercent = 25.0)
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            // Arrows that are not regenerated may drop, which needs an observer id.
            player.observerUUID = 1L
            player.quiver = InvObj("obj.bronze_arrow", 10)
            val arrows = objType("obj.bronze_arrow")

            // 25% = 2500 basis points: a roll of 2499 regenerates, 2500 does not.
            random.next = 2_499
            deps.ammo.useQuiverAmmo(player, arrows, TEST_COORDS, dropDelay = 1)
            assertEquals(10, player.quiver?.count)
            assertTrue(hooks.listener.resources.last().regenerated)

            random.next = 2_500
            deps.ammo.useQuiverAmmo(player, arrows, TEST_COORDS, dropDelay = 1)
            assertEquals(9, player.quiver?.count)
            assertFalse(hooks.listener.resources.last().regenerated)
        }
    }

    @Test
    fun GameTestState.`refunded runes are not consumed`() {
        val hooks = TestHooks()
        hooks.listener.resourceDecision = { event ->
            if (event.context.obj?.isType("obj.airrune") == true) {
                ResourceDecision.Refund
            } else {
                ResourceDecision.Consume
            }
        }
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            player.inv[0] = InvObj("obj.airrune", 10)
            player.inv[1] = InvObj("obj.mindrune", 10)
            val spell = checkNotNull(deps.spells.getObjSpell(objType("obj.01_wind_strike")))

            val result = deps.runes.attemptCast(player, spell)
            assertTrue(result.isSuccess())

            assertEquals(10, player.inv.count("obj.airrune"))
            assertEquals(9, player.inv.count("obj.mindrune"))
            val kinds = hooks.listener.resources.map { it.context.kind }.toSet()
            assertEquals(setOf(ResourceKind.Rune), kinds)
        }
    }

    @Test
    fun GameTestState.`rune pouch runes name the rune being spent`() {
        val hooks = TestHooks()
        hooks.listener.resourceDecision = { event ->
            if (event.context.obj?.isType("obj.airrune") == true) {
                ResourceDecision.Refund
            } else {
                ResourceDecision.Consume
            }
        }
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            player.inv[0] = InvObj("obj.bh_rune_pouch")
            player.inv[1] = InvObj("obj.mindrune", 10)
            player.fillRunePouchSlot(deps, slot = 1, rune = "obj.airrune", count = 10)
            val spell = checkNotNull(deps.spells.getObjSpell(objType("obj.01_wind_strike")))

            assertTrue(deps.runes.attemptCast(player, spell).isSuccess())

            assertEquals(10, player.vars["varbit.rune_pouch_quantity_1"])
            assertEquals(9, player.inv.count("obj.mindrune"))
            val pouchEvent =
                hooks.listener.resources.single { it.context.obj?.isType("obj.airrune") == true }
            assertEquals(ResourceKind.Rune, pouchEvent.context.kind)
            assertEquals(1, pouchEvent.context.count)
        }
    }

    @Test
    fun GameTestState.`added runes go to the pouch slot holding them, else the inventory`() {
        runModifierTest(CombatModifierRegistry.EMPTY) { deps ->
            player.placeAt(TEST_COORDS)
            player.inv[0] = InvObj("obj.bh_rune_pouch")
            player.fillRunePouchSlot(deps, slot = 2, rune = "obj.waterrune", count = 5)

            assertTrue(deps.runes.addRunes(player, objType("obj.waterrune")))
            assertEquals(6, player.vars["varbit.rune_pouch_quantity_2"])
            assertEquals(0, player.inv.count("obj.waterrune"))

            assertTrue(deps.runes.addRunes(player, objType("obj.firerune"), count = 3))
            assertEquals(3, player.inv.count("obj.firerune"))
        }
    }

    @Test
    fun GameTestState.`added runes overflow a full pouch slot into the inventory`() {
        runModifierTest(CombatModifierRegistry.EMPTY) { deps ->
            player.placeAt(TEST_COORDS)
            player.inv[0] = InvObj("obj.bh_rune_pouch")
            val full = MagicRuneManager.RUNE_POUCH_SLOT_CAPACITY
            player.fillRunePouchSlot(deps, slot = 1, rune = "obj.earthrune", count = full)

            assertTrue(deps.runes.addRunes(player, objType("obj.earthrune")))
            assertEquals(full, player.vars["varbit.rune_pouch_quantity_1"])
            assertEquals(1, player.inv.count("obj.earthrune"))
        }
    }

    @Test
    fun GameTestState.`added runes are refused when there is no room`() {
        runModifierTest(CombatModifierRegistry.EMPTY) { deps ->
            player.placeAt(TEST_COORDS)
            for (slot in 0 until player.inv.size) {
                player.inv[slot] = InvObj("obj.bronze_dagger")
            }

            assertFalse(deps.runes.addRunes(player, objType("obj.airrune")))
            assertEquals(0, player.inv.count("obj.airrune"))
        }
    }

    @Test
    fun GameTestState.`a spell cast entirely from the rune pouch succeeds`() {
        runModifierTest(CombatModifierRegistry.EMPTY) { deps ->
            player.placeAt(TEST_COORDS)
            player.inv[0] = InvObj("obj.bh_rune_pouch")
            player.fillRunePouchSlot(deps, slot = 1, rune = "obj.airrune", count = 10)
            player.fillRunePouchSlot(deps, slot = 2, rune = "obj.mindrune", count = 10)
            val spell = checkNotNull(deps.spells.getObjSpell(objType("obj.01_wind_strike")))

            assertTrue(deps.runes.attemptCast(player, spell).isSuccess())

            assertEquals(9, player.vars["varbit.rune_pouch_quantity_1"])
            assertEquals(9, player.vars["varbit.rune_pouch_quantity_2"])
        }
    }

    @Test
    fun GameTestState.`runes are consumed as before without listeners`() {
        runModifierTest(CombatModifierRegistry.EMPTY) { deps ->
            player.placeAt(TEST_COORDS)
            player.inv[0] = InvObj("obj.airrune", 10)
            player.inv[1] = InvObj("obj.mindrune", 10)
            val spell = checkNotNull(deps.spells.getObjSpell(objType("obj.01_wind_strike")))

            assertTrue(deps.runes.attemptCast(player, spell).isSuccess())

            assertEquals(9, player.inv.count("obj.airrune"))
            assertEquals(9, player.inv.count("obj.mindrune"))
        }
    }

    @Test
    fun GameTestState.`refunded weapon charges are not deducted`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            player.righthand = InvObj("obj.tumekens_shadow", vars = 100)
            val charges = "varobj.tumeken_charges"

            val consumed = deps.charges.attemptDetractWeapon(player, charges)
            assertEquals(ObjChargeManager.Uncharge.Success(chargesLeft = 99), consumed)
            assertEquals(99, deps.charges.getWeaponCharges(player, charges))

            hooks.listener.resourceDecision = { ResourceDecision.Refund }
            val refunded = deps.charges.attemptDetractWeapon(player, charges)
            assertEquals(ObjChargeManager.Uncharge.Success(chargesLeft = 99), refunded)
            assertEquals(99, deps.charges.getWeaponCharges(player, charges))

            val event = hooks.listener.resources.last()
            assertEquals(ResourceKind.Charge, event.context.kind)
            assertTrue(event.context.obj?.isType("obj.tumekens_shadow") == true)
        }
    }

    @Test
    fun GameTestState.`real ranged attacks regenerate ammo`() {
        val hooks = TestHooks()
        hooks.listener.resourceDecision = { ResourceDecision.Refund }
        runModifierTest(hooks.registry, CombatTestScripts.pvnCombat) {
            player.placeAt(TEST_COORDS)
            setLevel("stat.ranged", 99)
            player.righthand = InvObj("obj.shortbow")
            player.quiver = InvObj("obj.bronze_arrow", 10)
            val npc = spawnTarget()
            passCombatGracePeriod()

            player.opNpc2(npc)
            advanceUntil({ hooks.listener.rolled.isNotEmpty() }, timeoutTicks = 10)

            assertEquals(10, player.quiver?.count)
            val event = hooks.listener.resources.single()
            assertEquals(ResourceKind.Ammo, event.context.kind)
            assertEquals(CombatStyle.Ranged, hooks.listener.rolled.single().context.style)
        }
    }
}

private fun Player.fillRunePouchSlot(deps: ModifierTestDeps, slot: Int, rune: String, count: Int) {
    val compactId = checkNotNull(deps.compactRunes[objType(rune)]) { "No compact id: $rune" }
    VarPlayerIntMapSetter.set(this, "varbit.rune_pouch_type_$slot", compactId)
    VarPlayerIntMapSetter.set(this, "varbit.rune_pouch_quantity_$slot", count)
}
