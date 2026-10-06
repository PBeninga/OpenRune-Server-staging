package org.rsmod.content.leagues.demonicpacts.state

import com.google.inject.AbstractModule
import com.google.inject.Module
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Modules
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.PactsActiveForEveryone
import org.rsmod.content.leagues.demonicpacts.relog
import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.game.entity.Player

class DemonicPactsStateTest {
    @Test
    fun GameTestState.`a new player gets the Jagex defaults`() = pactTest { deps ->
        val pacts = deps.pacts
        assertEquals(2, pacts.pointsEarned(player))
        assertEquals(0, pacts.pointsSpent(player))
        assertEquals(40, pacts.spendCap(player))
        assertEquals(2, pacts.resetsAvailable(player))
        assertEquals(2, player.vars[PactState.POINTS_EARNED])
        assertEquals(0, player.vars[PactState.POINTS_SPENT])
        assertEquals(2, player.vars[PactState.RESETS_AVAILABLE])
        assertTrue(pacts.owned(player).isEmpty())
    }

    @Test
    fun GameTestState.`a commit buys linked nodes and the root is free`() = pactTest { deps ->
        val pacts = deps.pacts
        val result = pacts.commit(player, indices(deps, "AA", "BA", "BB"))
        assertInstanceOf(PactCommitResult.Committed::class.java, result)
        assertEquals(nodes(deps, "AA", "BA", "BB"), pacts.owned(player))
        assertEquals(2, player.vars[PactState.POINTS_SPENT])
        assertEquals(ownedBits(deps, "AA", "BA", "BB"), ownedVarps())

        val refused = pacts.commit(player, indices(deps, "BC"))
        assertEquals(PactCommitResult.Refused(PactCommitRefusal.NotEnoughPoints), refused)
        assertEquals(nodes(deps, "AA", "BA", "BB"), pacts.owned(player))
    }

    @Test
    fun GameTestState.`a commit not linked to owned nodes is refused`() = pactTest { deps ->
        val pacts = deps.pacts
        pacts.grantPoints(player, 10)
        pacts.commit(player, indices(deps, "AA"))
        val refused = pacts.commit(player, indices(deps, "N9"))
        assertEquals(PactCommitResult.Refused(PactCommitRefusal.NotLinked), refused)
        assertEquals(listOf("AA"), pacts.owned(player).ids())
        assertEquals(0, player.vars[PactState.POINTS_SPENT])
    }

    @Test
    fun GameTestState.`forged commits are refused and change nothing`() = pactTest { deps ->
        val pacts = deps.pacts
        pacts.commit(player, indices(deps, "AA"))
        val before = ownedVarps()
        for (forged in listOf(listOf(132), listOf(-1), listOf(1, 1), listOf(0), emptyList())) {
            val result = pacts.commit(player, forged)
            assertInstanceOf(PactCommitResult.Refused::class.java, result) { "$forged" }
        }
        assertEquals(before, ownedVarps())
    }

    @Test
    fun GameTestState.`points past the spend cap can't be spent`() = pactTest { deps ->
        val pacts = deps.pacts
        pacts.grantPoints(player, 100)
        assertEquals(102, pacts.pointsEarned(player))
        assertEquals(40, player.vars[PactState.POINTS_EARNED])

        pacts.commit(player, indices(deps, "AA"))
        val path = linkedPath(deps.tree, 41)
        val tooMany = pacts.commit(player, path.map(PactNode::index))
        assertEquals(PactCommitResult.Refused(PactCommitRefusal.OverSpendCap), tooMany)

        val forty = path.dropLast(1)
        val committed = pacts.commit(player, forty.map(PactNode::index))
        val added = forty.sortedBy(PactNode::index)
        assertEquals(PactCommitResult.Committed(added, pointsSpent = 40), committed)
        assertEquals(40, player.vars[PactState.POINTS_SPENT])
        assertEquals(0, pacts.pointsAvailable(player))
        val more = pacts.commit(player, listOf(path.last().index))
        assertEquals(PactCommitResult.Refused(PactCommitRefusal.OverSpendCap), more)
    }

    @Test
    fun GameTestState.`a reset keeps the root and uses one reset`() = pactTest { deps ->
        val pacts = deps.pacts
        pacts.commit(player, indices(deps, "AA", "BA", "BB"))
        val result = pacts.reset(player)
        assertEquals(PactResetResult.Reset(nodes(deps, "BA", "BB")), result)
        assertEquals(listOf("AA"), pacts.owned(player).ids())
        assertEquals(0, player.vars[PactState.POINTS_SPENT])
        assertEquals(1, player.vars[PactState.RESETS_AVAILABLE])
        assertEquals(PactEffects.of(nodes(deps, "AA")), pacts.effects(player))

        pacts.commit(player, indices(deps, "BC", "B3"))
        pacts.reset(player)
        assertEquals(0, pacts.resetsAvailable(player))

        pacts.commit(player, indices(deps, "BA"))
        val refused = pacts.reset(player)
        assertEquals(PactResetResult.Refused(DemonicPacts.NO_RESETS), refused)
        assertEquals(listOf("AA", "BA"), pacts.owned(player).ids())
    }

    @Test
    fun GameTestState.`a reset with only the root is refused without using a reset`() =
        pactTest { deps ->
            val pacts = deps.pacts
            pacts.commit(player, indices(deps, "AA"))
            assertEquals(PactResetResult.Refused(DemonicPacts.NOTHING_TO_RESET), pacts.reset(player))
            assertEquals(2, pacts.resetsAvailable(player))
        }

    @Test
    fun GameTestState.`granted resets add to the free ones`() = pactTest { deps ->
        val pacts = deps.pacts
        pacts.grantReset(player)
        pacts.grantReset(player, 2)
        assertEquals(5, pacts.resetsAvailable(player))
        assertEquals(5, player.vars[PactState.RESETS_AVAILABLE])
    }

    @Test
    fun GameTestState.`resets available stop at 63, what the tree's varbit holds`() = pactTest {
        deps ->
        val pacts = deps.pacts
        assertEquals(63, DemonicPacts.MAX_RESETS)
        assertEquals(61, pacts.grantReset(player, 100))
        assertEquals(63, pacts.resetsAvailable(player))
        assertEquals(63, player.vars[PactState.RESETS_AVAILABLE])
        assertEquals(0, pacts.grantReset(player))
        assertEquals(63, pacts.resetsAvailable(player))

        player.modLevel = Rights.ADMINISTRATOR
        player.cheat("pactresets", "70")
        assertMessageSent("At most 63 pact resets can be available.")
        assertEquals(63, pacts.resetsAvailable(player))
        assertEquals(63, player.vars[PactState.RESETS_AVAILABLE])
    }

    @Test
    fun GameTestState.`a reset with resets earned past the cap leaves 62`() = pactTest { deps ->
        val pacts = deps.pacts
        player.setVarp(PactState.RESETS_GRANTED, 100)
        pacts.refresh(player)
        assertEquals(63, pacts.resetsAvailable(player))
        assertEquals(63, player.vars[PactState.RESETS_AVAILABLE])

        pacts.commit(player, indices(deps, "AA", "BA"))
        assertInstanceOf(PactResetResult.Reset::class.java, pacts.reset(player))
        assertEquals(62, pacts.resetsAvailable(player))
        assertEquals(62, player.vars[PactState.RESETS_AVAILABLE])
    }

    @Test
    fun GameTestState.`the state survives a relog`() = pactTest { deps ->
        val pacts = deps.pacts
        pacts.grantPoints(player, 3)
        pacts.commit(player, indices(deps, "AA", "BA", "BB", "BC"))
        pacts.grantReset(player)
        pacts.reset(player)
        pacts.commit(player, indices(deps, "B1", "C1"))
        val owned = pacts.owned(player)

        val relogged = relog(player)
        assertEquals(owned, pacts.owned(relogged))
        assertEquals(5, pacts.pointsEarned(relogged))
        assertEquals(5, relogged.vars[PactState.POINTS_EARNED])
        assertEquals(2, relogged.vars[PactState.POINTS_SPENT])
        assertEquals(2, relogged.vars[PactState.RESETS_AVAILABLE])
        assertEquals(PactEffects.of(owned), pacts.effects(relogged))
        val regen = PactEffectVarbits.VARBITS.getValue(REGENERATE)
        assertEquals(pacts.effects(relogged)[REGENERATE], relogged.vars[regen])
    }

    @Test
    fun GameTestState.`the server-only counters are saved and never sent`() = pactTest {
        for (varp in COUNTERS) {
            val type = ServerCacheManager.getVarp(varp.asVarpId()) ?: error("Missing $varp")
            assertEquals(VarpLifetime.Perm, type.scope, varp)
            assertEquals(VarpTransmitLevel.Never, type.transmit, varp)
        }
    }

    @Test
    fun GameTestState.`effects sum repeated nodes and the panel shows clamped totals`() =
        pactTest { deps ->
            val pacts = deps.pacts
            val prayerPenetration = deps.tree.nodes.filter { it.effect.id == PRAYER_PENETRATION }
            assertEquals(9, prayerPenetration.size)
            pacts.setOwnedUnchecked(player, prayerPenetration + nodes(deps, "CA", "CB", "CC"))

            val effects = pacts.effects(player)
            assertEquals(225, effects[PRAYER_PENETRATION])
            val accuracy = nodes(deps, "CA", "CB", "CC").sumOf { it.effect.value }
            assertEquals(accuracy, effects[ALL_STYLE_ACCURACY])
            val penetrationVarbit = PactEffectVarbits.VARBITS.getValue(PRAYER_PENETRATION)
            assertEquals(127, player.vars[penetrationVarbit])
            val accuracyVarbit = PactEffectVarbits.VARBITS.getValue(ALL_STYLE_ACCURACY)
            assertEquals(accuracy, player.vars[accuracyVarbit])

            pacts.setOwnedUnchecked(player, emptyList())
            assertEquals(PactEffects.NONE, pacts.effects(player))
            assertEquals(0, player.vars[penetrationVarbit])
        }

    @Test
    fun GameTestState.`every effect id of the tree has a talent varbit`() = pactTest { deps ->
        assertEquals(deps.tree.effectIds, PactEffectVarbits.VARBITS.keys)
        for (varbit in PactEffectVarbits.VARBITS.values) {
            assertTrue(PactState.maxValue(varbit) > 0, varbit)
        }
    }

    @Test
    fun GameTestState.`listeners hear commits and resets`() {
        val heard = mutableListOf<String>()
        val listener =
            object : PactCommitListener, PactResetListener {
                override fun onCommit(player: Player, added: List<PactNode>) {
                    heard += "commit ${added.ids()}"
                }

                override fun onReset(player: Player, removed: List<PactNode>) {
                    heard += "reset ${removed.ids()}"
                }
            }
        val module =
            object : AbstractModule() {
                override fun configure() {
                    Multibinder.newSetBinder(binder(), PactCommitListener::class.java)
                        .addBinding()
                        .toInstance(listener)
                    Multibinder.newSetBinder(binder(), PactResetListener::class.java)
                        .addBinding()
                        .toInstance(listener)
                }
            }
        pactTest(module) { deps ->
            deps.pacts.commit(player, indices(deps, "AA", "BA"))
            deps.pacts.commit(player, indices(deps, "N9"))
            deps.pacts.reset(player)
            assertEquals(listOf("commit [AA, BA]", "reset [BA]"), heard)
        }
    }

    @Test
    fun GameTestState.`a server can replace the spend cap, the reset guard and the settings`() {
        val module =
            object : AbstractModule() {
                override fun configure() {
                    bind(PactSettings::class.java)
                        .toInstance(PactSettings(startingPoints = 5, freeResets = 1))
                    bind(PactSpendCap::class.java).toInstance(FixedCap(3))
                    bind(PactResetGuard::class.java).toInstance(RefuseResets)
                }
            }
        pactTest(module) { deps ->
            val pacts = deps.pacts
            assertEquals(5, pacts.pointsEarned(player))
            assertEquals(3, player.vars[PactState.POINTS_EARNED])
            assertEquals(1, pacts.resetsAvailable(player))
            val tooMany = pacts.commit(player, indices(deps, "AA", "BA", "BB", "BC", "B1"))
            assertEquals(PactCommitResult.Refused(PactCommitRefusal.OverSpendCap), tooMany)
            pacts.commit(player, indices(deps, "AA", "BA"))
            assertEquals(PactResetResult.Refused(RefuseResets.MESSAGE), pacts.reset(player))
            assertEquals(1, pacts.resetsAvailable(player))
        }
    }

    @Test
    fun GameTestState.`admin commands set nodes, points and resets`() = pactTest { deps ->
        val pacts = deps.pacts
        player.modLevel = Rights.ADMINISTRATOR
        player.cheat("pactset", "aa", "CA", "50")
        assertEquals(listOf("AA", "CA", "F4"), pacts.owned(player).ids())
        assertMessageSent("You now own 3 pacts: AA, CA, F4.")

        player.cheat("pactpoints", "10")
        assertEquals(12, pacts.pointsEarned(player))
        assertMessageSent("Pact points: 12 earned, 2 spent, cap 40. Resets: 2.")

        player.cheat("pactresets", "0")
        assertEquals(0, pacts.resetsAvailable(player))
        player.cheat("pactresets", "4")
        assertEquals(4, pacts.resetsAvailable(player))
        assertEquals(4, player.vars[PactState.RESETS_AVAILABLE])

        player.cheat("pactset", "all")
        assertEquals(deps.tree.size, pacts.owned(player).size)
        player.cheat("pactset", "none")
        assertTrue(pacts.owned(player).isEmpty())
        player.cheat("pactset", "ZZ")
        assertMessageSent("Usage: ::pactset <id ...> (wiki ids or indices), all or none")
        assertTrue(pacts.owned(player).isEmpty())
    }

    @Test
    fun GameTestState.`the admin commands are refused to normal players`() = pactTest { deps ->
        player.modLevel = Rights.NONE
        player.cheat("pactset", "all")
        player.cheat("pactpoints", "40")
        assertTrue(deps.pacts.owned(player).isEmpty())
        assertEquals(2, deps.pacts.pointsEarned(player))
    }

    @Test
    fun GameTestState.`a granted point is never lost to a refresh`() = pactTest { deps ->
        val pacts = deps.pacts
        pacts.grantPoints(player, 1)
        pacts.refresh(player)
        pacts.refreshOnLogin(player)
        assertEquals(3, pacts.pointsEarned(player))
        assertTrue(pacts.owned(player).isEmpty())
    }

    private fun GameTestState.pactTest(
        module: Module? = null,
        body: GameTestScope.(PactDeps) -> Unit,
    ) {
        val active = listOfNotNull(PactsActiveForEveryone, module)
        runInjectedGameTest(
            PactDeps::class,
            Modules.combine(active),
            DemonicPactsStateScript::class,
            testBody = body,
        )
    }

    private fun GameTestScope.ownedVarps(): List<Int> =
        (0..4).map { player.vars["${PactState.OWNED_VARP_PREFIX}$it"] }

    private fun ownedBits(deps: PactDeps, vararg ids: String): List<Int> {
        val words = IntArray(5)
        for (node in nodes(deps, *ids)) {
            words[node.varpIndex] = words[node.varpIndex] or (1 shl node.bitInVarp)
        }
        return words.toList()
    }

    /** [count] non-root nodes, each linked to the root or to one before it. */
    private fun linkedPath(tree: PactTree, count: Int): List<PactNode> {
        val reached = mutableListOf(tree.root)
        val path = mutableListOf<PactNode>()
        var i = 0
        while (path.size < count) {
            for (next in tree.neighbours(reached[i])) {
                if (next !in reached && path.size < count) {
                    reached += next
                    path += next
                }
            }
            i++
        }
        return path
    }

    private fun nodes(deps: PactDeps, vararg ids: String): List<PactNode> =
        ids.map(deps.tree::get).sortedBy(PactNode::index)

    private fun indices(deps: PactDeps, vararg ids: String): List<Int> =
        ids.map { deps.tree[it].index }

    private fun List<PactNode>.ids(): List<String> = map(PactNode::id)

    private fun String.asVarpId(): Int = asRSCM(RSCMType.VARP)

    class PactDeps @Inject constructor(val pacts: DemonicPacts, val tree: PactTree)

    private class FixedCap(private val cap: Int) : PactSpendCap {
        override fun cap(player: Player): Int = cap
    }

    private object RefuseResets : PactResetGuard {
        const val MESSAGE = "Not now."

        override fun refusal(player: Player): String = MESSAGE
    }

    private companion object {
        const val REGENERATE = 1
        const val ALL_STYLE_ACCURACY = 12
        const val PRAYER_PENETRATION = 40

        val COUNTERS =
            listOf(PactState.POINTS_GRANTED, PactState.RESETS_GRANTED, PactState.RESETS_USED)
    }
}
