package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isSuccess
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

class RuneRefundHookTest {
    @Test
    fun GameTestState.`a listener refunds one rune of a spell for the player who has the effect`() {
        val effect = PlayerEffect()
        effect.refund = { it.context.obj?.isType("obj.airrune") == true }
        runModifierTest(effect.registry()) { deps ->
            player.placeAt(TEST_COORDS)
            effect.owners += player
            val other = registerPlayer(TEST_COORDS.translateZ(2))
            val spell = checkNotNull(deps.spells.getObjSpell(objType("obj.01_wind_strike")))

            for (caster in listOf(player, other)) {
                caster.giveStrikeRunes()
                assertTrue(deps.runes.attemptCast(caster, spell).isSuccess())
            }

            assertEquals(10, player.inv.count("obj.airrune"))
            assertEquals(9, player.inv.count("obj.mindrune"))
            assertEquals(9, other.inv.count("obj.airrune"))
            assertEquals(9, other.inv.count("obj.mindrune"))
            val airRune =
                effect.resources.single {
                    it.context.player === player && it.context.obj?.isType("obj.airrune") == true
                }
            assertEquals(ResourceKind.Rune, airRune.context.kind)
            assertEquals(1, airRune.context.count)
        }
    }

    @Test
    fun GameTestState.`a provider's Regenerate chance refunds the runes it rolls for`() {
        val effect = PlayerEffect()
        effect.resource = ResourceModifiers(regenerateChancePercent = 25.0)
        runModifierTest(effect.registry()) { deps ->
            player.placeAt(TEST_COORDS)
            effect.owners += player
            player.giveStrikeRunes()
            val spell = checkNotNull(deps.spells.getObjSpell(objType("obj.01_wind_strike")))

            // 25% = 2500 basis points: 2499 regenerates the first rune, 2500 consumes the second.
            random.next = 2_499
            random.then = 2_500
            assertTrue(deps.runes.attemptCast(player, spell).isSuccess())

            val regenerated = effect.resources.filter { it.regenerated }
            assertEquals(1, regenerated.size)
            val total = player.inv.count("obj.airrune") + player.inv.count("obj.mindrune")
            assertEquals(19, total)
        }
    }

    private fun Player.giveStrikeRunes() {
        inv[0] = InvObj("obj.airrune", 10)
        inv[1] = InvObj("obj.mindrune", 10)
    }
}
