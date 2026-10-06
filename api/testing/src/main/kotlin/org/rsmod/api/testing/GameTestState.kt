package org.rsmod.api.testing

import com.github.michaelbull.logging.InlineLogger
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.util.Modules
import dev.openrune.ServerCacheManager
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.GameMapSpawnSink
import dev.openrune.map.MapSingletons
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.obj.MapObjDefinition
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.time.measureTime
import org.junit.jupiter.api.extension.ExtensionContext
import org.rsmod.api.game.process.PluginScriptBootGate
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastFactory
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.api.testing.factory.TestCacheTypes
import org.rsmod.api.testing.factory.collisionFactory
import org.rsmod.api.testing.module.GameTestOverrideModule
import org.rsmod.api.testing.module.GameTestPluginModules
import org.rsmod.api.testing.scope.AdvancedGameTestScope
import org.rsmod.api.testing.scope.AdvancedReadOnly
import org.rsmod.api.testing.scope.BasicGameTestScope
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.testing.util.TestServerConfig
import org.rsmod.events.EventBus
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.server.app.modules.GameModule
import org.rsmod.server.app.modules.ParserModule
import org.rsmod.server.shared.loader.PluginModuleLoader
import org.rsmod.server.shared.loader.PluginScriptLoader

public class GameTestState {
    /** The game map's collision, as loaded from the cache. Treat it as read-only. */
    public val collision: CollisionFlagMap
        get() = MapSingletons.collision

    public val routeFactory: RouteFactory by lazy { RouteFactory(collision) }
    public val rayCastFactory: RayCastFactory by lazy { RayCastFactory(collision) }
    public val stepFactory: StepFactory by lazy { StepFactory(collision) }
    public val rayCastValidator: RayCastValidator by lazy { RayCastValidator(collision) }
    public val boundValidator: BoundValidator by lazy { BoundValidator(collision) }

    /**
     * The event bus of the shared world: every plugin module and plugin script on the classpath,
     * booted once per test JVM as the server boots. Used by [runBasicGameTest].
     *
     * Booting it also runs every script's `startup`, which is where some scripts set process-wide
     * state that the per-test worlds rely on (for example `InvTransactionsScript`), as in rsmod's
     * harness.
     *
     * Treat it as read-only.
     */
    public val eventBus: EventBus by lazy { sharedWorld.getInstance(EventBus::class.java) }

    private val sharedWorld: Injector by lazy { createSharedWorld() }
    private val readOnly: AdvancedReadOnly by lazy {
        sharedWorld.getInstance(AdvancedReadOnly::class.java)
    }

    private val serverConfig = TestServerConfig.create()
    private val logger = InlineLogger()

    /**
     * Runs a game test with optional isolated script contexts.
     *
     * If one or more [scripts] are provided, the test runs with isolated [ScriptContext]s that bind
     * events only for the specified scripts. If no scripts are provided, the test runs **without
     * any plugin-specific events**, ensuring a clean execution environment.
     *
     * Each test gets its own injector: the server's module graph, api plugin modules, the content
     * plugin modules that own [scripts], and test overrides (deterministic random, test realm, no
     * database). See [GameTestScope.Builder].
     *
     * ### Why This Matters
     * Isolating scripts prevents unintended interactions from unrelated scripts. For example,
     * suppose you're testing a woodcutting script that relies on a preset [GameTestScope.random]
     * sequence. Without isolation, an unrelated script, such as one granting a random reward after
     * 30 seconds, might consume the random value before the woodcutting script executes.
     *
     * To prevent such interference, we explicitly specify the relevant script:
     * ```
     * runGameTest(WoodcuttingScript::class) { ... }
     * ```
     *
     * @param scripts The [PluginScript] classes relevant to the test scope. If specified, only the
     *   events for these scripts will be loaded. Otherwise, no plugin events are registered.
     * @see [GameTestScope]
     */
    public fun runGameTest(
        vararg scripts: KClass<out PluginScript>,
        scope: GameTestScope = GameTestScope.Builder(this, scripts.toSet()).build(),
        testBody: GameTestScope.() -> Unit,
    ): Unit = testBody(scope)

    @Suppress("konsist.test see tag references are formatted correctly")
    /**
     * Runs a game test with optional isolated script contexts and an injected dependency.
     *
     * This function allows for injecting a **single test-specific dependency**, while also
     * providing script isolation similar to the simpler [runGameTest]. The injected dependency acts
     * as a **wrapper** around one or more required dependencies, avoiding the need for multiple
     * dependency parameters.
     *
     * [childModule] **overrides** the test injector's bindings (it is applied with
     * `Modules.override`), so it can replace any binding, including ones from plugin modules such
     * as a registry bound with `bindInstance`, and add elements to multibinder sets.
     *
     * **Example Usage:**
     *
     * ```
     * object MeleeAccuracyTestModule : AbstractModule() {
     *   override fun configure() {
     *      bind(PvNMeleeAccuracy::class.java).`in`(Scopes.SINGLETON)
     *   }
     * }
     * ```
     * ```
     * class MeleeAccuracyTestDependencies @Inject constructor(val accuracy: PvNMeleeAccuracy)
     * ```
     * ```
     * runInjectedGameTest(
     *  // Wrapper to be injected that contains sub-dependencies.
     *  MeleeAccuracyTestDependencies::class,
     *  // Optional module with test-specific (or overriding) bindings.
     *  childModule = MeleeAccuracyTestModule,
     * ) { deps -> // `deps` is the injected `MeleeAccuracyTestDependencies`.
     *  val accuracy = deps.accuracy
     *  val npc = npcFactory.create(...)
     *  val hitChance = accuracy.getHitChance(player, npc, ...)
     *  assertEquals(5000, hitChance)
     * }
     * ```
     *
     * @param dependency The class type of the **dependency wrapper** to be injected.
     * @param childModule An optional [Module] that adds or overrides test-specific bindings.
     * @param scripts The [PluginScript] classes relevant to the test scope. If specified, only the
     *   events for these scripts will be loaded; otherwise, no plugin events are registered.
     * @see [GameTestScope]
     */
    public fun <T : Any> runInjectedGameTest(
        dependency: KClass<T>,
        childModule: Module? = null,
        vararg scripts: KClass<out PluginScript>,
        testBody: GameTestScope.(dependency: T) -> Unit,
    ) {
        val overrides = listOfNotNull(childModule)
        val injector = GameTestScope.Builder(this, scripts.toSet()).buildInjector(overrides)
        val scope = injector.getInstance(GameTestScope::class.java)
        val injectedDependency = injector.getInstance(dependency.java)
        testBody(scope, injectedDependency)
    }

    /**
     * Runs a loosely-coupled test using the [BasicGameTestScope], which provides basic properties
     * and explicit control over the systems being tested. This is useful for test cases that
     * require minimal dependencies or where the focus is on isolated components rather than the
     * full game system.
     *
     * For most scenarios, prefer using [runGameTest], which offers a more integrated scope with all
     * game systems operating as they would in a live environment.
     */
    public fun runBasicGameTest(
        scope: BasicGameTestScope = BasicGameTestScope(eventBus),
        testBody: BasicGameTestScope.() -> Unit,
    ): Unit = testBody(scope)

    /**
     * Executes a game test with higher-level privileged access.
     *
     * This function enables running integration tests with both standard and advanced testing
     * scopes. The [AdvancedGameTestScope] grants additional capabilities and resources, offering
     * greater flexibility and control over the testing environment. However, using this scope
     * requires careful handling to maintain test integrity.
     *
     * **Note**: **By default**, use the standard [runBasicGameTest] function. The
     * [runAdvancedGameTest] function should only be used when absolutely necessary and no other
     * workaround is possible.
     *
     * @see [AdvancedGameTestScope]
     * @see [runBasicGameTest]
     */
    public fun runAdvancedGameTest(
        standardScope: BasicGameTestScope = BasicGameTestScope(eventBus),
        advancedScope: AdvancedGameTestScope = AdvancedGameTestScope(readOnly),
        testBody: BasicGameTestScope.(AdvancedGameTestScope) -> Unit,
    ): Unit = testBody(standardScope, advancedScope)

    /**
     * Creates the injector for one game test. [scripts] selects the content plugin modules to
     * install (see [GameTestPluginModules]) and [overrides] are applied last.
     */
    internal fun createTestInjector(
        scripts: Collection<KClass<out PluginScript>>,
        overrides: List<Module>,
    ): Injector {
        val collision = collisionFactory.borrowSharedMap()
        // Copy each zone: sharing the arrays let a test's locs leak into the map of later tests.
        val mapFlags = MapSingletons.collision.flags
        for (zone in mapFlags.indices) {
            collision.flags[zone] = mapFlags[zone]?.copyOf()
        }
        val testOverrides = GameTestOverrideModule(collision, serverConfig)
        val modules = serverModules() + GameTestPluginModules.create(scripts)
        val injector = Guice.createInjector(overrideAll(modules, testOverrides, overrides))
        injector.getInstance(PluginScriptBootGate::class.java).markReady()
        return injector
    }

    internal fun initialize() {
        logger.info { "Setting up game-test state..." }
        val duration = measureTime {
            val cache = ServerCacheManager.init(serverConfig.revision)
            try {
                GameMapDecoder.decodeAll(IgnoreMapSpawns, cache)
            } finally {
                cache.close()
            }
            check(sharedWorld.getInstance(PluginScriptBootGate::class.java).isReady())
            if (System.getProperty(SYNTHETIC_TYPES_PROPERTY).toBoolean()) {
                TestCacheTypes.install()
            }
        }
        logger.info { "Set up game-test state in $duration." }
    }

    internal fun register(ctx: ExtensionContext) {
        logger.debug { "Register test: ${ctx.testClass.getOrNull()}" }
    }

    internal fun unregister(ctx: ExtensionContext) {
        logger.debug { "Unregister test: ${ctx.testClass.getOrNull()}" }
    }

    internal fun finalize() {
        logger.info { "Finalizing game-test state..." }
    }

    private fun createSharedWorld(): Injector {
        logger.info { "Booting the shared game-test world (all plugin modules and scripts)..." }
        val modules = serverModules() + PluginModuleLoader.load(PluginModule::class.java)
        val overrides = GameTestOverrideModule(collision = null, serverConfig = serverConfig)
        val injector = Guice.createInjector(overrideAll(modules, overrides, emptyList()))
        val scripts = PluginScriptLoader().load(PluginScript::class.java, injector)
        val context = injector.getInstance(ScriptContext::class.java)
        for (script in scripts) {
            with(script) { context.startup() }
        }
        injector.getInstance(PluginScriptBootGate::class.java).markReady()
        return injector
    }

    private fun serverModules(): List<Module> = listOf(GameModule, ParserModule)

    private fun overrideAll(
        modules: List<Module>,
        testOverrides: Module,
        overrides: List<Module>,
    ): Module {
        val withTestOverrides = Modules.override(modules).with(testOverrides)
        return if (overrides.isEmpty()) {
            withTestOverrides
        } else {
            Modules.override(withTestOverrides).with(overrides)
        }
    }

    private object IgnoreMapSpawns : GameMapSpawnSink {
        override fun onNpcSpawn(def: MapNpcDefinition, coords: CoordGrid) {}

        override fun onObjSpawn(def: MapObjDefinition, coords: CoordGrid) {}
    }

    public companion object {
        /**
         * Set to `true` (as a test task system property) in modules whose tests register synthetic
         * cache types ([TestCacheTypes]), so the cache lookups are stubbed before any test runs.
         * The stub is left out elsewhere: concurrent tests that advance game ticks through a
         * stubbed `ServerCacheManager` fail intermittently with MockK's "can't find stub".
         */
        public const val SYNTHETIC_TYPES_PROPERTY: String = "rsmod.testing.synthetic-types"
    }
}
