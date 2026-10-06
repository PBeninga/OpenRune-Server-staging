package org.rsmod.content.leagues.demonicpacts

import org.rsmod.content.leagues.demonicpacts.state.FreePactResets
import org.rsmod.content.leagues.demonicpacts.state.GrantedPactPoints
import org.rsmod.content.leagues.demonicpacts.state.GrantedPactResets
import org.rsmod.content.leagues.demonicpacts.state.PactCommitListener
import org.rsmod.content.leagues.demonicpacts.state.PactEffectsListener
import org.rsmod.content.leagues.demonicpacts.state.PactPointSource
import org.rsmod.content.leagues.demonicpacts.state.PactResetListener
import org.rsmod.content.leagues.demonicpacts.state.PactResetSource
import org.rsmod.content.leagues.demonicpacts.state.StartingPactPoints
import org.rsmod.plugin.module.PluginModule

/**
 * Declares the Demonic Pacts extension points and binds their defaults. A server changes the rules
 * from its own module: it adds [PactPointSource]s and [PactResetSource]s with `addSetBinding`,
 * listens with [PactCommitListener]/[PactResetListener]/[PactEffectsListener], and replaces the
 * single-answer hooks (`PactSpendCap`, `PactResetGuard`, `PactEffectValues`, `PactSettings`) with an
 * explicit `bind`, which wins over their `@ImplementedBy`/`@ProvidedBy` defaults.
 */
class DemonicPactsModule : PluginModule() {
    override fun bind() {
        newSetBinding<PactCommitListener>()
        newSetBinding<PactResetListener>()
        newSetBinding<PactEffectsListener>()
        addSetBinding<PactPointSource>(StartingPactPoints::class.java)
        addSetBinding<PactPointSource>(GrantedPactPoints::class.java)
        addSetBinding<PactResetSource>(FreePactResets::class.java)
        addSetBinding<PactResetSource>(GrantedPactResets::class.java)
    }
}
