package org.rsmod.content.leagues.demonicpacts.effects.resources

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.combat.commons.magic.SpellElement

/**
 * The elements a rune counts as for the C1–C4 pacts: the four elemental runes, sunfire runes as
 * fire, and each combination rune as both of its elements (it pays for both in a spell).
 */
object PactRuneElements {
    private val elementsByRune: Map<String, Set<SpellElement>> =
        mapOf(
            "obj.airrune" to setOf(SpellElement.Air),
            "obj.waterrune" to setOf(SpellElement.Water),
            "obj.earthrune" to setOf(SpellElement.Earth),
            "obj.firerune" to setOf(SpellElement.Fire),
            "obj.sunfirerune" to setOf(SpellElement.Fire),
            "obj.mistrune" to setOf(SpellElement.Air, SpellElement.Water),
            "obj.dustrune" to setOf(SpellElement.Air, SpellElement.Earth),
            "obj.smokerune" to setOf(SpellElement.Air, SpellElement.Fire),
            "obj.mudrune" to setOf(SpellElement.Water, SpellElement.Earth),
            "obj.steamrune" to setOf(SpellElement.Water, SpellElement.Fire),
            "obj.lavarune" to setOf(SpellElement.Earth, SpellElement.Fire),
        )

    private val elementsById: Map<Int, Set<SpellElement>> by lazy {
        elementsByRune.mapKeys { it.key.asRSCM(RSCMType.OBJ) }
    }

    /** The elemental rune of each standard element, as the F5, F6, F9 and F10 pacts add them. */
    val elementalRunes: Map<SpellElement, String> =
        mapOf(
            SpellElement.Air to "obj.airrune",
            SpellElement.Water to "obj.waterrune",
            SpellElement.Earth to "obj.earthrune",
            SpellElement.Fire to "obj.firerune",
        )

    /** The elements [rune] counts as; empty for non-elemental runes and unknown objs. */
    fun of(rune: ItemServerType?): Set<SpellElement> =
        rune?.let { elementsById[it.id] } ?: emptySet()
}
