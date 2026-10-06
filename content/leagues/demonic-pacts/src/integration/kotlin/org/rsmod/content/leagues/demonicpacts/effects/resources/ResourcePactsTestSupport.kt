package org.rsmod.content.leagues.demonicpacts.effects.resources

import com.google.inject.AbstractModule
import com.google.inject.Module
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Modules
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isSuccess
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.combat.modifiers.AttackContext
import org.rsmod.api.combat.modifiers.AttackModifiers
import org.rsmod.api.combat.modifiers.CombatModifierHitImpactScript
import org.rsmod.api.combat.modifiers.CombatModifierPipeline
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ResourceConsumedEvent
import org.rsmod.api.combat.modifiers.ResourceDecision
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.ComboRuneScript
import org.rsmod.api.spells.runes.compact.CompactRuneScript
import org.rsmod.api.spells.runes.fake.FakeRuneScript
import org.rsmod.api.spells.runes.staves.StaffSubstituteScript
import org.rsmod.api.spells.runes.subs.RuneSubstituteScript
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.PactsActiveForEveryone
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.DemonicPactsStateScript
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript

/** Records every resource the pipeline is asked about. */
class ResourceRecorder : CombatProcListener {
    val events: MutableList<ResourceConsumedEvent> = mutableListOf()

    override fun onResourceConsumed(event: ResourceConsumedEvent): ResourceDecision {
        events += event
        return ResourceDecision.Consume
    }
}

class ResourcePactsTestModule(private val recorder: ResourceRecorder) : AbstractModule() {
    override fun configure() {
        bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        Multibinder.newSetBinder(binder(), CombatProcListener::class.java)
            .addBinding()
            .toInstance(recorder)
    }
}

class ResourcePactsDeps
@Inject
constructor(
    val pipeline: CombatModifierPipeline,
    val manager: PlayerAttackManager,
    val runes: MagicRuneManager,
    val spells: MagicSpellRegistry,
    val ammo: RangedAmmoManager,
    val charges: CombatChargeManager,
    val energy: SpecialAttackEnergy,
    val modifiers: ResourcePactModifiers,
    val boosts: PactRegenerateBoosts,
    val pacts: DemonicPacts,
)

class ResourcePactsTestScope(
    val scope: GameTestScope,
    val deps: ResourcePactsDeps,
    val recorder: ResourceRecorder,
)

private val testScripts: List<KClass<out PluginScript>> =
    listOf(
        ResourcePactsScript::class,
        DemonicPactsStateScript::class,
        NpcHitScript::class,
        PlayerHitScript::class,
        CombatModifierHitImpactScript::class,
        WeaponAttackStylesScript::class,
        WeaponAttackTypesScript::class,
        ComboRuneScript::class,
        CompactRuneScript::class,
        FakeRuneScript::class,
        StaffSubstituteScript::class,
        RuneSubstituteScript::class,
        UnlimitedRuneScript::class,
    )

fun GameTestState.resourcePactTest(
    activation: Module = PactsActiveForEveryone,
    vararg extraScripts: KClass<out PluginScript>,
    body: ResourcePactsTestScope.() -> Unit,
) {
    val recorder = ResourceRecorder()
    runInjectedGameTest(
        ResourcePactsDeps::class,
        Modules.combine(activation, ResourcePactsTestModule(recorder)),
        *(testScripts + extraScripts).toTypedArray(),
    ) { deps ->
        player.placeAt(RESOURCE_TEST_COORDS)
        setLevels("stat.magic", 99)
        setLevels("stat.ranged", 99)
        setLevels("stat.hitpoints", 99)
        setLevels("stat.defence", 99)
        setLevels("stat.prayer", 99)
        ResourcePactsTestScope(this, deps, recorder).body()
    }
}

val RESOURCE_TEST_COORDS: CoordGrid = CoordGrid(0, 50, 50, 22, 18)

const val TARGET_HP: Int = 250
const val SETTLE_TICKS: Int = 3

/** The Regenerate nodes that sum to at least 100%, so every Regenerate roll succeeds. */
val ALWAYS_REGENERATE: Array<String> = arrayOf("AA", "F3", "H2")

/** An accuracy roll that always hits, and one that always misses. */
const val HIT: Int = 0
const val MISS: Int = 9_999

val ResourcePactsTestScope.player: Player
    get() = scope.player

fun GameTestScope.setLevels(stat: String, level: Int) {
    player.setBaseLevel(stat, level)
    player.setCurrentLevel(stat, level)
}

/** Owns exactly [ids] with the admin `::pactset`, as the effect tests do. */
fun ResourcePactsTestScope.own(vararg ids: String) {
    scope.player.modLevel = Rights.ADMINISTRATOR
    with(scope) { player.cheat("pactset", *ids) }
}

fun ResourcePactsTestScope.setCurrentLevel(stat: String, level: Int) {
    with(scope) { player.setCurrentLevel(stat, level) }
}

fun ResourcePactsTestScope.setVarp(varp: String, value: Int) {
    with(scope) { player.setVarp(varp, value) }
}

fun ResourcePactsTestScope.queueRolls(vararg rolls: Int) {
    for (roll in rolls) {
        scope.random.then = roll
    }
}

fun objType(internal: String): ItemServerType =
    checkNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
        "Obj type not found: $internal"
    }

/** Spawns a man with [TARGET_HP] hitpoints [tiles] east of the player. */
fun ResourcePactsTestScope.target(tiles: Int = 1): Npc {
    val coords = RESOURCE_TEST_COORDS.translateX(tiles)
    scope.allocZoneCollision(coords)
    val npc = scope.spawnNpc(coords, "npc.man")
    npc.baseHitpointsLvl = TARGET_HP
    npc.hitpoints = TARGET_HP
    return npc
}

/** Spends the runes of [spell] outside of any attack, as a teleport or alchemy cast does. */
fun ResourcePactsTestScope.castOutsideCombat(spell: String) {
    val magicSpell = checkNotNull(deps.spells.getObjSpell(objType(spell))) { spell }
    val result = deps.runes.attemptCast(player, magicSpell)
    check(result.isSuccess()) { "Couldn't cast $spell: $result, ${scope.player.inv}" }
}

/**
 * Casts [spell] on [npc] the way a spell attack does inside `PvNCombat`'s attack scope: spends the
 * runes (Regenerate is decided here), rolls accuracy ([accuracy]), rolls damage on a hit, queues
 * the hit, then lets it land.
 *
 * Random rolls, in order: each rune stack's Regenerate roll (none at 100%) and the C1 rolls of
 * that stack, then the accuracy roll, then the damage roll on a hit.
 */
fun ResourcePactsTestScope.castOn(npc: Npc, spell: String, accuracy: Int = HIT): Int {
    val type = objType(spell)
    val magicSpell = checkNotNull(deps.spells.getObjSpell(type)) { spell }
    var damage = 0
    deps.pipeline.withAttack(player, npc, CombatStyle.Magic, type) {
        val result = deps.runes.attemptCast(player, magicSpell)
    check(result.isSuccess()) { "Couldn't cast $spell: $result, ${scope.player.inv}" }
        scope.random.next = accuracy
        val hit = deps.manager.rollSpellAccuracy(player, npc, type, Spellbook.Standard, false)
        if (hit) {
            damage =
                deps.manager.rollSpellMaxHit(
                    player,
                    npc,
                    type,
                    Spellbook.Standard,
                    BASE_MAX_HIT,
                    SPELL_ATTACK_RATE,
                    sunfireRune = false,
                )
        }
        deps.manager.queueMagicHit(player, npc, type, damage, clientDelay = 0, hitDelay = 1)
    }
    scope.advance(SETTLE_TICKS)
    return damage
}

/**
 * Spends the runes of [spell] inside a magic attack on [target] (an npc or a player), without
 * rolling a hit.
 */
fun ResourcePactsTestScope.spendRunesOn(target: PathingEntity, spell: String) {
    val type = objType(spell)
    val magicSpell = checkNotNull(deps.spells.getObjSpell(type)) { spell }
    deps.pipeline.withAttack(player, target, CombatStyle.Magic, type) {
        val result = deps.runes.attemptCast(player, magicSpell)
    check(result.isSuccess()) { "Couldn't cast $spell: $result, ${scope.player.inv}" }
    }
}

/** The summed modifiers of a magic attack with [spell] on [target]. */
fun ResourcePactsTestScope.magicModifiers(target: PathingEntity, spell: String): AttackModifiers {
    val context =
        AttackContext(player, target, CombatStyle.Magic, null, isSpecial = false, objType(spell))
    return deps.pipeline.attackModifiers(context)
}

const val BASE_MAX_HIT: Int = 8
const val SPELL_ATTACK_RATE: Int = 5
