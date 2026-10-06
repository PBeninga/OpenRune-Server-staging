package org.rsmod.api.combat.modifiers

import com.google.inject.Guice
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.rsmod.api.core.module.EntityHitModule
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.random.RandomModule

/** [CombatModifiersModule] decorates OpenRune's `PlayerHitModifier` binding (`EntityHitModule`). */
class PlayerHitModifierBindingTest {
    @Test
    fun `without the combat modifiers module the standard modifier is bound`() {
        val injector = Guice.createInjector(EntityHitModule, RandomModule)

        val modifier = injector.getInstance(PlayerHitModifier::class.java)

        assertInstanceOf(StandardPlayerHitModifier::class.java, modifier)
    }

    @Test
    fun `the combat modifiers module decorates the binding with a singleton`() {
        val injector = Guice.createInjector(EntityHitModule, RandomModule, CombatModifiersModule())

        val modifier = injector.getInstance(PlayerHitModifier::class.java)

        assertInstanceOf(CombatModifierPlayerHitModifier::class.java, modifier)
        assertSame(modifier, injector.getInstance(PlayerHitModifier::class.java))
    }
}
