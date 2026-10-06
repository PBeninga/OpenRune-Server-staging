package org.rsmod.api.combat.commons.magic

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params

/**
 * The element of a combat spell: the four standard elements, and the four Ancient Magicks
 * elements, each of which has a standard [base] element (smoke is air, ice is water, blood is fire
 * and shadow is earth).
 *
 * Only the standard elements interact with an npc's elemental weakness ([ElementalWeakness]).
 */
public enum class SpellElement(baseElement: SpellElement?) {
    Air(null),
    Water(null),
    Earth(null),
    Fire(null),
    Smoke(Air),
    Ice(Water),
    Blood(Fire),
    Shadow(Earth);

    /** The standard element this element counts as: itself for the standard elements. */
    public val base: SpellElement = baseElement ?: this

    public val isStandard: Boolean
        get() = base == this

    public val isAncient: Boolean
        get() = !isStandard

    public companion object {
        private val standard: Map<String, SpellElement> =
            mapOf(
                "obj.01_wind_strike" to Air,
                "obj.17_wind_bolt" to Air,
                "obj.41_wind_blast" to Air,
                "obj.62_wind_wave" to Air,
                "obj.81_wind_surge" to Air,
                "obj.05_water_strike" to Water,
                "obj.23_water_bolt" to Water,
                "obj.47_water_blast" to Water,
                "obj.65_water_wave" to Water,
                "obj.85_water_surge" to Water,
                "obj.09_earth_strike" to Earth,
                "obj.29_earth_bolt" to Earth,
                "obj.53_earth_blast" to Earth,
                "obj.70_earth_wave" to Earth,
                "obj.90_earth_surge" to Earth,
                "obj.13_fire_strike" to Fire,
                "obj.35_fire_bolt" to Fire,
                "obj.59_fire_blast" to Fire,
                "obj.75_fire_wave" to Fire,
                "obj.95_fire_surge" to Fire,
            )

        private val ancient: Map<String, SpellElement> =
            mapOf(
                "obj.50_smoke_rush" to Smoke,
                "obj.62_smoke_burst" to Smoke,
                "obj.74_smoke_blitz" to Smoke,
                "obj.86_smoke_barrage" to Smoke,
                "obj.52_shadow_rush" to Shadow,
                "obj.64_shadow_burst" to Shadow,
                "obj.76_shadow_blitz" to Shadow,
                "obj.88_shadow_barrage" to Shadow,
                "obj.56_blood_rush" to Blood,
                "obj.68_blood_burst" to Blood,
                "obj.80_blood_blitz" to Blood,
                "obj.92_blood_barrage" to Blood,
                "obj.58_ice_rush" to Ice,
                "obj.70_ice_burst" to Ice,
                "obj.82_ice_blitz" to Ice,
                "obj.94_ice_barrage" to Ice,
            )

        private val byObjId: Map<Int, SpellElement> by lazy {
            (standard + ancient).mapKeys { (name, _) -> name.asRSCM(RSCMType.OBJ) }
        }

        /** The spell objs of every elemental combat spell, by internal name. */
        public val spells: Map<String, SpellElement> = standard + ancient

        /**
         * Returns the element of [spell] (a spell obj), or `null` for spells without one, such as
         * Magic Dart, the god spells, Crumble Undead, Iban Blast and the binding spells.
         */
        public fun of(spell: ItemServerType): SpellElement? = byObjId[spell.id]

        /** Returns the element of [spell], or `null` for spells without one. */
        public fun of(spell: MagicSpell): SpellElement? = of(spell.obj)
    }
}

/**
 * An npc's weakness to one of the standard [SpellElement]s, read from its `elemental_weakness_type`
 * and `elemental_weakness_percent` params.
 *
 * @property percent The weakness, in percent (`10` = `10%`).
 */
public data class ElementalWeakness(val element: SpellElement, val percent: Int) {
    public companion object {
        /** Returns the elemental weakness of [type], or `null` if it has none. */
        public fun of(type: NpcServerType): ElementalWeakness? {
            val element =
                when (type.paramOrNull(params.elemental_weakness_type)) {
                    constants.elemental_weakness_wind -> SpellElement.Air
                    constants.elemental_weakness_water -> SpellElement.Water
                    constants.elemental_weakness_earth -> SpellElement.Earth
                    constants.elemental_weakness_fire -> SpellElement.Fire
                    else -> return null
                }
            val percent = type.paramOrNull(params.elemental_weakness_percent) ?: 0
            return ElementalWeakness(element, percent)
        }

        /**
         * Returns `true` if [type] is weak to [element]. Ancient elements only count as their
         * [SpellElement.base] when [ancientAsBase] is set, as some effects make them do.
         */
        public fun isWeakTo(
            type: NpcServerType,
            element: SpellElement,
            ancientAsBase: Boolean = false,
        ): Boolean {
            val weakness = of(type) ?: return false
            val resolved = if (ancientAsBase) element.base else element
            return weakness.element == resolved
        }
    }
}
