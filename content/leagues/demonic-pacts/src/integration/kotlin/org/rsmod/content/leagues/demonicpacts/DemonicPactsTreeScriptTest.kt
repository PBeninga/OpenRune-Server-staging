package org.rsmod.content.leagues.demonicpacts

import com.google.inject.AbstractModule
import com.google.inject.Module
import com.google.inject.util.Modules
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.DemonicPactsStateScript
import org.rsmod.content.leagues.demonicpacts.state.PactCommitRefusal
import org.rsmod.content.leagues.demonicpacts.state.PactResetGuard
import org.rsmod.content.leagues.demonicpacts.state.PactState
import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.game.entity.Player

class DemonicPactsTreeScriptTest {
    @Test
    fun GameTestState.`pactopen opens the tree in the floater and runs its init`() =
        runActivePactTest(DemonicPactsTreeScript::class) {
            player.modLevel = Rights.ADMINISTRATOR
            player.cheat("pactopen")

            assertOverlayOpen(TREE)
            assertModalNotOpen(TREE)
            assertEquals(6, player.vars["varbit.league_type"])
            val init =
                player.capture.filterIsInstance<RunClientScript>().single {
                    it.id == DemonicPactsTree.TALENT_TREE_INIT
                }
            assertEquals(listOf<Any>(1), init.values)
            val nodes = "component.talent_tree:tree_node_draw_layer".asRSCM(RSCMType.COMPONENT)
            val events = player.capture.filterIsInstance<IfSetEventsV2>()
            assertTrue(events.any { it.combinedId == nodes && it.start == 0 && it.end == 131 })
            val trigger = IfEvent.ScriptTrigger.bitmask
            for (component in listOf(COMMIT, RESET)) {
                val packed = component.asRSCM(RSCMType.COMPONENT)
                val componentEvents = events.filter { it.combinedId == packed }
                assertTrue(componentEvents.any { (it.events1.toLong() and trigger) != 0L })
            }
        }

    @Test
    fun GameTestState.`pactopen is for administrators`() =
        runGameTest(DemonicPactsTreeScript::class) {
            player.cheat("pactopen")
            advance()
            assertOverlayNotOpen(TREE)
        }

    @Test
    fun GameTestState.`the close button closes the tree`() = treeTest { deps ->
        deps.tree.open(player)
        player.ifButton("component.talent_tree:frame", comsub = 11)
        advance()
        assertOverlayNotOpen(TREE)
    }

    @Test
    fun GameTestState.`opening the tree refreshes the points it shows`() = treeTest { deps ->
        VarPlayerIntMapSetter.set(player, PactState.POINTS_EARNED, 0)
        VarPlayerIntMapSetter.set(player, PactState.RESETS_AVAILABLE, 0)
        deps.tree.open(player)
        assertEquals(2, player.vars[PactState.POINTS_EARNED])
        assertEquals(2, player.vars[PactState.RESETS_AVAILABLE])
    }

    @Test
    fun GameTestState.`a commit buys the selected nodes for any player`() = treeTest { deps ->
        deps.tree.open(player)
        player.ifScriptTrigger(COMMIT, deps.indices("AA", "BA", "BB"))
        advance()

        assertEquals(setOf("AA", "BA", "BB"), deps.pacts.owned(player).ids().toSet())
        assertEquals(2, player.vars[PactState.POINTS_SPENT])
        assertEquals(1, player.vars[INITIAL_POINTS_SPENT])
        assertMessageSent("You take on 3 new pacts. Pact points left: 0.")
    }

    @Test
    fun GameTestState.`a commit across the owned varps decodes every index`() = treeTest { deps ->
        val path = deps.linkedPath(40)
        deps.pacts.grantPoints(player, 38)
        deps.tree.open(player)
        player.ifScriptTrigger(COMMIT, path.map(PactNode::index).toIntArray())
        advance()

        assertEquals(path.sortedBy(PactNode::index), deps.pacts.owned(player))
        assertTrue(path.any { it.varpIndex > 0 })
        assertMessageSent("You take on 40 new pacts. Pact points left: 1.")
    }

    @Test
    fun GameTestState.`an illegal commit is refused with a message`() = treeTest { deps ->
        deps.pacts.grantPoints(player, 10)
        deps.tree.open(player)
        player.ifScriptTrigger(COMMIT, deps.indices("AA", "N9"))
        advance()

        assertMessageSent(PactCommitRefusal.NotLinked.message)
        assertTrue(deps.pacts.owned(player).isEmpty())
        assertEquals(0, player.vars[INITIAL_POINTS_SPENT])

        player.ifScriptTrigger(COMMIT, intArrayOf(0, 132))
        advance()
        assertMessageSent(PactCommitRefusal.InvalidSelection.message)
        assertTrue(deps.pacts.owned(player).isEmpty())
    }

    @Test
    fun GameTestState.`a commit over the points earned is refused`() = treeTest { deps ->
        deps.tree.open(player)
        player.ifScriptTrigger(COMMIT, deps.indices("AA", "BA", "BB", "BC"))
        advance()

        assertMessageSent(PactCommitRefusal.NotEnoughPoints.message)
        assertTrue(deps.pacts.owned(player).isEmpty())
    }

    @Test
    fun GameTestState.`a commit or reset with the tree closed is ignored`() = treeTest { deps ->
        player.ifScriptTrigger(COMMIT, deps.indices("AA"))
        advance()
        assertTrue(deps.pacts.owned(player).isEmpty())

        deps.pacts.commit(player, deps.indices("AA", "BA").toList())
        player.ifScriptTrigger(RESET)
        advance()
        assertNoDialogue()
        assertEquals(setOf("AA", "BA"), deps.pacts.owned(player).ids().toSet())
    }

    @Test
    fun GameTestState.`the reset button asks first, then uses one reset`() = treeTest { deps ->
        deps.pacts.commit(player, deps.indices("AA", "BA", "BB").toList())
        deps.tree.open(player)
        player.ifScriptTrigger(RESET)
        advance()

        assertDialogue("Reset all your pacts? You have 2 resets.")
        assertDialogueOptions("Yes, reset my pacts and use one reset.", "No, keep my pacts.")
        assertEquals(setOf("AA", "BA", "BB"), deps.pacts.owned(player).ids().toSet())
        assertOverlayOpen(TREE)

        player.chooseOption(1)
        advance()

        assertEquals(listOf("AA"), deps.pacts.owned(player).ids())
        assertEquals(0, player.vars[PactState.POINTS_SPENT])
        assertEquals(1, player.vars[PactState.RESETS_AVAILABLE])
        assertMessageSent(
            "Your pacts have been reset and your points refunded. You have 1 reset left."
        )
        assertOverlayOpen(TREE)
    }

    @Test
    fun GameTestState.`declining the reset keeps the pacts and the reset`() = treeTest { deps ->
        deps.pacts.commit(player, deps.indices("AA", "BA").toList())
        deps.tree.open(player)
        player.ifScriptTrigger(RESET)
        advance()
        player.chooseOption("No, keep my pacts.")
        advance()

        assertNoDialogue()
        assertEquals(setOf("AA", "BA"), deps.pacts.owned(player).ids().toSet())
        assertEquals(2, player.vars[PactState.RESETS_AVAILABLE])
    }

    @Test
    fun GameTestState.`a second reset click while asking is refused`() = treeTest { deps ->
        deps.pacts.commit(player, deps.indices("AA", "BA").toList())
        deps.tree.open(player)
        player.ifScriptTrigger(RESET)
        advance()
        player.ifScriptTrigger(RESET)
        advance()

        assertMessageSent("You're busy right now.")
        assertDialogueOptions("Yes, reset my pacts and use one reset.", "No, keep my pacts.")
        player.chooseOption(1)
        advance()
        assertEquals(listOf("AA"), deps.pacts.owned(player).ids())
        assertEquals(1, player.vars[PactState.RESETS_AVAILABLE])
    }

    @Test
    fun GameTestState.`a reset with none left is refused without asking`() = treeTest { deps ->
        deps.pacts.commit(player, deps.indices("AA", "BA").toList())
        deps.pacts.setResetsAvailable(player, 0)
        deps.tree.open(player)
        player.ifScriptTrigger(RESET)
        advance()

        assertMessageSent(DemonicPacts.NO_RESETS)
        assertNoDialogue()
        assertEquals(setOf("AA", "BA"), deps.pacts.owned(player).ids().toSet())
    }

    @Test
    fun GameTestState.`a reset with only the root is refused without asking`() = treeTest { deps ->
        deps.pacts.commit(player, deps.indices("AA").toList())
        deps.tree.open(player)
        player.ifScriptTrigger(RESET)
        advance()

        assertMessageSent(DemonicPacts.NOTHING_TO_RESET)
        assertNoDialogue()
        assertEquals(2, player.vars[PactState.RESETS_AVAILABLE])
    }

    @Test
    fun GameTestState.`a reset the guard refuses is refused without asking`() =
        treeTest(Modules.combine(PactsActiveForEveryone, RefuseResets)) { deps ->
            deps.pacts.commit(player, deps.indices("AA", "BA").toList())
            deps.tree.open(player)
            player.ifScriptTrigger(RESET)
            advance()

            assertMessageSent(GUARD_REFUSAL)
            assertNoDialogue()
            assertEquals(setOf("AA", "BA"), deps.pacts.owned(player).ids().toSet())
            assertEquals(2, player.vars[PactState.RESETS_AVAILABLE])
        }

    private fun GameTestState.treeTest(
        module: Module = PactsActiveForEveryone,
        body: GameTestScope.(TreeDeps) -> Unit,
    ) =
        runInjectedGameTest(
            TreeDeps::class,
            module,
            DemonicPactsTreeScript::class,
            DemonicPactsStateScript::class,
            testBody = body,
        )

    private fun List<PactNode>.ids(): List<String> = map(PactNode::id)

    private val Player.capture: CaptureClient
        get() = client as CaptureClient

    class TreeDeps
    @Inject
    constructor(val tree: DemonicPactsTree, val pacts: DemonicPacts, val pactTree: PactTree) {
        fun indices(vararg ids: String): IntArray = ids.map { pactTree[it].index }.toIntArray()

        /** [count] nodes from the root outwards, each linked to an earlier one. */
        fun linkedPath(count: Int): List<PactNode> {
            val taken = linkedSetOf(pactTree.root)
            while (taken.size < count) {
                val next =
                    taken
                        .flatMap(pactTree::neighbours)
                        .filter { it !in taken }
                        .maxByOrNull(PactNode::index) ?: break
                taken += next
            }
            return taken.toList()
        }
    }

    private companion object {
        const val TREE = DemonicPactsTree.TREE
        const val COMMIT = DemonicPactsTree.COMMIT
        const val RESET = DemonicPactsTree.RESET
        const val INITIAL_POINTS_SPENT = "varbit.league_initial_points_spent"
        const val GUARD_REFUSAL = "Not now."

        val RefuseResets: Module =
            object : AbstractModule() {
                override fun configure() {
                    val guard =
                        object : PactResetGuard {
                            override fun refusal(player: Player): String = GUARD_REFUSAL
                        }
                    bind(PactResetGuard::class.java).toInstance(guard)
                }
            }
    }
}
