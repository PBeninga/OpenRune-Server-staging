package org.rsmod.content.leagues.demonicpacts.effects.ranged

import com.google.inject.AbstractModule
import com.google.inject.Module
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Modules
import dev.openrune.util.Wearpos
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierHitImpactScript
import org.rsmod.api.combat.modifiers.CombatModifierPipeline
import org.rsmod.api.combat.modifiers.CombatModifierProvider
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitDealtEvent
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.modifiers.ResourceContext
import org.rsmod.api.combat.modifiers.ResourceKind
import org.rsmod.api.combat.modifiers.ResourceModifiers
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.PactsActiveForEveryone
import org.rsmod.content.leagues.demonicpacts.effects.registerOpponent
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.DemonicPactsStateScript
import org.rsmod.content.leagues.demonicpacts.tree.PactTree
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid

/** A Regenerate chance for ammo, as the Regenerate pacts give; the echo pacts fire on it. */
class TestRegenerate : CombatModifierProvider {
    var ammoPercent: Double = 100.0

    override fun resourceModifiers(context: ResourceContext): ResourceModifiers =
        if (context.kind == ResourceKind.Ammo) {
            ResourceModifiers(ammoPercent)
        } else {
            ResourceModifiers.NONE
        }
}

/** Records the hits the pipeline sees. */
class HitRecorder : CombatProcListener {
    val rolled: MutableList<HitRolledEvent> = mutableListOf()
    val dealt: MutableList<HitDealtEvent> = mutableListOf()

    fun dealt(source: HitSource): List<HitDealtEvent> = dealt.filter { it.source == source }

    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        rolled += event
        return emptyList()
    }

    override fun onHitDealt(event: HitDealtEvent) {
        dealt += event
    }
}

class RangedPactsTestModule(
    private val regenerate: TestRegenerate,
    private val recorder: HitRecorder,
) : AbstractModule() {
    override fun configure() {
        install(RangedPactsModule())
        bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        Multibinder.newSetBinder(binder(), CombatModifierProvider::class.java)
            .addBinding()
            .toInstance(regenerate)
        Multibinder.newSetBinder(binder(), CombatProcListener::class.java)
            .addBinding()
            .toInstance(recorder)
    }
}

class RangedPactsDeps
@Inject
constructor(
    val pipeline: CombatModifierPipeline,
    val manager: PlayerAttackManager,
    val ammo: RangedAmmoManager,
    val pacts: DemonicPacts,
    val bowStacks: BowStacks,
    val extraTarget: ThrownExtraTarget,
    val areaChecker: AreaChecker,
    val playerHitModifier: PlayerHitModifier,
    val wornBonuses: WornBonuses,
    val tree: PactTree,
)

class RangedPactsTestScope(
    val scope: GameTestScope,
    val deps: RangedPactsDeps,
    val regenerate: TestRegenerate,
    val recorder: HitRecorder,
)

fun GameTestState.rangedPactTest(
    activation: Module = PactsActiveForEveryone,
    body: RangedPactsTestScope.() -> Unit,
) {
    val regenerate = TestRegenerate()
    val recorder = HitRecorder()
    runInjectedGameTest(
        RangedPactsDeps::class,
        Modules.combine(activation, RangedPactsTestModule(regenerate, recorder)),
        DemonicPactsStateScript::class,
        NpcHitScript::class,
        PlayerHitScript::class,
        CombatModifierHitImpactScript::class,
        WeaponAttackStylesScript::class,
        WeaponAttackTypesScript::class,
    ) { deps ->
        player.placeAt(RANGED_TEST_COORDS)
        setLevels("stat.ranged", 99)
        setLevels("stat.attack", 99)
        setLevels("stat.strength", 99)
        setLevels("stat.hitpoints", 99)
        RangedPactsTestScope(this, deps, regenerate, recorder).body()
    }
}

val RANGED_TEST_COORDS: CoordGrid = CoordGrid(0, 50, 50, 22, 18)

const val TARGET_HP: Int = 250

/** Owns exactly [ids] with the admin `::pactset`, as the effect tests do. */
fun RangedPactsTestScope.own(vararg ids: String) {
    scope.player.modLevel = Rights.ADMINISTRATOR
    with(scope) { player.cheat("pactset", *ids) }
}

fun RangedPactsTestScope.wield(obj: String, count: Int = 1) {
    scope.player.worn[Wearpos.RightHand.slot] = InvObj(obj, count)
}

fun RangedPactsTestScope.quiver(obj: String, count: Int = 1000) {
    scope.player.worn[Wearpos.Quiver.slot] = InvObj(obj, count)
}

fun RangedPactsTestScope.wear(wearpos: Wearpos, obj: String) {
    scope.player.worn[wearpos.slot] = InvObj(obj)
}

fun GameTestScope.setLevels(stat: String, level: Int) {
    player.setBaseLevel(stat, level)
    player.setCurrentLevel(stat, level)
}

/** Spawns a man with [TARGET_HP] hitpoints [tiles] east of the player. */
fun RangedPactsTestScope.target(tiles: Int = 1, from: CoordGrid = RANGED_TEST_COORDS): Npc {
    val coords = from.translateX(tiles)
    scope.allocZoneCollision(coords)
    val npc = scope.spawnNpc(coords, "npc.man")
    npc.baseHitpointsLvl = TARGET_HP
    npc.hitpoints = TARGET_HP
    return npc
}

/** The summed modifiers of an attack on [target] (an npc or a player) with the worn weapon. */
fun RangedPactsTestScope.modifiers(
    target: PathingEntity,
    style: CombatStyle = CombatStyle.Ranged,
    source: HitSource = HitSource.Base,
): AttackModifiers {
    val weapon = scope.player.worn[Wearpos.RightHand.slot]?.let(::getInvObj)
    val context =
        AttackContext(scope.player, target, style, weapon, isSpecial = false, source = source)
    return deps.pipeline.attackModifiers(context)
}

/** A player with 99 Hitpoints [tiles] east of the player, for the PvP cases. */
fun RangedPactsTestScope.opponent(tiles: Int = 1): Player =
    scope.registerOpponent(RANGED_TEST_COORDS.translateX(tiles))

/**
 * Makes a ranged attack with the quivered ammo on the player [opponent], the way `PvPCombat` does,
 * and lets the hit land. Random rolls, in order: the accuracy roll, then the damage roll.
 */
fun RangedPactsTestScope.pvpRangedAttack(opponent: Player) {
    val player = scope.player
    val weapon = checkNotNull(player.worn[Wearpos.RightHand.slot])
    val quiver = getInvObj(checkNotNull(player.worn[Wearpos.Quiver.slot]))
    deps.pipeline.withAttack(player, opponent, CombatStyle.Ranged) {
        deps.ammo.useQuiverAmmo(player, quiver, opponent.coords, dropDelay = 1)
        val ranged = CombatAttack.Ranged(weapon, RANGED_TYPE, RANGED_STYLE)
        val damage = deps.manager.rollRangedDamage(player, opponent, ranged)
        deps.manager.queueRangedHit(player, opponent, quiver, damage, clientDelay = 0, hitDelay = 1)
    }
    scope.advance(SETTLE_TICKS)
}

/**
 * Makes a ranged attack on [npc] the way `PvNCombat` does: spends the ammo (Regenerate is decided
 * here), rolls the attack and queues the hit, then lets the hits land.
 *
 * Random rolls, in order: the accuracy roll, the damage roll, then any echo chance roll and the
 * echo's own accuracy and damage rolls. Without a Regenerate, the ammo's drop roll comes first.
 */
fun RangedPactsTestScope.rangedAttack(npc: Npc, special: Boolean = false) {
    val player = scope.player
    val weapon = checkNotNull(player.worn[Wearpos.RightHand.slot])
    val weaponType = getInvObj(weapon)
    val quiver = player.worn[Wearpos.Quiver.slot]
    val thrown = quiver == null
    deps.pipeline.withAttack(player, npc, CombatStyle.Ranged) {
        val attack = {
            if (thrown) {
                deps.ammo.useThrownWeapon(player, weaponType, npc.coords, dropDelay = 1)
            } else {
                deps.ammo.useQuiverAmmo(player, getInvObj(quiver!!), npc.coords, dropDelay = 1)
            }
            val ranged = CombatAttack.Ranged(weapon, RANGED_TYPE, RANGED_STYLE)
            val damage = deps.manager.rollRangedDamage(player, npc, ranged)
            val ammo = quiver?.let(::getInvObj)
            deps.manager.queueRangedHit(player, npc, ammo, damage, clientDelay = 0, hitDelay = 1)
        }
        if (special) deps.pipeline.withSpecialAttack(player) { attack() } else attack()
    }
    scope.advance(SETTLE_TICKS)
}

/** Makes a melee attack on [npc] with the worn weapon and lets the hits land. */
fun RangedPactsTestScope.meleeAttack(npc: Npc) {
    val player = scope.player
    val weapon = player.worn[Wearpos.RightHand.slot]
    deps.pipeline.withAttack(player, npc, CombatStyle.Melee) {
        val melee =
            CombatAttack.Melee(
                weapon,
                MeleeAttackType.Slash,
                MeleeAttackStyle.Aggressive,
                CombatStance.Stance1,
            )
        val damage = deps.manager.rollMeleeDamage(player, npc, melee)
        deps.manager.queueMeleeHit(player, npc, damage)
    }
    scope.advance(SETTLE_TICKS)
}

fun RangedPactsTestScope.rangedMaxHit(npc: Npc): Int =
    deps.manager.calculateRangedMaxHit(scope.player, npc, RANGED_TYPE, RANGED_STYLE, 1.0, 0)

fun RangedPactsTestScope.queueRolls(vararg rolls: Int) {
    for (roll in rolls) {
        scope.random.then = roll
    }
}

val RANGED_TYPE: RangedAttackType = RangedAttackType.Standard
val RANGED_STYLE: RangedAttackStyle = RangedAttackStyle.Accurate

const val SETTLE_TICKS: Int = 3

/** An accuracy roll that always hits, and one that always misses. */
const val HIT: Int = 0
const val MISS: Int = 9_999
