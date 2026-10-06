package org.rsmod.content.leagues.demonicpacts

import com.google.inject.AbstractModule
import kotlin.reflect.KClass
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.leagues.demonicpacts.state.PactActivation
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript

/** Makes every player's pacts active, as `enabled: true` in `game.yml` does. */
object PactsActiveForEveryone : AbstractModule() {
    override fun configure() {
        bind(PactActivation::class.java).toInstance(PactActivation { true })
    }
}

/** Makes pacts active only for [players], as a world hosting several rulesets would. */
class PactsActiveFor(val players: MutableList<Player> = mutableListOf()) : AbstractModule() {
    override fun configure() {
        val activation = PactActivation { player -> players.any { it === player } }
        bind(PactActivation::class.java).toInstance(activation)
    }
}

/** Makes every player's pacts active while [active] is true, for a ruleset switched on and off. */
class PactsActiveWhile(var active: Boolean = true) : AbstractModule() {
    override fun configure() {
        bind(PactActivation::class.java).toInstance(PactActivation { active })
    }
}

/** [GameTestState.runGameTest] with every player's pacts active ([PactsActiveForEveryone]). */
fun GameTestState.runActivePactTest(
    vararg scripts: KClass<out PluginScript>,
    testBody: GameTestScope.() -> Unit,
): Unit =
    runInjectedGameTest(PactActivation::class, PactsActiveForEveryone, *scripts) { testBody() }
