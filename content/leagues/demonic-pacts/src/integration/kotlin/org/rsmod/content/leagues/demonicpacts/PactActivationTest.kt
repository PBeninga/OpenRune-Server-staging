package org.rsmod.content.leagues.demonicpacts

import com.google.inject.AbstractModule
import com.google.inject.Module
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.baseVar
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.server.config.DemonicPactsYaml
import org.rsmod.api.server.config.GameplayConfig
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.DemonicPactsStateScript
import org.rsmod.content.leagues.demonicpacts.state.PactCommitRefusal
import org.rsmod.content.leagues.demonicpacts.state.PactCommitResult
import org.rsmod.content.leagues.demonicpacts.state.PactEffectVarbits
import org.rsmod.content.leagues.demonicpacts.state.PactEffects
import org.rsmod.content.leagues.demonicpacts.state.PactResetResult
import org.rsmod.content.leagues.demonicpacts.state.PactSettings
import org.rsmod.content.leagues.demonicpacts.state.PactState
import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.game.entity.Player

class PactActivationTest {
    @Test
    fun GameTestState.`pacts are off unless the config enables them`() = pactTest { deps ->
        assertEquals(PactSettings(), deps.settings)
        assertFalse(deps.settings.enabled)
        assertFalse(deps.pacts.isActive(player))
    }

    @Test
    fun GameTestState.`an inactive player is sent no pact vars on login`() = pactTest { deps ->
        assertEquals(0, player.vars[PactState.POINTS_EARNED])
        assertEquals(0, player.vars[PactState.RESETS_AVAILABLE])
        assertEquals(0, player.vars[PactState.LEAGUE_TYPE])
        assertEquals(emptyList<Int>(), sentVarps(player).filter { it in pactVarps() })
        assertEquals(0, deps.pacts.pointsEarned(player))
        assertEquals(0, deps.pacts.resetsAvailable(player))
    }

    @Test
    fun GameTestState.`an inactive player can't commit, reset or be granted anything`() =
        pactTest { deps ->
            val pacts = deps.pacts
            val root = deps.tree.root
            pacts.grantPoints(player, 10)
            pacts.grantReset(player, 3)
            pacts.setOwnedUnchecked(player, deps.tree.nodes)
            pacts.setResetsAvailable(player, 5)
            pacts.refresh(player)

            val commit = pacts.commit(player, listOf(root.index))
            assertEquals(PactCommitResult.Refused(PactCommitRefusal.NotActive), commit)
            assertEquals(PactResetResult.Refused(DemonicPacts.NOT_ACTIVE), pacts.reset(player))
            assertTrue(pacts.owned(player).isEmpty())
            assertEquals(0, player.vars[PactState.POINTS_GRANTED])
            assertEquals(0, player.vars[PactState.RESETS_GRANTED])
            assertEquals(0, player.vars[PactState.POINTS_EARNED])
            assertEquals(emptyList<Int>(), sentVarps(player).filter { it in pactVarps() })
        }

    @Test
    fun GameTestState.`an inactive player's saved pacts have no effect`() = pactTest { deps ->
        player.setVarp("varp.combat_mastery_perm_0", -1)
        assertTrue(deps.pacts.owned(player).isNotEmpty())
        assertEquals(PactEffects.NONE, deps.pacts.effects(player))
    }

    @Test
    fun GameTestState.`the tree and the admin commands refuse an inactive player`() =
        pactTest { deps ->
            player.modLevel = Rights.ADMINISTRATOR
            player.cheat("pactopen")
            assertOverlayNotOpen(DemonicPactsTree.TREE)
            assertMessageSent(DemonicPacts.NOT_ACTIVE)
            assertEquals(0, player.vars[PactState.LEAGUE_TYPE])
            assertFalse(deps.treeUi.open(player))

            player.cheat("pactset", "all")
            player.cheat("pactpoints", "40")
            assertTrue(deps.pacts.owned(player).isEmpty())
            assertEquals(0, deps.pacts.pointsEarned(player))
        }

    @Test
    fun GameTestState.`of two players only the active one gets effects, the tree and points`() {
        val activation = PactsActiveFor()
        pactTest(activation) { deps ->
            val pacts = deps.pacts
            val inactive = player
            val active = Player()
            activation.players += active
            registerPlayer(player = active)

            assertTrue(pacts.isActive(active))
            assertFalse(pacts.isActive(inactive))
            assertEquals(2, active.vars[PactState.POINTS_EARNED])
            assertEquals(2, active.vars[PactState.RESETS_AVAILABLE])
            assertEquals(PactState.LEAGUES_VI, active.vars[PactState.LEAGUE_TYPE])
            assertEquals(0, inactive.vars[PactState.POINTS_EARNED])
            assertEquals(0, inactive.vars[PactState.LEAGUE_TYPE])

            val nodes = listOf(deps.tree["AA"], deps.tree["BA"])
            val indices = nodes.map(PactNode::index)
            assertInstanceOf(PactCommitResult.Committed::class.java, pacts.commit(active, indices))
            val refused = pacts.commit(inactive, indices)
            assertEquals(PactCommitResult.Refused(PactCommitRefusal.NotActive), refused)
            val effect = deps.tree["BA"].effect.id
            assertEquals(PactEffects.of(nodes), pacts.effects(active))
            assertTrue(pacts.effects(active)[effect] > 0)
            assertEquals(PactEffects.NONE, pacts.effects(inactive))
            val varbit = PactEffectVarbits.VARBITS.getValue(effect)
            assertEquals(pacts.effects(active)[effect], active.vars[varbit])
            assertEquals(0, inactive.vars[varbit])

            assertTrue(deps.treeUi.open(active))
            assertOverlayOpen(DemonicPactsTree.TREE, active)
            assertFalse(deps.treeUi.open(inactive))
            assertOverlayNotOpen(DemonicPactsTree.TREE, inactive)
            assertEquals(emptyList<Int>(), sentVarps(inactive).filter { it in pactVarps() })
        }
    }

    @Test
    fun GameTestState.`a player whose pacts turned off has the shown vars cleared on login`() {
        val activation = PactsActiveWhile()
        pactTest(activation) { deps ->
            val pacts = deps.pacts
            val nodes = buyRegenerateNodes(deps)
            assertShown(player, earned = 7, spent = 1, resets = 3, leagueType = PactState.LEAGUES_VI)

            activation.active = false
            val inactive = relog(player)
            assertShown(inactive, earned = 0, spent = 0, resets = 0, leagueType = 0)
            assertEquals(0, inactive.vars[regenerateVarbit(deps)])
            assertEquals(nodes, pacts.owned(inactive))

            activation.active = true
            val back = relog(inactive)
            assertShown(back, earned = 7, spent = 1, resets = 3, leagueType = PactState.LEAGUES_VI)
            assertEquals(nodes, pacts.owned(back))
            assertEquals(PactEffects.of(nodes), pacts.effects(back))
            assertEquals(55, back.vars[regenerateVarbit(deps)])
        }
    }

    @Test
    fun GameTestState.`a refresh after pacts turn off clears what the client shows`() {
        val activation = PactsActiveWhile()
        pactTest(activation) { deps ->
            val pacts = deps.pacts
            val nodes = buyRegenerateNodes(deps)
            assertEquals(55, player.vars[regenerateVarbit(deps)])

            activation.active = false
            pacts.refresh(player)
            assertShown(player, earned = 0, spent = 0, resets = 0, leagueType = 0)
            assertEquals(0, player.vars[regenerateVarbit(deps)])
            assertEquals(nodes, pacts.owned(player))
            assertEquals(5, player.vars[PactState.POINTS_GRANTED])

            activation.active = true
            pacts.refresh(player)
            assertShown(player, earned = 7, spent = 1, resets = 3, leagueType = PactState.LEAGUES_VI)
            assertEquals(55, player.vars[regenerateVarbit(deps)])
        }
    }

    @Test
    fun GameTestState.`enabled in the config, pacts have the Jagex defaults`() =
        pactTest(config(DemonicPactsYaml(enabled = true))) { deps ->
            val pacts = deps.pacts
            assertTrue(pacts.isActive(player))
            assertEquals(PactSettings(enabled = true), deps.settings)
            assertEquals(2, pacts.pointsEarned(player))
            assertEquals(2, pacts.resetsAvailable(player))
            assertEquals(40, pacts.spendCap(player))
            assertEquals(2, player.vars[PactState.POINTS_EARNED])
            assertEquals(PactState.LEAGUES_VI, player.vars[PactState.LEAGUE_TYPE])
        }

    @Test
    fun GameTestState.`the config overrides the starting points, free resets and spend cap`() {
        val yaml = DemonicPactsYaml(enabled = true, startingPoints = 7, freeResets = 3, spendCap = 5)
        pactTest(config(yaml)) { deps ->
            val pacts = deps.pacts
            assertEquals(7, pacts.pointsEarned(player))
            assertEquals(5, player.vars[PactState.POINTS_EARNED])
            assertEquals(3, pacts.resetsAvailable(player))
            assertEquals(5, pacts.spendCap(player))
        }
    }

    /** Grants 5 points and a reset, then buys AA and BA (55% Regenerate). */
    private fun GameTestScope.buyRegenerateNodes(deps: Deps): List<PactNode> {
        val nodes = listOf(deps.tree["AA"], deps.tree["BA"])
        deps.pacts.grantPoints(player, 5)
        deps.pacts.grantReset(player)
        val result = deps.pacts.commit(player, nodes.map(PactNode::index))
        assertInstanceOf(PactCommitResult.Committed::class.java, result)
        return nodes
    }

    private fun regenerateVarbit(deps: Deps): String =
        PactEffectVarbits.VARBITS.getValue(deps.tree["AA"].effect.id)

    private fun assertShown(player: Player, earned: Int, spent: Int, resets: Int, leagueType: Int) {
        assertEquals(earned, player.vars[PactState.POINTS_EARNED], "points earned")
        assertEquals(spent, player.vars[PactState.POINTS_SPENT], "points spent")
        assertEquals(resets, player.vars[PactState.RESETS_AVAILABLE], "resets")
        assertEquals(leagueType, player.vars[PactState.LEAGUE_TYPE], "league_type")
    }

    private fun GameTestState.pactTest(
        module: Module? = null,
        body: GameTestScope.(Deps) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            module,
            DemonicPactsStateScript::class,
            DemonicPactsTreeScript::class,
            testBody = body,
        )

    private fun config(yaml: DemonicPactsYaml): Module =
        object : AbstractModule() {
            override fun configure() {
                val config =
                    ServerConfig(
                        name = "test-suite",
                        gamePort = 43594,
                        revision = 241,
                        environment = "TEST",
                        world = 255,
                        gameplay = GameplayConfig(demonicPacts = yaml),
                    )
                bind(ServerConfig::class.java).toInstance(config)
            }
        }

    private fun sentVarps(player: Player): List<Int> {
        val client = player.client as CaptureClient
        return client.filterIsInstance<VarpSmall>().map { it.id } +
            client.filterIsInstance<VarpLarge>().map { it.id }
    }

    /** Every varp the pact state, the effect panel and `league_type` live in. */
    private fun pactVarps(): Set<Int> {
        val varps =
            listOf(PactState.POINTS_EARNED, PactState.POINTS_SPENT) +
                (0..4).map { "${PactState.OWNED_VARP_PREFIX}$it" }
        val varbits =
            listOf(PactState.RESETS_AVAILABLE, PactState.LEAGUE_TYPE) +
                PactEffectVarbits.VARBITS.values
        val baseVarps =
            varbits.map { varbit ->
                val type = ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))
                checkNotNull(type) { "Missing $varbit" }.baseVar.id
            }
        return (varps.map { it.asRSCM(RSCMType.VARP) } + baseVarps).toSet()
    }

    class Deps
    @Inject
    constructor(
        val pacts: DemonicPacts,
        val tree: PactTree,
        val treeUi: DemonicPactsTree,
        val settings: PactSettings,
    )
}
