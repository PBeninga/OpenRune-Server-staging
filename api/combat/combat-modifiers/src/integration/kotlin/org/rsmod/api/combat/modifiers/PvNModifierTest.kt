package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.commons.styles.MagicAttackStyle
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.accuracy.AccuracyRollModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.hit.HitType

/** Each PvN stat modifier category changes the outcome of `PlayerAttackManager` rolls. */
class PvNModifierTest {
    @Test
    fun GameTestState.`accuracy percent raises the hit chance of every style`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget()
            val accuracy = deps.accuracy
            val scale = rollModifier(accuracyPercent = 50.0)

            val melee = MeleeAttackType.Crush
            val meleeStyle = MeleeAttackStyle.Accurate
            val meleeBase = accuracy.getMeleeHitChance(player, npc, melee, meleeStyle, melee, 1.0)
            val meleeModified =
                accuracy.getMeleeHitChance(player, npc, melee, meleeStyle, melee, 1.0, scale)

            val ranged = RangedAttackType.Standard
            val rangedStyle = RangedAttackStyle.Accurate
            val rangedBase =
                accuracy.getRangedHitChance(player, npc, ranged, rangedStyle, ranged, 1.0)
            val rangedModified =
                accuracy.getRangedHitChance(player, npc, ranged, rangedStyle, ranged, 1.0, scale)

            val spell = objType("obj.01_wind_strike")
            val book = Spellbook.Standard
            val spellBase = accuracy.getSpellHitChance(player, npc, spell, book, false)
            val spellModified = accuracy.getSpellHitChance(player, npc, spell, book, false, scale)

            val staffStyle = MagicAttackStyle.Accurate
            val staffBase = accuracy.getStaffHitChance(player, npc, staffStyle, 1.0)
            val staffModified = accuracy.getStaffHitChance(player, npc, staffStyle, 1.0, scale)

            assertTrue(meleeModified > meleeBase)
            assertTrue(rangedModified > rangedBase)
            assertTrue(spellModified > spellBase)
            assertTrue(staffModified > staffBase)

            val manager = deps.manager

            // Without modifiers, the manager rolls exactly the unmodified hit chance.
            assertRollChance(meleeBase) {
                manager.rollMeleeAccuracy(player, npc, melee, meleeStyle, melee, 1.0)
            }

            hooks.provider.attack = AttackModifiers(accuracyPercent = 50.0)
            assertRollChance(meleeModified) {
                manager.rollMeleeAccuracy(player, npc, melee, meleeStyle, melee, 1.0)
            }
            assertRollChance(rangedModified) {
                manager.rollRangedAccuracy(player, npc, ranged, rangedStyle, ranged, 1.0)
            }
            assertRollChance(spellModified) {
                manager.rollSpellAccuracy(player, npc, spell, book, sunfireRune = false)
            }
            assertRollChance(staffModified) {
                manager.rollStaffAccuracy(player, npc, staffStyle, 1.0)
            }

            val styles = hooks.provider.attackContexts.map { it.style }.toSet()
            assertEquals(CombatStyle.entries.toSet(), styles)
        }
    }

    @Test
    fun GameTestState.`defence roll ignore raises the hit chance`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget()
            npc.defenceLvl = 99

            val type = MeleeAttackType.Stab
            val style = MeleeAttackStyle.Accurate
            val base = deps.accuracy.getMeleeHitChance(player, npc, type, style, type, 1.0)
            val ignore = rollModifier(defenceRollIgnorePercent = 40.0)
            val expected =
                deps.accuracy.getMeleeHitChance(player, npc, type, style, type, 1.0, ignore)
            assertTrue(expected > base)

            hooks.provider.attack = AttackModifiers(defenceRollIgnorePercent = 40.0)
            assertRollChance(expected) {
                deps.manager.rollMeleeAccuracy(player, npc, type, style, type, 1.0)
            }

            // Ignoring the whole defence roll makes the hit (almost) certain.
            hooks.provider.attack = AttackModifiers(defenceRollIgnorePercent = 100.0)
            val full = rollModifier(defenceRollIgnorePercent = 100.0)
            val certain = deps.accuracy.getMeleeHitChance(player, npc, type, style, type, 1.0, full)
            assertTrue(certain >= 9_900)
            assertRollChance(certain) {
                deps.manager.rollMeleeAccuracy(player, npc, type, style, type, 1.0)
            }
        }
    }

    @Test
    fun GameTestState.`max hit percent and flat bonus raise the max hit`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.strength", 99)
            setLevel("stat.ranged", 99)
            val npc = spawnTarget()
            val maxHits = deps.maxHits
            val manager = deps.manager

            val melee = MeleeAttackType.Slash
            val meleeStyle = MeleeAttackStyle.Aggressive
            val meleeBase = maxHits.getMeleeMaxHit(player, npc, melee, meleeStyle, 1.0)
            val ranged = RangedAttackType.Standard
            val rangedStyle = RangedAttackStyle.Accurate
            val rangedBase = maxHits.getRangedMaxHit(player, npc, ranged, rangedStyle, 1.0, 0)
            val staffBase = maxHits.getStaffMaxHit(player, npc, baseMaxHit = 20, 1.0)
            val spell = objType("obj.01_wind_strike")
            val book = Spellbook.Standard
            val spellBase = maxHits.getSpellMaxHitRange(player, npc, spell, book, 2, 5, false)

            // Neutral without modifiers.
            assertEquals(
                meleeBase,
                manager.calculateMeleeMaxHit(player, npc, melee, meleeStyle, 1.0),
            )

            hooks.provider.attack = AttackModifiers(maxHitPercent = 50.0, maxHitFlat = 3)

            val meleeModified = manager.calculateMeleeMaxHit(player, npc, melee, meleeStyle, 1.0)
            assertEquals(meleeBase * 3 / 2 + 3, meleeModified)
            assertEquals(meleeModified, player.vars["varp.com_maxhit"])

            val rangedModified =
                manager.calculateRangedMaxHit(player, npc, ranged, rangedStyle, 1.0, 0)
            assertEquals(rangedBase * 3 / 2 + 3, rangedModified)

            val staffModified = manager.calculateStaffMaxHit(player, npc, baseMaxHit = 20, 1.0)
            assertEquals(staffBase * 3 / 2 + 3, staffModified)

            val spellModified =
                manager.calculateSpellMaxHit(player, npc, spell, book, 2, 5, sunfireRune = false)
            assertEquals(spellBase.first, spellModified.first)
            assertEquals(spellBase.last * 3 / 2 + 3, spellModified.last)
        }
    }

    @Test
    fun GameTestState.`min hit raises the lowest damage of a successful hit`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.strength", 99)
            val npc = spawnTarget()
            val manager = deps.manager
            val type = MeleeAttackType.Slash
            val style = MeleeAttackStyle.Aggressive
            val maxHit = manager.calculateMeleeMaxHit(player, npc, type, style, 1.0)
            assertTrue(maxHit > 5)

            // A min hit at or above the max hit always rolls the max hit.
            hooks.provider.attack = AttackModifiers(minHitFlat = 1_000)
            repeat(10) {
                assertEquals(maxHit, manager.rollMeleeMaxHit(player, npc, type, style, 1.0))
            }

            // Otherwise rolls stay within `minHit..maxHit`.
            hooks.provider.attack = AttackModifiers(minHitFlat = maxHit - 1)
            repeat(25) {
                val damage = manager.rollMeleeMaxHit(player, npc, type, style, 1.0)
                assertTrue(damage in (maxHit - 1)..maxHit)
            }

            // Spells keep their own minimum unless the modifier min hit is higher, and a min hit
            // never exceeds the max hit.
            val spell = objType("obj.01_wind_strike")
            val book = Spellbook.Standard
            hooks.provider.attack = AttackModifiers.NONE
            val spellRange = manager.calculateSpellMaxHit(player, npc, spell, book, 8, 5, false)
            assertEquals(8, spellRange.last)
            assertTrue(spellRange.first < 6)
            hooks.provider.attack = AttackModifiers(minHitFlat = 6)
            assertEquals(6..8, manager.calculateSpellMaxHit(player, npc, spell, book, 8, 5, false))
            hooks.provider.attack = AttackModifiers(minHitFlat = 20)
            assertEquals(8..8, manager.calculateSpellMaxHit(player, npc, spell, book, 8, 5, false))
        }
    }

    @Test
    fun GameTestState.`critical hits multiply base hits by four`() {
        val hooks = TestHooks()
        hooks.provider.attack = AttackModifiers(criticalChancePercent = 100.0)
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 200)

            deps.manager.queueMeleeHit(player, npc, damage = 10)
            advance(2)

            assertEquals(160, npc.hitpoints)
            val rolled = hooks.listener.rolled.single()
            assertTrue(rolled.critical)
            assertEquals(10, rolled.rolledDamage)
            assertEquals(40, rolled.damage)
            assertEquals(HitSource.Base, hooks.listener.dealt.single().source)
            assertEquals(40, hooks.listener.dealt.single().damage)
        }
    }

    @Test
    fun GameTestState.`a critical hit is rolled with the game random`() {
        val hooks = TestHooks()
        hooks.provider.attack = AttackModifiers(criticalChancePercent = 5.0)
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 200)

            // 5% = 500 basis points: a roll of 499 crits, 500 does not.
            random.next = 499
            deps.manager.queueMeleeHit(player, npc, damage = 10)
            random.next = 500
            deps.manager.queueMeleeHit(player, npc, damage = 10)
            advance(2)

            assertEquals(listOf(true, false), hooks.listener.rolled.map { it.critical })
            assertEquals(200 - 40 - 10, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`crit, protection prayer and damage cap apply in order`() {
        val hooks = TestHooks()
        hooks.provider.attack = AttackModifiers(criticalChancePercent = 100.0)
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 250)
            val manager = deps.manager

            // 10 -> crit 40 -> 50% protection 20 -> cap 15.
            hooks.rules.protection = 50.0
            hooks.rules.cap = 15
            manager.queueMeleeHit(player, npc, damage = 10)

            // 10 -> crit 40 -> 50% protection, half of it penetrated: 30 (no cap).
            hooks.rules.cap = null
            hooks.provider.attack =
                AttackModifiers(criticalChancePercent = 100.0, prayerPenetrationPercent = 50.0)
            manager.queueMeleeHit(player, npc, damage = 10)

            // Full protection without penetration blocks everything.
            hooks.rules.protection = 100.0
            hooks.provider.attack = AttackModifiers(criticalChancePercent = 100.0)
            manager.queueRangedHit(player, npc, null, damage = 10, clientDelay = 0, hitDelay = 1)

            advance(2)
            assertEquals(listOf(15, 30, 0), hooks.listener.rolled.map { it.damage })
            assertEquals(250 - 15 - 30, npc.hitpoints)
        }
    }

    @Test
    fun GameTestState.`with nothing registered combat is unchanged`() {
        runModifierTest(CombatModifierRegistry.EMPTY) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.strength", 99)
            val npc = spawnTarget(hitpoints = 200)
            val manager = deps.manager
            val pipeline = deps.pipeline
            assertFalse(pipeline.isActive)

            val type = MeleeAttackType.Slash
            val style = MeleeAttackStyle.Aggressive
            val chance = deps.accuracy.getMeleeHitChance(player, npc, type, style, type, 1.0)
            assertRollChance(chance) {
                manager.rollMeleeAccuracy(player, npc, type, style, type, 1.0)
            }

            val maxHit = deps.maxHits.getMeleeMaxHit(player, npc, type, style, 1.0)
            assertEquals(maxHit, manager.calculateMeleeMaxHit(player, npc, type, style, 1.0))

            // Hits are queued untouched, and no random value is consumed besides the hit roll.
            random.next = 7
            assertEquals(7, manager.rollMeleeMaxHit(player, npc, type, style, 1.0))
            manager.queueMeleeHit(player, npc, damage = 7)
            advance(2)
            assertEquals(193, npc.hitpoints)

            pipeline.withAttack(player, npc, CombatStyle.Melee) {
                manager.setNextAttackDelay(player, 4)
            }
            assertEquals(player.currentMapClock + 4, player.actionDelay)
            assertEquals(5, pipeline.modifyAttackRange(player, npc, CombatStyle.Ranged, 5))
            val arrow = objType("obj.bronze_arrow")
            assertFalse(pipeline.isRefunded(player, ResourceKind.Ammo, arrow, 1))
            assertEquals(HitType.Melee, CombatStyle.Melee.hitType)

            // The decorated player hit modifier leaves npc hits as the standard modifier does.
            setLevel("stat.hitpoints", 99)
            player.queueHit(npc, 1, HitType.Melee, 9, deps.playerHitModifier)
            advance(2)
            assertEquals(99 - 9, player.hitpoints)
        }
    }

    @Test
    fun GameTestState.`multi-hit attacks index their base hits`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val npc = spawnTarget(hitpoints = 200)

            // Like the Scythe of Vitur: several base hits queued by one attack.
            deps.pipeline.withAttack(player, npc, CombatStyle.Melee) {
                repeat(3) { deps.manager.queueMeleeHit(player, npc, damage = 1) }
            }
            deps.manager.queueMeleeHit(player, npc, damage = 1)

            assertEquals(listOf(0, 1, 2, 0), hooks.listener.rolled.map { it.baseHitIndex })
        }
    }

    @Test
    fun GameTestState.`game tests bind empty pipeline sets by default`() {
        runInjectedGameTest(ModifierTestDeps::class) { deps -> assertFalse(deps.pipeline.isActive) }
    }

    private fun GameTestScope.assertRollChance(expectedChance: Int, roll: () -> Boolean) {
        random.next = expectedChance - 1
        assertTrue(roll()) { "Expected a roll of ${expectedChance - 1} to hit." }
        random.next = expectedChance
        assertFalse(roll())
    }

    private fun rollModifier(
        accuracyPercent: Double = 0.0,
        defenceRollIgnorePercent: Double = 0.0,
    ): AccuracyRollModifier =
        object : AccuracyRollModifier {
            override fun modifyAttackRoll(attackRoll: Int): Int =
                ModifierMath.scaleAttackRoll(attackRoll, accuracyPercent)

            override fun modifyDefenceRoll(defenceRoll: Int): Int =
                ModifierMath.scaleDefenceRoll(defenceRoll, defenceRollIgnorePercent)
        }
}
