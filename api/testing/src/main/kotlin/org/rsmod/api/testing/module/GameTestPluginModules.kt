package org.rsmod.api.testing.module

import com.google.inject.AbstractModule
import com.google.inject.Binding
import com.google.inject.Key
import com.google.inject.Module
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder
import com.google.inject.spi.Elements
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scan.PluginClasspathScan
import org.rsmod.plugin.scripts.PluginScript

private const val CONTENT_PACKAGE = "org.rsmod.content."

/**
 * Chooses which [PluginModule]s a game test installs.
 *
 * The server installs every plugin module on the classpath. A game test installs:
 * - every plugin module outside `org.rsmod.content`. These are api modules: they declare hook sets
 *   (`newSetBinding`) and bind the engine's managers, so the test injector matches the server's.
 * - the content plugin modules that own a script under test, i.e. whose package is the script's
 *   package or a parent of it. Testing `org.rsmod.content.skills.woodcutting.scripts.X` installs
 *   `org.rsmod.content.skills.woodcutting.WoodcuttingModule`, but not unrelated content hooks (drop
 *   tables, bone crushers, ...), which would change the behaviour under test or consume
 *   [org.rsmod.api.random.GameRandom] values.
 *
 * Some hook sets are only ever created by content modules contributing to them (`addSetBinding`),
 * with no api module declaring them. So that code injecting those sets still works, every set a
 * content module contributes to is declared, empty, in every test (see [emptyContentSets]).
 */
internal object GameTestPluginModules {
    private val all: List<Class<out PluginModule>> by lazy { scan() }

    private val contentSetKeys: List<Key<*>> by lazy { scanContentSets() }

    fun create(scripts: Collection<KClass<out PluginScript>>): List<Module> {
        val scriptPackages = scripts.map { it.java.packageName }
        val modules = all.filter { it.isApiModule() || it.ownsAny(scriptPackages) }
        return modules.map(::instantiate) + emptyContentSets()
    }

    private fun emptyContentSets(): Module =
        object : AbstractModule() {
            override fun configure() {
                for (key in contentSetKeys) {
                    declareSet(key)
                }
            }

            private fun declareSet(setKey: Key<*>) {
                val type = setKey.typeLiteral.type as ParameterizedType
                val element = TypeLiteral.get(type.actualTypeArguments.single())
                val annotation = setKey.annotation
                val annotationType = setKey.annotationType
                when {
                    annotation != null -> Multibinder.newSetBinder(binder(), element, annotation)
                    annotationType != null ->
                        Multibinder.newSetBinder(binder(), element, annotationType)
                    else -> Multibinder.newSetBinder(binder(), element)
                }
            }
        }

    private fun scan(): List<Class<out PluginModule>> =
        PluginClasspathScan.scan
            .getSubclasses(PluginModule::class.java)
            .directOnly()
            .map { it.loadClass(PluginModule::class.java) }
            .filterNot { Modifier.isAbstract(it.modifiers) }
            .sortedBy { it.name }

    private fun scanContentSets(): List<Key<*>> {
        val modules = all.filterNot { it.isApiModule() }.map(::instantiate)
        return Elements.getElements(modules)
            .filterIsInstance<Binding<*>>()
            .map { it.key }
            .filter { it.isConcreteSetKey() }
            .distinct()
    }

    /** A `Set<T>` key for a concrete `T`; multibinders also bind `Set<? extends T>` themselves. */
    private fun Key<*>.isConcreteSetKey(): Boolean {
        val type = typeLiteral.type as? ParameterizedType ?: return false
        if (type.rawType != Set::class.java) {
            return false
        }
        val element = type.actualTypeArguments.single()
        return element is Class<*> || element is ParameterizedType
    }

    private fun Class<*>.isApiModule(): Boolean = !packageName.startsWith(CONTENT_PACKAGE)

    private fun Class<*>.ownsAny(scriptPackages: Collection<String>): Boolean =
        scriptPackages.any { it == packageName || it.startsWith("$packageName.") }

    private fun instantiate(type: Class<out PluginModule>): PluginModule {
        val constructor = type.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }
}
