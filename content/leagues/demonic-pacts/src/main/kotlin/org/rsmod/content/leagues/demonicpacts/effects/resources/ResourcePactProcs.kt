package org.rsmod.content.leagues.demonicpacts.effects.resources

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.combat.commons.magic.SpellElement
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.modifiers.AttackScope
import org.rsmod.api.combat.modifiers.CombatModifierPipeline
import org.rsmod.api.combat.modifiers.CombatProcListener
import org.rsmod.api.combat.modifiers.CombatStyle
import org.rsmod.api.combat.modifiers.ExtraHit
import org.rsmod.api.combat.modifiers.HitRolledEvent
import org.rsmod.api.combat.modifiers.HitSource
import org.rsmod.api.combat.modifiers.ResourceConsumedEvent
import org.rsmod.api.combat.modifiers.ResourceDecision
import org.rsmod.api.combat.modifiers.ResourceKind
import org.rsmod.api.combat.weapon.WeaponClass
import org.rsmod.api.combat.weapon.WeaponClasses
import org.rsmod.api.player.stat.PlayerHealSource
import org.rsmod.api.player.stat.PlayerHealing
import org.rsmod.api.player.stat.baseDefenceLvl
import org.rsmod.api.player.stat.statHeal
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.AIR_RUNE_PRAYER
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.DEFENCE_BOOST_CAP_PERCENT
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.EARTH_RUNE_DEFENCE
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.FIRE_RUNE_DAMAGE
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.MAGIC_BOOST_PER_TRIGGER
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.REGENERATE_MAGIC_BOOST
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.STAFF_AIR_RUNE
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.STAFF_EARTH_RUNE
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.STAFF_FIRE_RUNE
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.STAFF_WATER_RUNE
import org.rsmod.content.leagues.demonicpacts.effects.resources.ResourcePactEffects.WATER_RUNE_HEAL
import org.rsmod.content.leagues.demonicpacts.state.DemonicPacts
import org.rsmod.content.leagues.demonicpacts.state.PactEffects
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * The pacts that react to a Regenerate ([ResourceConsumedEvent.regenerated]).
 *
 * Runes a **combat spell** Regenerates (a rune spent inside a magic attack):
 * - **B1:** Magic is boosted by 1 for 30 ticks, at most +10; once per cast, however many rune
 *   stacks were Regenerated.
 * - **C1:** each air rune has a 15% chance to restore 1 Prayer point.
 * - **C2:** each water rune heals 1, as pact healing (it can overheal with F2, F13 or H9).
 * - **C3:** each fire rune adds 1 damage to the next magic hit that lands
 *   ([ResourcePactModifiers]).
 * - **C4:** each earth rune boosts Defence by 1 for 30 ticks, at most 20% of the base level.
 *
 * Combination runes count as both their elements and sunfire runes as fire ([PactRuneElements]).
 *
 * A powered staff's or wand's Regenerated **charge** also Regenerates 1 rune per owned F5 (water),
 * F6 (air), F9 (earth) and F10 (fire) node: the rune is added to the rune pouch or inventory, and
 * counts as a rune its spell Regenerated for B1 and C1–C4.
 *
 * In PvP (a magic attack on a player) only the stat boosts trigger, B1 and C4, as Jagex's stat
 * boosts did; C1, C2 and C3 stay PvN.
 */
@Singleton
class ResourcePactProcs
@Inject
constructor(
    private val pacts: DemonicPacts,
    private val modifiers: ResourcePactModifiers,
    private val boosts: PactRegenerateBoosts,
    private val healing: PlayerHealing,
    private val mapClock: MapClock,
    private val pipeline: Provider<CombatModifierPipeline>,
    private val runes: Provider<MagicRuneManager>,
) : CombatProcListener {
    override fun onResourceConsumed(event: ResourceConsumedEvent): ResourceDecision {
        if (!event.regenerated) {
            return ResourceDecision.Consume
        }
        val context = event.context
        val player = context.player
        val effects = pacts.effects(player)
        if (effects.isEmpty) {
            return ResourceDecision.Consume
        }
        when (context.kind) {
            ResourceKind.Rune -> {
                val spell = combatSpell(player)
                if (spell != null) {
                    val elements = PactRuneElements.of(context.obj)
                    val pvp = spell.target is Player
                    onRunesRegenerated(player, effects, elements, context.count, pvp)
                }
            }
            ResourceKind.Charge -> onChargeRegenerated(player, effects, context.obj)
            ResourceKind.Ammo -> {}
        }
        return ResourceDecision.Consume
    }

    override fun onHitRolled(event: HitRolledEvent): List<ExtraHit> {
        val context = event.context
        if (context.style != CombatStyle.Magic || event.source != HitSource.Base) {
            return emptyList()
        }
        if (event.baseHitIndex == 0 && event.rolledDamage > 0) {
            modifiers.clearFireDamage(context.attacker)
        }
        return emptyList()
    }

    private fun onChargeRegenerated(player: Player, effects: PactEffects, weapon: ItemServerType?) {
        if (weapon == null || WeaponClass.PoweredStaff !in WeaponClasses.of(weapon)) {
            return
        }
        val spell = combatSpell(player)
        for ((effect, element) in STAFF_RUNES) {
            val count = effects[effect]
            if (count <= 0) {
                continue
            }
            val rune = runeType(element)
            if (runes.get().addRunes(player, rune, count) && spell != null) {
                onRunesRegenerated(player, effects, setOf(element), count, spell.target is Player)
            }
        }
    }

    private fun onRunesRegenerated(
        player: Player,
        effects: PactEffects,
        elements: Set<SpellElement>,
        count: Int,
        pvp: Boolean,
    ) {
        boostMagic(player, effects)
        for (element in elements) {
            if (pvp && element != SpellElement.Earth) {
                continue
            }
            when (element) {
                SpellElement.Air -> restorePrayer(player, effects[AIR_RUNE_PRAYER], count)
                SpellElement.Water -> heal(player, effects[WATER_RUNE_HEAL] * count)
                SpellElement.Fire -> modifiers.addFireDamage(player, effects[FIRE_RUNE_DAMAGE] * count)
                SpellElement.Earth -> boostDefence(player, effects[EARTH_RUNE_DEFENCE] * count)
                else -> {}
            }
        }
    }

    private fun boostMagic(player: Player, effects: PactEffects) {
        val cap = effects[REGENERATE_MAGIC_BOOST]
        if (cap <= 0 || player.attr[MAGIC_BOOSTED_AT] == mapClock.cycle) {
            return
        }
        player.attr[MAGIC_BOOSTED_AT] = mapClock.cycle
        boosts.add(player, PactRegenerateBoost.Magic, MAGIC_BOOST_PER_TRIGGER, cap)
    }

    private fun restorePrayer(player: Player, chancePercent: Int, runes: Int) {
        if (chancePercent <= 0) {
            return
        }
        var restored = 0
        repeat(runes) {
            if (pipeline.get().roll(chancePercent.toDouble())) {
                restored++
            }
        }
        if (restored > 0) {
            player.statHeal(PRAYER, restored, 0)
        }
    }

    private fun heal(player: Player, amount: Int) {
        if (amount > 0) {
            healing.heal(player, amount, PlayerHealSource.Pact)
        }
    }

    private fun boostDefence(player: Player, levels: Int) {
        if (levels <= 0) {
            return
        }
        val cap = player.baseDefenceLvl * DEFENCE_BOOST_CAP_PERCENT / 100
        boosts.add(player, PactRegenerateBoost.Defence, levels, cap)
    }

    private fun combatSpell(player: Player): AttackScope? =
        pipeline.get().currentAttack(player)?.takeIf { it.style == CombatStyle.Magic }

    private fun runeType(element: SpellElement): ItemServerType {
        val internal = PactRuneElements.elementalRunes.getValue(element)
        return checkNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
            "Rune type not found: $internal"
        }
    }

    private companion object {
        const val PRAYER = "stat.prayer"

        val MAGIC_BOOSTED_AT: AttributeKey<Int> = AttributeKey(temp = true)

        val STAFF_RUNES: List<Pair<Int, SpellElement>> =
            listOf(
                STAFF_WATER_RUNE to SpellElement.Water,
                STAFF_AIR_RUNE to SpellElement.Air,
                STAFF_EARTH_RUNE to SpellElement.Earth,
                STAFF_FIRE_RUNE to SpellElement.Fire,
            )
    }
}
