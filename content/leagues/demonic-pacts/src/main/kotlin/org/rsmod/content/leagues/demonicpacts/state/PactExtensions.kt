package org.rsmod.content.leagues.demonicpacts.state

import com.google.inject.ImplementedBy
import jakarta.inject.Inject
import org.rsmod.content.leagues.demonicpacts.tree.PactNode
import org.rsmod.game.entity.Player

/**
 * Decides whether [player] has Demonic Pacts. Every pact entry point asks it: an inactive player
 * can't open the tree, commit, reset or be granted anything, gets no pact vars on login, and has no
 * effects ([DemonicPacts.effects] is [PactEffects.NONE]), so combat hooks change nothing for them.
 * The default, [ConfigPactActivation], answers `game.yml`'s `enabled` for everyone; a server with
 * several rulesets on one world binds one that checks the player's own.
 */
@ImplementedBy(ConfigPactActivation::class)
fun interface PactActivation {
    fun isActive(player: Player): Boolean
}

/**
 * Where a player's pact points come from. Every source in the set (a Guice multibinder) is asked
 * on each [DemonicPacts.refresh] and the answers are summed into the points earned. The defaults
 * are [StartingPactPoints] and [GrantedPactPoints]; a server adds its own (combat level, tasks).
 */
interface PactPointSource {
    /** The points this source gives [player] in total, not since the last call. */
    fun points(player: Player): Int
}

/** The most points [player] may have spent at once. The default is [PactSettings.spendCap]. */
@ImplementedBy(DefaultPactSpendCap::class)
interface PactSpendCap {
    fun cap(player: Player): Int
}

/**
 * Where a player's pact resets come from. Every source in the set (a Guice multibinder) is summed
 * into the resets earned; the resets available are those earned minus those used. The defaults are
 * [FreePactResets] and [GrantedPactResets].
 */
interface PactResetSource {
    /** The resets this source gives [player] in total, not since the last call. */
    fun resets(player: Player): Int
}

/** Decides whether [player] may reset their pacts now. The default always allows it. */
@ImplementedBy(AllowPactResets::class)
interface PactResetGuard {
    /** The message to refuse the reset with, or null to allow it. */
    fun refusal(player: Player): String?
}

/** Told after a commit has bought [added] for [player] and the vars are up to date. */
interface PactCommitListener {
    fun onCommit(player: Player, added: List<PactNode>)
}

/** Told after a reset has taken [removed] from [player] and the vars are up to date. */
interface PactResetListener {
    fun onReset(player: Player, removed: List<PactNode>)
}

/**
 * Told after every [DemonicPacts.refresh] (login, commit, reset, grant and the admin commands) with
 * [player]'s current effects, so effects that keep their own state (timers, stat boosts) can start,
 * update or stop it.
 */
interface PactEffectsListener {
    fun onEffectsRefreshed(player: Player, effects: PactEffects)
}

/**
 * The value each node adds to its effect. The default, [CachePactEffectValues], is the `effect`
 * column of `dbtable.talent_tree` (Jagex's values); a server binds its own to rebalance nodes
 * without changing the cache. The tree's tooltips still show the cache's value.
 */
@ImplementedBy(CachePactEffectValues::class)
fun interface PactEffectValues {
    fun value(node: PactNode): Int
}

class CachePactEffectValues @Inject constructor() : PactEffectValues {
    override fun value(node: PactNode): Int = node.effect.value
}

class ConfigPactActivation @Inject constructor(private val settings: PactSettings) :
    PactActivation {
    override fun isActive(player: Player): Boolean = settings.enabled
}

class StartingPactPoints @Inject constructor(private val settings: PactSettings) :
    PactPointSource {
    override fun points(player: Player): Int = settings.startingPoints
}

/** The points given with [DemonicPacts.grantPoints]. */
class GrantedPactPoints @Inject constructor(private val state: PactState) : PactPointSource {
    override fun points(player: Player): Int = state.pointsGranted(player)
}

class FreePactResets @Inject constructor(private val settings: PactSettings) : PactResetSource {
    override fun resets(player: Player): Int = settings.freeResets
}

/** The resets given with [DemonicPacts.grantReset]. */
class GrantedPactResets @Inject constructor(private val state: PactState) : PactResetSource {
    override fun resets(player: Player): Int = state.resetsGranted(player)
}

class DefaultPactSpendCap @Inject constructor(private val settings: PactSettings) : PactSpendCap {
    override fun cap(player: Player): Int = settings.spendCap
}

class AllowPactResets : PactResetGuard {
    override fun refusal(player: Player): String? = null
}
