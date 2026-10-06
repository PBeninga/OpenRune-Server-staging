package org.rsmod.api.combat.modifiers

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.constants
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.spells.MagicSpellModule
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.combo.ComboRuneScript
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneScript
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneScript
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteScript
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteScript
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript

/** The combat scripts are `internal`, so they are loaded by name. */
object CombatTestScripts {
    val pvnCombat: KClass<out PluginScript> = script("org.rsmod.api.combat.scripts.PvNCombatScript")
    val nvpCombat: KClass<out PluginScript> = script("org.rsmod.api.combat.scripts.NvPCombatScript")
    val pvpCombat: KClass<out PluginScript> = script("org.rsmod.api.combat.scripts.PvPCombatScript")

    /** Scripts every combat modifier test needs: hit processing, weapon data and runes. */
    val base: List<KClass<out PluginScript>> =
        listOf(
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

    private fun script(name: String): KClass<out PluginScript> =
        Class.forName(name).asSubclass(PluginScript::class.java).kotlin
}

/**
 * Binds the modifier registry used by a test, plus the singletons that production binds in `internal`
 * plugin modules (weapon styles and types, magic spells and rune repositories).
 */
class ModifierTestModule(private val registry: CombatModifierRegistry) : AbstractModule() {
    override fun configure() {
        bind(CombatModifierRegistry::class.java).toInstance(registry)
        install(MagicSpellModule())
        bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        bind(ComboRuneRepository::class.java).`in`(Scopes.SINGLETON)
        bind(CompactRuneRepository::class.java).`in`(Scopes.SINGLETON)
        bind(FakeRuneRepository::class.java).`in`(Scopes.SINGLETON)
        bind(RuneSubstituteRepository::class.java).`in`(Scopes.SINGLETON)
        bind(StaffSubstituteRepository::class.java).`in`(Scopes.SINGLETON)
        bind(UnlimitedRuneRepository::class.java).`in`(Scopes.SINGLETON)
    }
}

/** Everything a combat modifier test needs from the injector. */
class ModifierTestDeps
@Inject
constructor(
    val pipeline: CombatModifierPipeline,
    val manager: PlayerAttackManager,
    val ammo: RangedAmmoManager,
    val runes: MagicRuneManager,
    val charges: CombatChargeManager,
    val accuracy: AccuracyFormulae,
    val maxHits: MaxHitFormulae,
    val spells: MagicSpellRegistry,
    val compactRunes: CompactRuneRepository,
    /** OpenRune's `PlayerHitModifier` binding, decorated by [CombatModifierPlayerHitModifier]. */
    val playerHitModifier: PlayerHitModifier,
)

/** A provider whose modifiers a test can change at any time. Records every context it sees. */
class TestModifierProvider : CombatModifierProvider {
    var attack: AttackModifiers = AttackModifiers.NONE
    var defence: DefenceModifiers = DefenceModifiers.NONE
    var resource: ResourceModifiers = ResourceModifiers.NONE
    val attackContexts: MutableList<AttackContext> = mutableListOf()
    val defenceContexts: MutableList<DefenceContext> = mutableListOf()

    override fun attackModifiers(context: AttackContext): AttackModifiers {
        attackContexts += context
        return attack
    }

    override fun defenceModifiers(context: DefenceContext): DefenceModifiers {
        defenceContexts += context
        return defence
    }

    override fun resourceModifiers(context: ResourceContext): ResourceModifiers = resource
}

/** A listener whose answers a test can change at any time. Records every event it sees. */
class TestProcListener : CombatProcListener {
    var extraHits: (HitRolledEvent) -> List<ExtraHit> = { emptyList() }
    var damageResponse: (DamageReceivedEvent) -> DamageReceivedResponse = {
        DamageReceivedResponse.NONE
    }
    var resourceDecision: (ResourceConsumedEvent) -> ResourceDecision = { ResourceDecision.Consume }
    val rolled: MutableList<HitRolledEvent> = mutableListOf()
    val dealt: MutableList<HitDealtEvent> = mutableListOf()
    val received: MutableList<DamageReceivedEvent> = mutableListOf()
    val resources: MutableList<ResourceConsumedEvent> = mutableListOf()

    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        rolled += event
        return extraHits(event)
    }

    override fun onHitDealt(event: HitDealtEvent) {
        dealt += event
    }

    override fun onDamageReceived(event: DamageReceivedEvent): DamageReceivedResponse {
        received += event
        return damageResponse(event)
    }

    override fun onResourceConsumed(event: ResourceConsumedEvent): ResourceDecision {
        resources += event
        return resourceDecision(event)
    }
}

/** Npc rules whose protection and cap a test can change at any time. */
class TestNpcRules : NpcCombatRules {
    var protection: Double = 0.0
    var cap: Int? = null

    override fun protectionPercent(context: NpcDamageContext): Double = protection

    override fun damageCap(context: NpcDamageContext): Int? = cap
}

/** The test provider, listener and rules, all registered together. */
class TestHooks {
    val provider: TestModifierProvider = TestModifierProvider()
    val listener: TestProcListener = TestProcListener()
    val rules: TestNpcRules = TestNpcRules()
    val registry: CombatModifierRegistry =
        CombatModifierRegistry.of(listOf(provider), listOf(listener), listOf(rules))
}

/**
 * Runs a game test with [registry] as the modifier registry, the [CombatTestScripts.base] scripts and
 * any extra [scripts].
 */
fun GameTestState.runModifierTest(
    registry: CombatModifierRegistry,
    vararg scripts: KClass<out PluginScript>,
    testBody: GameTestScope.(ModifierTestDeps) -> Unit,
) {
    val allScripts = (CombatTestScripts.base + scripts).toTypedArray()
    runInjectedGameTest(
        ModifierTestDeps::class,
        ModifierTestModule(registry),
        *allScripts,
        testBody = testBody,
    )
}

/** A spot in Lumbridge with free tiles around it. */
val TEST_COORDS: CoordGrid = CoordGrid(0, 50, 50, 22, 18)

/** Looks up an obj type by its RSCM name, for example `"obj.bronze_arrow"`. */
fun objType(internal: String): ItemServerType =
    checkNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
        "Obj type not found: $internal"
    }

/** Sets both the base and the current level of [stat] (an RSCM name such as `"stat.attack"`). */
fun GameTestScope.setLevel(stat: String, level: Int) {
    player.setBaseLevel(stat, level)
    player.setCurrentLevel(stat, level)
}

/**
 * Advances past the "active combat" window that starts at map clock 0 in tests, during which the
 * combat scripts treat the player as already under attack.
 */
fun GameTestScope.passCombatGracePeriod() {
    advance(constants.combat_activecombat_delay + 2)
}

/** Spawns a man next to the player with [hitpoints] hitpoints. */
fun GameTestScope.spawnTarget(
    hitpoints: Int = 250,
    coords: CoordGrid = TEST_COORDS.translateX(1),
): Npc {
    allocZoneCollision(coords)
    val npc = spawnNpc(coords, "npc.man")
    npc.baseHitpointsLvl = hitpoints
    npc.hitpoints = hitpoints
    return npc
}

/**
 * An effect that only the players in [owners] have: a chain cap and a Regenerate chance from the
 * provider, extra hits and rune refunds from the listener. Records every event it sees.
 */
class PlayerEffect : CombatModifierProvider, CombatProcListener {
    val owners: MutableSet<Player> = mutableSetOf()
    var attack: AttackModifiers = AttackModifiers.NONE
    var resource: ResourceModifiers = ResourceModifiers.NONE
    var extraHits: (HitRolledEvent) -> List<ExtraHit> = { emptyList() }
    var refund: (ResourceConsumedEvent) -> Boolean = { false }
    val rolled: MutableList<HitRolledEvent> = mutableListOf()
    val dealt: MutableList<HitDealtEvent> = mutableListOf()
    val resources: MutableList<ResourceConsumedEvent> = mutableListOf()

    override fun attackModifiers(context: AttackContext): AttackModifiers =
        if (context.attacker in owners) attack else AttackModifiers.NONE

    override fun resourceModifiers(context: ResourceContext): ResourceModifiers =
        if (context.player in owners) resource else ResourceModifiers.NONE

    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        rolled += event
        return if (event.context.attacker in owners) extraHits(event) else emptyList()
    }

    override fun onHitDealt(event: HitDealtEvent) {
        dealt += event
    }

    override fun onResourceConsumed(event: ResourceConsumedEvent): ResourceDecision {
        resources += event
        val refunded = event.context.player in owners && refund(event)
        return if (refunded) ResourceDecision.Refund else ResourceDecision.Consume
    }

    fun registry(): CombatModifierRegistry =
        CombatModifierRegistry.of(providers = listOf(this), listeners = listOf(this))
}

fun GameTestScope.setLevel(player: Player, stat: String, level: Int) {
    player.setBaseLevel(stat, level)
    player.setCurrentLevel(stat, level)
}
