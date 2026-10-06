package org.rsmod.content.leagues.demonicpacts.effects.stats

import com.google.inject.AbstractModule
import com.google.inject.Module
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Modules
import dev.openrune.util.Wearpos
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierHitImpactScript
import org.rsmod.api.combat.modifiers.CombatModifierPipeline
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.NpcCombatRules
import org.rsmod.api.combat.modifiers.NpcDamageContext
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.stat.StatBoosts
import org.rsmod.api.stats.plugin.StatBoostScript
import org.rsmod.api.stats.plugin.StatRegenScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.PactsActiveForEveryone
import org.rsmod.content.leagues.demonicpacts.relog
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.DemonicPactsStateScript
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript

/** An npc protection prayer that blocks [percent] of every styled hit. */
class TestProtection : NpcCombatRules {
    var percent: Double = 0.0

    override fun protectionPercent(context: NpcDamageContext): Double = percent
}

class StatPactsTestModule(private val protection: TestProtection) : AbstractModule() {
    override fun configure() {
        bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        Multibinder.newSetBinder(binder(), NpcCombatRules::class.java)
            .addBinding()
            .toInstance(protection)
    }
}

class StatPactsDeps
@Inject
constructor(
    val pipeline: CombatModifierPipeline,
    val manager: PlayerAttackManager,
    val pacts: DemonicPacts,
    val boosts: StatBoosts,
    val tree: PactTree,
)

class StatPactsTestScope(
    val scope: GameTestScope,
    val deps: StatPactsDeps,
    val protection: TestProtection,
) {
    val player: Player
        get() = scope.player
}

/** The scripts of a stat pact test, in the order their login handlers are registered. */
val STAT_PACT_SCRIPTS: List<KClass<out PluginScript>> =
    listOf(
        StatPactsScript::class,
        DemonicPactsStateScript::class,
        StatBoostScript::class,
        StatRegenScript::class,
        NpcHitScript::class,
        PlayerHitScript::class,
        CombatModifierHitImpactScript::class,
        WeaponAttackStylesScript::class,
        WeaponAttackTypesScript::class,
    )

/** A test with the stat pacts bound and the player at 99 in every combat stat. */
fun GameTestState.statPactTest(
    activation: Module = PactsActiveForEveryone,
    scripts: List<KClass<out PluginScript>> = STAT_PACT_SCRIPTS,
    body: StatPactsTestScope.() -> Unit,
) {
    val protection = TestProtection()
    runInjectedGameTest(
        StatPactsDeps::class,
        Modules.combine(activation, StatPactsTestModule(protection)),
        *scripts.toTypedArray(),
    ) { deps ->
        player.placeAt(STAT_TEST_COORDS)
        for (stat in COMBAT_STATS) {
            player.setBaseLevel(stat, 99)
            player.setCurrentLevel(stat, 99)
        }
        StatPactsTestScope(this, deps, protection).body()
    }
}

val STAT_TEST_COORDS: CoordGrid = CoordGrid(0, 50, 50, 22, 18)

val COMBAT_STATS: List<String> =
    listOf(
        "stat.attack",
        "stat.strength",
        "stat.defence",
        "stat.ranged",
        "stat.magic",
        "stat.hitpoints",
        "stat.prayer",
    )

const val STAT_TARGET_HP: Int = 250

/** Owns exactly [ids] with the admin `::pactset`, as the effect tests do. */
fun StatPactsTestScope.own(vararg ids: String) {
    player.modLevel = Rights.ADMINISTRATOR
    with(scope) { player.cheat("pactset", *ids) }
}

fun StatPactsTestScope.wield(obj: String, count: Int = 1) {
    player.worn[Wearpos.RightHand.slot] = InvObj(obj, count)
}

fun StatPactsTestScope.quiver(obj: String) {
    player.worn[Wearpos.Quiver.slot] = InvObj(obj, 1000)
}

/** Spawns a man with [STAT_TARGET_HP] hitpoints next to the player. */
fun StatPactsTestScope.target(): Npc {
    val coords = STAT_TEST_COORDS.translateX(1)
    scope.allocZoneCollision(coords)
    val npc = scope.spawnNpc(coords, "npc.man")
    npc.baseHitpointsLvl = STAT_TARGET_HP
    npc.hitpoints = STAT_TARGET_HP
    return npc
}

/** The summed modifiers of a [style] attack on [target] (an npc or a player), worn weapon. */
fun StatPactsTestScope.modifiers(target: PathingEntity, style: CombatStyle): AttackModifiers {
    val weapon = player.worn[Wearpos.RightHand.slot]?.let(::getInvObj)
    val context = AttackContext(player, target, style, weapon, isSpecial = false)
    return deps.pipeline.attackModifiers(context)
}

fun StatPactsTestScope.meleeMaxHit(npc: Npc): Int =
    deps.manager.calculateMeleeMaxHit(
        player,
        npc,
        MeleeAttackType.Slash,
        MeleeAttackStyle.Aggressive,
        1.0,
    )

fun StatPactsTestScope.rangedMaxHit(npc: Npc): Int =
    deps.manager.calculateRangedMaxHit(
        player,
        npc,
        RangedAttackType.Standard,
        RangedAttackStyle.Accurate,
        1.0,
        0,
    )

fun StatPactsTestScope.staffMaxHit(npc: Npc, baseDamage: Int): Int =
    deps.manager.calculateStaffMaxHit(player, npc, baseDamage, 1.0)

/** [relog] for a stat pact test: the levels of [COMBAT_STATS] carry over. */
fun StatPactsTestScope.relog(player: Player): Player =
    scope.relog(player, STAT_TEST_COORDS, COMBAT_STATS)
