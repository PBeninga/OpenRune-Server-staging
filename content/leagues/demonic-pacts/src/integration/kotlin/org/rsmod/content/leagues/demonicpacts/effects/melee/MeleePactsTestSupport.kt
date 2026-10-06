package org.rsmod.content.leagues.demonicpacts.effects.melee

import com.google.inject.AbstractModule
import com.google.inject.Module
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Modules
import dev.openrune.util.Wearpos
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatStance
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierHitImpactScript
import org.rsmod.api.combat.modifiers.CombatModifierPipeline
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.stat.PlayerHealing
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.PactsActiveForEveryone
import org.rsmod.content.leagues.demonicpacts.effects.defence.DefencePactsScript
import org.rsmod.content.leagues.demonicpacts.effects.ranged.HitRecorder
import org.rsmod.content.leagues.demonicpacts.effects.ranged.setLevels
import org.rsmod.content.leagues.demonicpacts.effects.registerOpponent
import org.rsmod.content.leagues.demonicpacts.state.DemonicPactsStateScript
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid

class MeleePactsTestModule(private val recorder: HitRecorder) : AbstractModule() {
    override fun configure() {
        install(MeleePactsModule())
        bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        Multibinder.newSetBinder(binder(), CombatProcListener::class.java)
            .addBinding()
            .toInstance(recorder)
    }
}

class MeleePactsDeps
@Inject
constructor(
    val pipeline: CombatModifierPipeline,
    val manager: PlayerAttackManager,
    val healing: PlayerHealing,
    val wornBonuses: WornBonuses,
    val playerHitModifier: PlayerHitModifier,
    val blindbag: Blindbag,
)

class MeleePactsTestScope(
    val scope: GameTestScope,
    val deps: MeleePactsDeps,
    val recorder: HitRecorder,
)

/**
 * A test with the melee pacts bound, the player at [MELEE_TEST_COORDS] with 99 Attack, Strength
 * and Hitpoints. Thorns (D1) works too, for G5 and G10.
 */
fun GameTestState.meleePactTest(
    activation: Module = PactsActiveForEveryone,
    body: MeleePactsTestScope.() -> Unit,
) {
    val recorder = HitRecorder()
    runInjectedGameTest(
        MeleePactsDeps::class,
        Modules.combine(activation, MeleePactsTestModule(recorder)),
        DemonicPactsStateScript::class,
        DefencePactsScript::class,
        NpcHitScript::class,
        PlayerHitScript::class,
        CombatModifierHitImpactScript::class,
        WeaponAttackStylesScript::class,
        WeaponAttackTypesScript::class,
    ) { deps ->
        player.placeAt(MELEE_TEST_COORDS)
        setLevels("stat.attack", 99)
        setLevels("stat.strength", 99)
        setLevels("stat.hitpoints", 99)
        MeleePactsTestScope(this, deps, recorder).body()
    }
}

val MELEE_TEST_COORDS: CoordGrid = CoordGrid(0, 50, 50, 22, 18)

const val MELEE_TARGET_HP: Int = 250

/** Owns exactly [ids] with the admin `::pactset`, as the effect tests do. */
fun MeleePactsTestScope.own(vararg ids: String) {
    scope.player.modLevel = Rights.ADMINISTRATOR
    with(scope) { player.cheat("pactset", *ids) }
}

fun MeleePactsTestScope.wield(obj: String) {
    scope.player.worn[Wearpos.RightHand.slot] = InvObj(obj)
}

fun MeleePactsTestScope.wear(wearpos: Wearpos, obj: String) {
    scope.player.worn[wearpos.slot] = InvObj(obj)
}

fun MeleePactsTestScope.carry(vararg objs: String) {
    for ((slot, obj) in objs.withIndex()) {
        scope.player.inv[slot] = InvObj(obj)
    }
}

/** Spawns a man with [MELEE_TARGET_HP] hitpoints [tiles] east of the player. */
fun MeleePactsTestScope.target(tiles: Int = 1): Npc {
    val coords = MELEE_TEST_COORDS.translateX(tiles)
    scope.allocZoneCollision(coords)
    val npc = scope.spawnNpc(coords, "npc.man")
    npc.baseHitpointsLvl = MELEE_TARGET_HP
    npc.hitpoints = MELEE_TARGET_HP
    return npc
}

/** The summed modifiers of an attack on [target] (an npc or a player) with the worn weapon. */
fun MeleePactsTestScope.modifiers(
    target: PathingEntity,
    style: CombatStyle = CombatStyle.Melee,
    source: HitSource = HitSource.Base,
    special: Boolean = false,
): AttackModifiers {
    val weapon = scope.player.worn[Wearpos.RightHand.slot]?.let(::getInvObj)
    val context = AttackContext(scope.player, target, style, weapon, special, source = source)
    return deps.pipeline.attackModifiers(context)
}

/** A player with 99 Hitpoints [tiles] east of the player, for the PvP cases. */
fun MeleePactsTestScope.opponent(tiles: Int = 1): Player =
    scope.registerOpponent(MELEE_TEST_COORDS.translateX(tiles))

/** Makes a melee attack on the player [opponent], the way `PvPCombat` does. */
fun MeleePactsTestScope.pvpMeleeAttack(opponent: Player) {
    val player = scope.player
    val weapon = player.worn[Wearpos.RightHand.slot]
    deps.pipeline.withAttack(player, opponent, CombatStyle.Melee) {
        val melee =
            CombatAttack.Melee(
                weapon,
                MeleeAttackType.Slash,
                MeleeAttackStyle.Aggressive,
                CombatStance.Stance1,
            )
        val damage = deps.manager.rollMeleeDamage(player, opponent, melee)
        deps.manager.queueMeleeHit(player, opponent, damage)
    }
    scope.advance(MELEE_SETTLE_TICKS)
}

/**
 * Makes a melee attack on [npc] with the worn weapon, the way `PvNCombat` does, and lets the hits
 * land. Random rolls, in order: the accuracy roll, the damage roll, then the pacts' own rolls
 * (Blindbag, healing) and the rolls of any extra hit.
 */
fun MeleePactsTestScope.meleeAttack(npc: Npc, special: Boolean = false) {
    val player = scope.player
    val weapon = player.worn[Wearpos.RightHand.slot]
    deps.pipeline.withAttack(player, npc, CombatStyle.Melee) {
        val attack = {
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
        if (special) deps.pipeline.withSpecialAttack(player) { attack() } else attack()
    }
    scope.advance(MELEE_SETTLE_TICKS)
}

/** A ranged hit of [damage] on [npc], with no rolls, for the pacts that work with any style. */
fun MeleePactsTestScope.rangedHit(npc: Npc, damage: Int) {
    val player = scope.player
    deps.pipeline.withAttack(player, npc, CombatStyle.Ranged) {
        deps.manager.queueRangedHit(player, npc, null, damage, clientDelay = 0, hitDelay = 1)
    }
    scope.advance(MELEE_SETTLE_TICKS)
}

fun MeleePactsTestScope.attackDelay(target: PathingEntity, cycles: Int): Int =
    deps.pipeline.withAttack(scope.player, target, CombatStyle.Melee) {
        deps.pipeline.modifyAttackDelay(scope.player, cycles)
    }

fun MeleePactsTestScope.attackRange(target: PathingEntity, range: Int): Int =
    deps.pipeline.modifyAttackRange(scope.player, target, CombatStyle.Melee, range)

fun MeleePactsTestScope.queueRolls(vararg rolls: Int) {
    for (roll in rolls) {
        scope.random.then = roll
    }
}

const val MELEE_SETTLE_TICKS: Int = 3

/** An accuracy (or proc) roll that always succeeds, and one that always fails. */
const val ROLL_HIT: Int = 0
const val ROLL_MISS: Int = 9_999
