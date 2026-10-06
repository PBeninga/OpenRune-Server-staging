package org.rsmod.api.combat.modifiers

/**
 * Reacts to combat events: extra hits, lifesteal, thorns, Regenerate and similar procs.
 *
 * Register implementations with a Guice `Multibinder`, for example from a `PluginModule`:
 * ```
 * class EchoModule : PluginModule() {
 *     override fun bind() {
 *         addSetBinding<CombatProcListener>(EchoProcListener::class.java)
 *     }
 * }
 * ```
 *
 * Listeners are called in registration order on the game thread. They may roll chances, but must do
 * so with an injected `GameRandom` so that tests stay deterministic. Every hook has a no-op
 * default, so a listener only overrides what it needs. Every event names the player it concerns:
 * an effect that only some players have answers with no extra hits, no response or
 * [ResourceDecision.Consume] for the others.
 *
 * Guard rails enforced by [CombatModifierPipeline]:
 * - [onHitRolled] only fires for [HitSource.Base] hits, and for extra hits of a source with a
 *   chain cap ([AttackModifiers.chainCaps]), which may only chain into the same source. Extra hits
 *   never crit.
 * - [onHitDealt] fires for every hit, including extra hits and retaliation, so lifesteal works on
 *   all of them.
 * - Dodge and damage reduction from [onDamageReceived] never apply to typeless or mechanic damage.
 */
public interface CombatProcListener {
    /**
     * Called once a player's **base** hit on an npc has been resolved (after crit, protection and
     * damage caps) and queued.
     *
     * @return Extra hits to queue for this attack, such as double strike, echo, cleave, chain or
     *   blindbag. Each must be tagged with a non-[HitSource.Base] source. Extra hits land with the
     *   base hit unless they ask for a later tick. They do not trigger [onHitRolled] again.
     */
    public fun onHitRolled(event: HitRolledEvent): List<ExtraHit> = emptyList()

    /**
     * Called when a hit queued through the pipeline lands on an npc (lifesteal, special
     * energy, ...). [HitDealtEvent.damage] is the damage actually dealt.
     */
    public fun onHitDealt(event: HitDealtEvent) {}

    /**
     * Called when an npc's hit on a player is about to be applied (dodge, damage reduction, recoil,
     * thorns, reflect).
     *
     * @return How this listener wants to change the hit. All responses are combined: any `dodge`
     *   dodges, reductions are summed with [DefenceModifiers.damageReductionPercent], and all
     *   retaliation hits are queued.
     */
    public fun onDamageReceived(event: DamageReceivedEvent): DamageReceivedResponse =
        DamageReceivedResponse.NONE

    /**
     * Called before runes, ammo or charges are consumed.
     *
     * @return [ResourceDecision.Refund] to Regenerate the resource (it is not consumed), or
     *   [ResourceDecision.Consume]. If any listener refunds, or the provider-driven Regenerate
     *   chance succeeds ([ResourceConsumedEvent.regenerated]), the resource is refunded.
     */
    public fun onResourceConsumed(event: ResourceConsumedEvent): ResourceDecision =
        ResourceDecision.Consume
}
