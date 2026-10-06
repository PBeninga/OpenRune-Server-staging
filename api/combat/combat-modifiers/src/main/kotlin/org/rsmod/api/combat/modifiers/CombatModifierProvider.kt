package org.rsmod.api.combat.modifiers

/**
 * Contributes **stat modifiers** to combat. Effect systems such as Leagues pacts and relics
 * each implement this instead of adding special cases to combat scripts.
 *
 * Register implementations with a Guice `Multibinder`, for example from a `PluginModule`:
 * ```
 * class PactModule : PluginModule() {
 *     override fun bind() {
 *         addSetBinding<CombatModifierProvider>(PactModifierProvider::class.java)
 *     }
 * }
 * ```
 *
 * Every function is called by [CombatModifierPipeline] on the game thread, possibly several times
 * per attack (accuracy, max hit, crit, ...), so implementations must be cheap, deterministic and
 * free of side effects. Use [CombatProcListener] for anything that reacts to events or rolls random
 * chances. Contributions from all providers are summed (see [AttackModifiers] for the stacking
 * rules); return the `NONE` value of each type when nothing applies.
 *
 * Every context names the player it concerns, so an effect that only some players have returns the
 * `NONE` values for the others. With no provider registered the pipeline does nothing and combat
 * behaves exactly as without it.
 */
public interface CombatModifierProvider {
    /**
     * Returns this provider's modifiers for a player's attack against an npc or a player.
     *
     * Queried when the accuracy roll, max hit, min hit, crit, protection and attack speed or range
     * of the attack described by [context] are resolved, and for the chain cap of its extra hits.
     * Against a player ([AttackContext.isPvp]) only the PvP stats of the result are used
     * ([AttackModifiers.pvp]).
     */
    public fun attackModifiers(context: AttackContext): AttackModifiers = AttackModifiers.NONE

    /** Returns this provider's modifiers for a hit an npc is dealing to a player. */
    public fun defenceModifiers(context: DefenceContext): DefenceModifiers = DefenceModifiers.NONE

    /** Returns this provider's modifiers for a resource the player is about to consume. */
    public fun resourceModifiers(context: ResourceContext): ResourceModifiers =
        ResourceModifiers.NONE
}
