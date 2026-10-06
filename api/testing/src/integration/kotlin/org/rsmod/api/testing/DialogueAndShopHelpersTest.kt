package org.rsmod.api.testing

import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.shops.ShopScript
import org.rsmod.api.shops.Shops
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Self-tests of the dialogue and shop helpers of [GameTestScope], on a test npc's dialogue. */
class DialogueAndShopHelpersTest {
    @Test
    fun GameTestState.`walk a dialogue through lines, a choice and a message box`() =
        runGameTest(TestDialogueNpcScript::class) {
            talkToMan()
            assertDialogue(TestDialogueNpcScript.GREETING, speaker = "Man")

            player.resumePauseButton()
            advance()
            assertDialogueOptions(TestDialogueNpcScript.BUY, TestDialogueNpcScript.BYE)

            player.chooseOption(TestDialogueNpcScript.BYE)
            advance()
            assertDialogue("Goodbye.")

            player.resumePauseButton()
            advance()
            assertDialogue(TestDialogueNpcScript.WAVE)

            player.resumePauseButton()
            advance()
            assertNoDialogue()
        }

    @Test
    fun GameTestState.`wrong dialogue input fails the test`() =
        runGameTest(TestDialogueNpcScript::class) {
            assertNoDialogue()
            player.assertThrows<AssertionFailedError> { player.resumePauseButton() }

            talkToMan()
            player.assertThrows<AssertionFailedError> { player.chooseOption(1) }
            player.assertThrows<AssertionFailedError> { assertDialogue("Something else.") }

            player.resumePauseButton()
            advance()
            player.assertThrows<AssertionFailedError> { player.resumePauseButton() }
            player.assertThrows<AssertionFailedError> { player.chooseOption(3) }
        }

    @Test
    fun GameTestState.`buy from and sell to a shop opened by dialogue`() =
        runGameTest(TestDialogueNpcScript::class, ShopScript::class) {
            player.inv[0] = InvObj("obj.coins", 1000)
            talkToMan()
            player.resumePauseButton()
            advance()
            player.chooseOption(1)
            advance()
            assertDialogue("Sell me something.")
            player.resumePauseButton()
            advance()
            assertModalOpen("interface.shopmain")

            player.shopBuy(TestDialogueNpcScript.STOCK, count = 5)
            advance()
            assertEquals(5, player.count(TestDialogueNpcScript.STOCK))
            val coinsAfterBuying = player.count("obj.coins")
            assertTrue(coinsAfterBuying < 1000)

            player.shopSell(TestDialogueNpcScript.STOCK)
            advance()
            assertEquals(4, player.count(TestDialogueNpcScript.STOCK))
            assertTrue(player.count("obj.coins") > coinsAfterBuying)

            player.assertThrows<AssertionFailedError> { player.shopBuy("obj.abyssal_whip") }
            player.assertThrows<AssertionFailedError> { player.shopSell("obj.abyssal_whip") }
            player.assertThrows<AssertionFailedError> {
                player.shopBuy(TestDialogueNpcScript.STOCK, count = 3)
            }
        }

    private fun GameTestScope.talkToMan(): Npc {
        val start = CoordGrid(0, 50, 50, 22, 18)
        player.placeAt(start)
        allocZoneCollision(start)
        val man = spawnNpc(start.translateX(1), "npc.man")
        player.opNpc1(man)
        advanceUntil({ player.dialogue() != null }, timeoutTicks = 5)
        return man
    }
}

/** A man who greets, offers a choice, and either opens a shop or says goodbye. */
class TestDialogueNpcScript @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.man") { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        startDialogue(npc) { greet() }
    }

    private suspend fun Dialogue.greet() {
        chatNpc(happy, GREETING)
        when (choice2(BUY, 1, BYE, 2)) {
            1 -> buy()
            2 -> leave()
        }
    }

    private suspend fun Dialogue.buy() {
        chatPlayer(quiz, "Sell me something.")
        shops.open(player, "Test Shop", SHOP_INV, 130.0, 40.0, 3.0)
    }

    private suspend fun Dialogue.leave() {
        chatPlayer(neutral, "Goodbye.")
        mesbox(WAVE)
    }

    companion object {
        const val GREETING: String =
            "Hello there, adventurer. This line is long enough to need a second line in the " +
                "chatbox."
        const val BUY: String = "Buy something"
        const val BYE: String = "Goodbye"
        const val WAVE: String = "The man waves."
        const val SHOP_INV: String = "inv.axeshop"
        const val STOCK: String = "obj.bronze_axe"
    }
}
