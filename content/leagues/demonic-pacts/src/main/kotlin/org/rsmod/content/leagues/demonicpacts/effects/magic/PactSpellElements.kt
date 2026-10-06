package org.rsmod.content.leagues.demonicpacts.effects.magic

import dev.openrune.types.ItemServerType
import org.rsmod.api.combat.commons.magic.SpellElement
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.BLOOD_AS_FIRE
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.ICE_AS_WATER
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.SHADOW_AS_EARTH
import org.rsmod.content.leagues.demonicpacts.effects.magic.MagicPactEffects.SMOKE_AS_AIR
import org.rsmod.content.leagues.demonicpacts.state.PactEffects

/**
 * The element a spell counts as for the magic pacts: a standard elemental spell is its own
 * element, and an Ancient Magicks spell counts as its standard element only while L5 (smoke), L6
 * (ice), L7 (blood) or L8 (shadow) is owned.
 */
object PactSpellElements {
    private val ancientNodes: Map<SpellElement, Int> =
        mapOf(
            SpellElement.Smoke to SMOKE_AS_AIR,
            SpellElement.Ice to ICE_AS_WATER,
            SpellElement.Blood to BLOOD_AS_FIRE,
            SpellElement.Shadow to SHADOW_AS_EARTH,
        )

    /** The standard element [spell] counts as with [effects], or `null` if none. */
    fun of(spell: ItemServerType?, effects: PactEffects): SpellElement? {
        val element = spell?.let(SpellElement::of) ?: return null
        if (element.isStandard) {
            return element
        }
        val node = ancientNodes.getValue(element)
        return if (node in effects) element.base else null
    }
}
