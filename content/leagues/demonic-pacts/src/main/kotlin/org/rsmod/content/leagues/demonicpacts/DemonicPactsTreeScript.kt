package org.rsmod.content.leagues.demonicpacts

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onIfScriptTrigger
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactCommitResult
import org.rsmod.content.leagues.demonicpacts.state.PactResetGuard
import org.rsmod.content.leagues.demonicpacts.state.PactResetResult
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Demonic Pacts tree's server side. Content opens the tree with [DemonicPactsTree.open];
 * `::pactopen` does it for administrators.
 *
 * The tree keeps the player's pending selection itself and sends only two things: the commit
 * ("Yes, Apply Changes") and the reset ("Reset Pacts"). Both go to [DemonicPacts], which validates
 * them; this script only decodes them and shows the outcome. The client asks nothing before a
 * reset, so the server asks for a yes or no first, as Jagex's league did, unless [DemonicPacts.reset]
 * would refuse it anyway (in the same order of checks).
 */
class DemonicPactsTreeScript
@Inject
constructor(
    private val tree: DemonicPactsTree,
    private val pacts: DemonicPacts,
    private val resetGuard: PactResetGuard,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("pactopen") {
            desc = "Open the Demonic Pacts tree"
            requiredRights = Rights.ADMINISTRATOR
            cheat { tree.open(player) }
        }
        onIfOpen(DemonicPactsTree.TREE) { pacts.refresh(player) }
        onIfOverlayButton(DemonicPactsTree.FRAME) {
            if (it.comsub == DemonicPactsTree.CLOSE_BUTTON) {
                tree.close(player)
            }
        }
        onIfScriptTrigger<PactCommitArgs>(DemonicPactsTree.COMMIT) { commit(it.nodes) }
        onIfScriptTrigger<PactResetArgs>(DemonicPactsTree.RESET) { reset() }
    }

    private fun ProtectedAccess.commit(nodes: IntArray) {
        if (!isTreeOpen()) {
            return
        }
        when (val result = pacts.commit(player, nodes.toList())) {
            is PactCommitResult.Committed -> {
                VarPlayerIntMapSetter.set(player, INITIAL_POINTS_SPENT, 1)
                mes(committedMessage(result.added.size, pacts.pointsAvailable(player)))
            }
            is PactCommitResult.Refused -> {
                mes(result.refusal.message)
                // Resending every var makes the client redraw and drop the refused selection.
                pacts.refreshOnLogin(player)
            }
        }
    }

    private suspend fun ProtectedAccess.reset() {
        if (!isTreeOpen()) {
            return
        }
        if (player.isModalButtonProtected) {
            mes(BUSY)
            return
        }
        val refusal = resetRefusal()
        if (refusal != null) {
            mes(refusal)
            return
        }
        val resets = pacts.resetsAvailable(player)
        val confirmed =
            choice2(CONFIRM_RESET, true, CANCEL_RESET, false, title = confirmTitle(resets))
        if (!confirmed) {
            return
        }
        when (val result = pacts.reset(player)) {
            is PactResetResult.Reset -> mes(resetMessage(pacts.resetsAvailable(player)))
            is PactResetResult.Refused -> mes(result.message)
        }
    }

    private fun ProtectedAccess.isTreeOpen(): Boolean =
        player.ui.containsOverlay(DemonicPactsTree.TREE)

    private fun ProtectedAccess.resetRefusal(): String? {
        val onlyRoot = pacts.owned(player).all { it.index == PactTree.ROOT_INDEX }
        if (onlyRoot) {
            return DemonicPacts.NOTHING_TO_RESET
        }
        return resetGuard.refusal(player)
            ?: if (pacts.resetsAvailable(player) <= 0) DemonicPacts.NO_RESETS else null
    }

    private fun committedMessage(added: Int, available: Int): String {
        val taken = if (added == 1) "a new pact" else "$added new pacts"
        return "You take on $taken. Pact points left: $available."
    }

    private fun confirmTitle(resets: Int): String =
        "Reset all your pacts? You have ${resetCount(resets)}."

    private fun resetMessage(resetsLeft: Int): String =
        "Your pacts have been reset and your points refunded. You have ${resetCount(resetsLeft)} " +
            "left."

    private fun resetCount(resets: Int): String = if (resets == 1) "1 reset" else "$resets resets"

    private companion object {
        const val INITIAL_POINTS_SPENT = "varbit.league_initial_points_spent"

        const val CONFIRM_RESET = "Yes, reset my pacts and use one reset."
        const val CANCEL_RESET = "No, keep my pacts."
        const val BUSY = "You're busy right now."
    }
}
