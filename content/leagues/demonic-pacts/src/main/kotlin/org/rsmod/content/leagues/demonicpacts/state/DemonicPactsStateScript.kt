package org.rsmod.content.leagues.demonicpacts.state

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Rebuilds a player's shown pact vars and effects on login (or clears them, for a player whose
 * pacts are now inactive), and the admin commands, which only work for an administrator whose own
 * pacts are active ([PactActivation]):
 * - `::pactset <id ...>` owns exactly the given nodes (wiki ids such as `CA`, or indices), ignoring
 *   links, points and the cap; `all` owns every node and `none` clears them;
 * - `::pactpoints [n]` sets the points granted through [DemonicPacts.grantPoints] and shows the
 *   pact state;
 * - `::pactresets <n>` makes `n` resets available, at most [DemonicPacts.MAX_RESETS].
 */
class DemonicPactsStateScript
@Inject
constructor(private val pacts: DemonicPacts, private val tree: PactTree) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { pacts.refreshOnLogin(player) }
        onCommand("pactset") {
            desc = "Own exactly these pacts, e.g. ::pactset AA CA CB (or all, none)"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = PACTSET_USAGE
            cheat(active(::pactSet))
        }
        onCommand("pactpoints") {
            desc = "Set the granted pact points, e.g. ::pactpoints 40 (no number: show state)"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = PACTPOINTS_USAGE
            cheat(active(::pactPoints))
        }
        onCommand("pactresets") {
            desc = "Set the pact resets available, e.g. ::pactresets 3"
            requiredRights = Rights.ADMINISTRATOR
            invalidArgs = PACTRESETS_USAGE
            cheat(active(::pactResets))
        }
    }

    private fun active(command: (Cheat) -> Unit): Cheat.() -> Unit = {
        if (pacts.isActive(player)) {
            command(this)
        } else {
            player.mes(DemonicPacts.NOT_ACTIVE)
        }
    }

    private fun pactSet(cheat: Cheat) {
        val player = cheat.player
        if (cheat.args.isEmpty()) {
            player.mes(PACTSET_USAGE)
            return
        }
        val nodes = parseNodes(cheat.args)
        if (nodes == null) {
            player.mes(PACTSET_USAGE)
            return
        }
        pacts.setOwnedUnchecked(player, nodes)
        player.mes(ownedMessage(nodes))
        player.mes(status(player))
    }

    private fun parseNodes(args: List<String>): List<PactNode>? {
        val single = args.singleOrNull()?.lowercase()
        if (single == ALL) {
            return tree.nodes
        }
        if (single == NONE) {
            return emptyList()
        }
        val nodes = args.map { arg -> nodeOrNull(arg) ?: return null }
        return nodes.distinct().sortedBy(PactNode::index)
    }

    private fun nodeOrNull(arg: String): PactNode? {
        val index = arg.toIntOrNull()
        if (index != null) {
            return tree.nodes.getOrNull(index)
        }
        return tree.getOrNull(arg.uppercase())
    }

    private fun pactPoints(cheat: Cheat) {
        val player = cheat.player
        if (cheat.args.isEmpty()) {
            player.mes(status(player))
            return
        }
        val points = cheat.args.singleOrNull()?.toIntOrNull()
        if (points == null || points < 0) {
            player.mes(PACTPOINTS_USAGE)
            return
        }
        pacts.setPointsGranted(player, points)
        player.mes(status(player))
    }

    private fun pactResets(cheat: Cheat) {
        val player = cheat.player
        val resets = cheat.args.singleOrNull()?.toIntOrNull()
        if (resets == null || resets < 0) {
            player.mes(PACTRESETS_USAGE)
            return
        }
        pacts.setResetsAvailable(player, resets)
        if (resets > DemonicPacts.MAX_RESETS) {
            player.mes("At most ${DemonicPacts.MAX_RESETS} pact resets can be available.")
        }
        player.mes(status(player))
    }

    private fun ownedMessage(nodes: List<PactNode>): String =
        if (nodes.isEmpty()) {
            "You now own no pacts."
        } else {
            "You now own ${nodes.size} pacts: ${nodes.joinToString(limit = 12) { it.id }}."
        }

    private fun status(player: Player): String =
        "Pact points: ${pacts.pointsEarned(player)} earned, ${pacts.pointsSpent(player)} spent, " +
            "cap ${pacts.spendCap(player)}. Resets: ${pacts.resetsAvailable(player)}."

    private companion object {
        const val ALL = "all"
        const val NONE = "none"
        const val PACTSET_USAGE = "Usage: ::pactset <id ...> (wiki ids or indices), all or none"
        const val PACTPOINTS_USAGE = "Usage: ::pactpoints [points]"
        const val PACTRESETS_USAGE = "Usage: ::pactresets <resets>"
    }
}
