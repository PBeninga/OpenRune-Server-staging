package org.rsmod.api.combat.modifiers

import dev.openrune.types.ItemServerType
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/**
 * Describes a player's attack against an npc (PvN) or another player (PvP), as handed to
 * [CombatModifierProvider.attackModifiers] and to the proc events.
 *
 * Built by [CombatModifierPipeline]; providers should treat it as read-only. Against a player
 * ([isPvp]) the pipeline keeps only the PvP stats of what providers return (see
 * [AttackModifiers.pvp]), and proc events are never sent, so a provider only checks [isPvp] for an
 * effect whose stat depends on a proc (for example a bonus that a proc spends).
 *
 * @property attacker The attacking player.
 * @property target The npc or player being attacked. See [targetNpc] and [targetPlayer].
 * @property style The combat style of the attack.
 * @property weapon The attacker's worn `righthand` obj at the time of the attack, if any.
 * @property isSpecial `true` while a special attack is being performed (see
 *   [CombatModifierPipeline.withSpecialAttack]). Attack-speed and attack-range queries are made
 *   outside of the special attack and always see `false`.
 * @property spell The spell obj for spell attacks, when known (spell max hit and magic hits).
 * @property source [HitSource.Base] for the attack's own rolls; the extra-hit source while an extra
 *   hit re-rolls accuracy and damage (see [ExtraHit.reroll]).
 */
public class AttackContext(
    public val attacker: Player,
    public val target: PathingEntity,
    public val style: CombatStyle,
    public val weapon: ItemServerType?,
    public val isSpecial: Boolean,
    public val spell: ItemServerType? = null,
    public val source: HitSource = HitSource.Base,
) {
    /** The npc being attacked, or `null` in PvP. */
    public val targetNpc: Npc?
        get() = target as? Npc

    /** The player being attacked, or `null` in PvN. */
    public val targetPlayer: Player?
        get() = target as? Player

    /** `true` for an attack on another player. */
    public val isPvp: Boolean
        get() = target is Player

    /** Chebyshev distance in tiles between the attacker's and the target's south-west tiles. */
    public val distance: Int
        get() = attacker.coords.chebyshevDistance(target.coords)

    override fun toString(): String =
        "AttackContext(" +
            "attacker=${attacker.username}, " +
            "target=${target.describe()}, " +
            "style=$style, " +
            "weapon=${weapon?.name}, " +
            "isSpecial=$isSpecial, " +
            "spell=${spell?.name}, " +
            "source=$source" +
            ")"
}

/**
 * Describes a hit an npc is about to deal to a player (NvP), as handed to
 * [CombatModifierProvider.defenceModifiers] and [CombatProcListener.onDamageReceived].
 *
 * @property defender The player receiving the hit.
 * @property attacker The npc dealing the hit, or `null` if it can no longer be found (for example
 *   it despawned before a hit that resolves on impact landed).
 * @property hitType The type of the incoming hit.
 * @property isMechanic `true` for boss mechanic damage that must stay lethal, flagged
 *   with `HitBuilder.isMechanic` (for example `PlayerHitModifier.asMechanic()` or the boss DSL's
 *   `mechanic` flag). Like [HitType.Typeless] hits, mechanic hits bypass dodge and damage
 *   reduction.
 */
public class DefenceContext(
    public val defender: Player,
    public val attacker: Npc?,
    public val hitType: HitType,
    public val isMechanic: Boolean,
) {
    /** `false` for typeless and mechanic damage, which dodge and damage reduction never touch. */
    public val isMitigable: Boolean
        get() = !isMechanic && hitType != HitType.Typeless

    override fun toString(): String =
        "DefenceContext(" +
            "defender=${defender.username}, " +
            "attacker=${attacker?.visType?.name}, " +
            "hitType=$hitType, " +
            "isMechanic=$isMechanic" +
            ")"
}

/** The kind of resource consumed by an attack or a spell. */
public enum class ResourceKind {
    /** Runes spent on a spell (inventory runes and rune pouch runes). */
    Rune,

    /** Ammunition fired from the quiver, or a thrown weapon. */
    Ammo,

    /** Charges used by a charged weapon such as Tumeken's shadow. */
    Charge,
}

/**
 * Describes a resource that is about to be consumed, as handed to
 * [CombatModifierProvider.resourceModifiers] and [CombatProcListener.onResourceConsumed].
 *
 * @property player The player spending the resource.
 * @property kind What is being spent.
 * @property obj The obj being spent, when known. Rune pouch runes and charges of unknown objs have
 *   no obj.
 * @property count How many units are being spent. A refund (Regenerate) refunds all of them.
 */
public class ResourceContext(
    public val player: Player,
    public val kind: ResourceKind,
    public val obj: ItemServerType?,
    public val count: Int,
) {
    override fun toString(): String =
        "ResourceContext(player=${player.username}, kind=$kind, obj=${obj?.name}, count=$count)"
}

/**
 * Describes a hit dealt to an npc by a player, as handed to [NpcCombatRules].
 *
 * @property style The style of the hit, or `null` for retaliation hits such as recoil.
 */
public class NpcDamageContext(
    public val attacker: Player,
    public val target: Npc,
    public val style: CombatStyle?,
    public val source: HitSource,
) {
    override fun toString(): String =
        "NpcDamageContext(" +
            "attacker=${attacker.username}, " +
            "target=${target.visType.name}, " +
            "style=$style, " +
            "source=$source" +
            ")"
}

internal fun PathingEntity.describe(): String =
    when (this) {
        is Npc -> visType.name
        is Player -> username
    }
