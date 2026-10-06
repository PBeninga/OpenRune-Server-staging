package org.rsmod.content.leagues.demonicpacts.effects.magic

import com.google.inject.AbstractModule
import com.google.inject.Module
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Modules
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierHitImpactScript
import org.rsmod.api.combat.modifiers.CombatModifierPipeline
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitDealtEvent
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.PactsActiveForEveryone
import org.rsmod.content.leagues.demonicpacts.effects.registerOpponent
import org.rsmod.content.leagues.demonicpacts.state.DemonicPactsStateScript
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid

/** Records the hits the pipeline sees. */
class MagicHitRecorder : CombatProcListener {
    val dealt: MutableList<HitDealtEvent> = mutableListOf()

    fun dealt(source: HitSource): List<HitDealtEvent> = dealt.filter { it.source == source }

    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> = emptyList()

    override fun onHitDealt(event: HitDealtEvent) {
        dealt += event
    }
}

class MagicPactsTestModule(private val recorder: MagicHitRecorder) : AbstractModule() {
    override fun configure() {
        bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        Multibinder.newSetBinder(binder(), CombatProcListener::class.java)
            .addBinding()
            .toInstance(recorder)
    }
}

class MagicPactsDeps
@Inject
constructor(
    val pipeline: CombatModifierPipeline,
    val manager: PlayerAttackManager,
    val procs: MagicPactProcs,
    val burns: PactBurns,
    val wornBonuses: WornBonuses,
    val areaChecker: AreaChecker,
)

class MagicPactsTestScope(
    val scope: GameTestScope,
    val deps: MagicPactsDeps,
    val recorder: MagicHitRecorder,
)

fun GameTestState.magicPactTest(
    activation: Module = PactsActiveForEveryone,
    body: MagicPactsTestScope.() -> Unit,
) {
    val recorder = MagicHitRecorder()
    runInjectedGameTest(
        MagicPactsDeps::class,
        Modules.combine(activation, MagicPactsTestModule(recorder)),
        MagicPactsScript::class,
        DemonicPactsStateScript::class,
        NpcHitScript::class,
        PlayerHitScript::class,
        CombatModifierHitImpactScript::class,
        WeaponAttackStylesScript::class,
        WeaponAttackTypesScript::class,
    ) { deps ->
        player.placeAt(MAGIC_TEST_COORDS)
        setLevels("stat.magic", 99)
        setLevels("stat.hitpoints", 99)
        setLevels("stat.defence", 99)
        MagicPactsTestScope(this, deps, recorder).body()
    }
}

val MAGIC_TEST_COORDS: CoordGrid = CoordGrid(0, 50, 50, 22, 18)

const val TARGET_HP: Int = 250
const val BASE_MAX_HIT: Int = 20
const val SPELL_ATTACK_RATE: Int = 5
const val STAFF_ATTACK_RATE: Int = 4
const val SETTLE_TICKS: Int = 3

/** An accuracy roll that always hits, and one that always misses. */
const val HIT: Int = 0
const val MISS: Int = 9_999

val MagicPactsTestScope.player
    get() = scope.player

/** Owns exactly [ids] with the admin `::pactset`, as the effect tests do. */
fun MagicPactsTestScope.own(vararg ids: String) {
    scope.player.modLevel = Rights.ADMINISTRATOR
    with(scope) { player.cheat("pactset", *ids) }
}

fun MagicPactsTestScope.wear(wearpos: Wearpos, obj: String) {
    scope.player.worn[wearpos.slot] = InvObj(obj)
}

fun MagicPactsTestScope.setCurrentLevel(stat: String, level: Int) {
    with(scope) { player.setCurrentLevel(stat, level) }
}

fun MagicPactsTestScope.setVarBit(varbit: String, value: Int) {
    with(scope) { player.setVarBit(varbit, value) }
}

fun MagicPactsTestScope.placeAt(coords: CoordGrid) {
    with(scope) { player.placeAt(coords) }
}

fun GameTestScope.setLevels(stat: String, level: Int) {
    player.setBaseLevel(stat, level)
    player.setCurrentLevel(stat, level)
}

fun MagicPactsTestScope.queueRolls(vararg rolls: Int) {
    for (roll in rolls) {
        scope.random.then = roll
    }
}

/** Spawns [npc] with [TARGET_HP] hitpoints [tiles] east of [from]. */
fun MagicPactsTestScope.target(
    tiles: Int = 1,
    from: CoordGrid = MAGIC_TEST_COORDS,
    npc: String = "npc.man",
): Npc {
    val coords = from.translateX(tiles)
    scope.allocZoneCollision(coords)
    val spawned = scope.spawnNpc(coords, npc)
    spawned.baseHitpointsLvl = TARGET_HP
    spawned.hitpoints = TARGET_HP
    return spawned
}

fun spellbook(spell: String): Spellbook =
    if (spell.removePrefix("obj.").substringAfter('_').substringBefore('_') in ANCIENT_ELEMENTS) {
        Spellbook.Ancients
    } else {
        Spellbook.Standard
    }

private val ANCIENT_ELEMENTS = setOf("smoke", "ice", "blood", "shadow")

/** The hit range of [spell] (base max hit [BASE_MAX_HIT]) against [target]. */
fun MagicPactsTestScope.spellRange(target: PathingEntity, spell: String): IntRange =
    deps.manager.calculateSpellMaxHit(
        scope.player,
        target,
        scope.objType(spell),
        spellbook(spell),
        BASE_MAX_HIT,
        SPELL_ATTACK_RATE,
        sunfireRune = false,
    )

/** The summed modifiers of [spell] cast on [target] (a powered staff's spell when `null`). */
fun MagicPactsTestScope.modifiers(target: PathingEntity, spell: String?): AttackModifiers {
    val weapon = scope.player.worn[Wearpos.RightHand.slot]?.let(::getInvObj)
    val spellType = spell?.let { scope.objType(it) }
    val context =
        AttackContext(scope.player, target, CombatStyle.Magic, weapon, isSpecial = false, spellType)
    return deps.pipeline.attackModifiers(context)
}

/**
 * Casts [spell] on [target] (an npc or a player) the way a spell attack does inside `PvNCombat`'s
 * or `PvPCombat`'s attack scope: rolls accuracy, rolls damage on a hit, queues the hit, then lets
 * the hits land.
 *
 * Random rolls, in order: the accuracy roll, then the damage roll on a hit; then each bounce's
 * accuracy and damage rolls.
 */
fun MagicPactsTestScope.cast(target: PathingEntity, spell: String) {
    val player = scope.player
    val type: ItemServerType = scope.objType(spell)
    val book = spellbook(spell)
    deps.pipeline.withAttack(player, target, CombatStyle.Magic, type) {
        val hit = deps.manager.rollSpellAccuracy(player, target, type, book, sunfireRune = false)
        val damage =
            if (hit) {
                deps.manager.rollSpellMaxHit(
                    player,
                    target,
                    type,
                    book,
                    BASE_MAX_HIT,
                    SPELL_ATTACK_RATE,
                    sunfireRune = false,
                )
            } else {
                0
            }
        deps.manager.queueMagicHit(player, target, type, damage, clientDelay = 0, hitDelay = 1)
    }
    scope.advance(SETTLE_TICKS)
}

/** The attack delay set by an attack on [target] whose base rate is [cycles]. */
fun MagicPactsTestScope.attackDelay(target: PathingEntity, spell: String?, cycles: Int): Int {
    val player = scope.player
    val type = spell?.let { scope.objType(it) }
    deps.pipeline.withAttack(player, target, CombatStyle.Magic, type) {
        deps.manager.setNextAttackDelay(player, cycles)
    }
    return player.actionDelay - player.currentMapClock
}

/** A player with 99 Hitpoints [tiles] east of the player, for the PvP cases. */
fun MagicPactsTestScope.opponent(tiles: Int = 1): Player =
    scope.registerOpponent(MAGIC_TEST_COORDS.translateX(tiles))

/** The first of a few known multi-combat spots, with open ground to the east. */
fun MagicPactsTestScope.multiCombatCoords(): CoordGrid {
    val found = MULTI_CANDIDATES.firstOrNull { deps.areaChecker.inArea(MULTIWAY, it) }
    return checkNotNull(found) { "None of $MULTI_CANDIDATES is in $MULTIWAY." }
}

private const val MULTIWAY = "area.multiway"

private val MULTI_CANDIDATES: List<CoordGrid> =
    listOf(
        CoordGrid(2882, 5310, 2),
        CoordGrid(3484, 9510, 2),
        CoordGrid(1665, 10050, 0),
        CoordGrid(3082, 3420, 0),
        CoordGrid(3150, 3800, 0),
    )
