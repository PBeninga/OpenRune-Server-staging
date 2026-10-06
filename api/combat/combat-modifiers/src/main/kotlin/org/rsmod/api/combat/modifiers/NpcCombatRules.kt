package org.rsmod.api.combat.modifiers

/**
 * Per-npc rules applied at the **end** of the hit pipeline, after critical hits (order of
 * operations, step 4): the npc's protection prayer and its hard damage cap.
 *
 * Register implementations with a Guice `Multibinder` (like [CombatModifierProvider]). Boss content
 * should express OSRS hard caps here (Zulrah, Corporeal Beast, Verzik P1, Warden cores), so that
 * crits and extra hits can never exceed them. No npc uses this yet.
 *
 * The rules apply to every hit a player deals through the pipeline: base hits, extra hits and
 * retaliation hits (thorns, recoil). With several rules registered, the **highest** protection and
 * the **lowest** cap win.
 */
public interface NpcCombatRules {
    /**
     * Returns the percentage (`0..100`) of the hit's damage that the npc's protection prayer
     * blocks, before the attacker's [AttackModifiers.prayerPenetrationPercent] is applied. Called
     * only for hits with a style.
     */
    public fun protectionPercent(context: NpcDamageContext): Double = 0.0

    /** Returns the most damage a single hit may deal to the npc, or `null` for no cap. */
    public fun damageCap(context: NpcDamageContext): Int? = null
}
