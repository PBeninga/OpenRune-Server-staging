package org.rsmod.api.combat.modifiers

import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.combat.commons.styles.MagicAttackStyle
import org.rsmod.api.combat.commons.styles.MeleeAttackStyle
import org.rsmod.api.combat.commons.styles.RangedAttackStyle
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.accuracy.AccuracyRollModifier
import org.rsmod.api.config.constants
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Jagex's PvP limit: accuracy, max hit, prayer penetration, attack speed and attack range apply
 * to a player's attacks on players; crits, min hits, max-hit chances and procs don't.
 */
class PvPModifierTest {
    @Test
    fun GameTestState.`accuracy modifiers raise the hit chance against a player`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val opponent = opponent()
            val accuracy = deps.accuracy
            val manager = deps.manager
            val scale = rollModifier(accuracyPercent = 50.0)

            val melee = MeleeAttackType.Crush
            val meleeStyle = MeleeAttackStyle.Accurate
            val meleeBase =
                accuracy.getMeleeHitChance(player, opponent, melee, meleeStyle, melee, 1.0)
            val meleeModified =
                accuracy.getMeleeHitChance(player, opponent, melee, meleeStyle, melee, 1.0, scale)

            val ranged = RangedAttackType.Standard
            val rangedStyle = RangedAttackStyle.Accurate
            val rangedModified =
                accuracy.getRangedHitChance(player, opponent, ranged, rangedStyle, 1.0, scale)

            val spell = objType("obj.01_wind_strike")
            val book = Spellbook.Standard
            val spellModified =
                accuracy.getSpellHitChance(player, opponent, spell, book, false, scale)

            val staffStyle = MagicAttackStyle.Accurate
            val staffModified = accuracy.getStaffHitChance(player, opponent, staffStyle, 1.0, scale)

            val rangedBase =
                accuracy.getRangedHitChance(player, opponent, ranged, rangedStyle, 1.0)
            val spellBase = accuracy.getSpellHitChance(player, opponent, spell, book, false)
            val staffBase = accuracy.getStaffHitChance(player, opponent, staffStyle, 1.0)
            assertTrue(meleeModified > meleeBase)
            assertTrue(rangedModified > rangedBase)
            assertTrue(spellModified > spellBase)
            assertTrue(staffModified > staffBase)

            assertRollChance(meleeBase) {
                manager.rollMeleeAccuracy(player, opponent, melee, meleeStyle, melee, 1.0)
            }

            hooks.provider.attack = AttackModifiers(accuracyPercent = 50.0)
            assertRollChance(meleeModified) {
                manager.rollMeleeAccuracy(player, opponent, melee, meleeStyle, melee, 1.0)
            }
            assertRollChance(rangedModified) {
                manager.rollRangedAccuracy(player, opponent, ranged, rangedStyle, ranged, 1.0)
            }
            assertRollChance(spellModified) {
                manager.rollSpellAccuracy(player, opponent, spell, book, sunfireRune = false)
            }
            assertRollChance(staffModified) {
                manager.rollStaffAccuracy(player, opponent, staffStyle, 1.0)
            }

            val contexts = hooks.provider.attackContexts
            assertTrue(contexts.all { it.isPvp && it.targetPlayer === opponent })
            assertTrue(contexts.all { it.targetNpc == null })
            assertEquals(CombatStyle.entries.toSet(), contexts.map { it.style }.toSet())
        }
    }

    @Test
    fun GameTestState.`max hit modifiers raise the max hit against a player`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.strength", 99)
            setLevel("stat.ranged", 99)
            val opponent = opponent()
            val maxHits = deps.maxHits
            val manager = deps.manager

            val melee = MeleeAttackType.Slash
            val meleeStyle = MeleeAttackStyle.Aggressive
            val meleeBase = maxHits.getMeleeMaxHit(player, opponent, melee, meleeStyle, 1.0)
            val ranged = RangedAttackType.Standard
            val rangedStyle = RangedAttackStyle.Accurate
            val rangedBase = maxHits.getRangedMaxHit(player, opponent, ranged, rangedStyle, 1.0, 0)
            val staffBase = maxHits.getStaffMaxHit(player, opponent, baseMaxHit = 20, 1.0)
            val spell = objType("obj.01_wind_strike")
            val book = Spellbook.Standard
            val spellBase = maxHits.getSpellMaxHitRange(player, opponent, spell, book, 2, false)

            assertEquals(
                meleeBase,
                manager.calculateMeleeMaxHit(player, opponent, melee, meleeStyle, 1.0),
            )

            hooks.provider.attack = AttackModifiers(maxHitPercent = 50.0, maxHitFlat = 3)

            val meleeModified = manager.calculateMeleeMaxHit(player, opponent, melee, meleeStyle, 1.0)
            assertEquals(meleeBase * 3 / 2 + 3, meleeModified)
            assertEquals(meleeModified, player.vars["varp.com_maxhit"])
            assertEquals(
                rangedBase * 3 / 2 + 3,
                manager.calculateRangedMaxHit(player, opponent, ranged, rangedStyle, 1.0, 0),
            )
            assertEquals(
                staffBase * 3 / 2 + 3,
                manager.calculateStaffMaxHit(player, opponent, baseMaxHit = 20, 1.0),
            )
            val spellModified =
                manager.calculateSpellMaxHit(player, opponent, spell, book, 2, 5, false)
            assertEquals(spellBase.first, spellModified.first)
            assertEquals(spellBase.last * 3 / 2 + 3, spellModified.last)

            // Strength terms go through the formula, like PvN.
            hooks.provider.attack = AttackModifiers(meleeStrengthFlat = 100)
            val strength = manager.calculateMeleeMaxHit(player, opponent, melee, meleeStyle, 1.0)
            assertTrue(strength > meleeBase)
        }
    }

    @Test
    fun GameTestState.`crits, min hits, max hit chances and procs don't apply to players`() {
        val hooks = TestHooks()
        hooks.provider.attack =
            AttackModifiers(
                accuracyPercent = 10.0,
                criticalChancePercent = 100.0,
                minHitFlat = 1_000,
                maxHitChancePercent = 100.0,
                procChancePercent = mapOf(HitSource.Echo to 100.0),
                chainCaps = mapOf(HitSource.Echo to 4),
            )
        hooks.listener.extraHits = { listOf(ExtraHit.fixed(HitSource.Echo, 5)) }
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            setLevel("stat.strength", 99)
            val opponent = opponent()
            val manager = deps.manager

            assertEquals(
                AttackModifiers(accuracyPercent = 10.0),
                deps.pipeline.attackModifiers(player, opponent, CombatStyle.Melee),
            )

            val type = MeleeAttackType.Slash
            val style = MeleeAttackStyle.Aggressive
            random.next = 2
            assertEquals(2, manager.rollMeleeMaxHit(player, opponent, type, style, 1.0))

            deps.pipeline.withAttack(player, opponent, CombatStyle.Melee) {
                manager.queueMeleeHit(player, opponent, damage = 10)
            }
            advance(2)

            assertEquals(99 - 10, opponent.hitpoints)
            assertTrue(hooks.listener.rolled.isEmpty())
            assertTrue(hooks.listener.dealt.isEmpty())
        }
    }

    @Test
    fun GameTestState.`prayer penetration ignores part of a player's protection prayer`() {
        val hooks = TestHooks()
        runModifierTest(hooks.registry) { deps ->
            player.placeAt(TEST_COORDS)
            val opponent = opponent()
            opponent.setVarBit("varbit.prayer_protectfrommelee", 1)
            opponent.setVarBit("varbit.prayer_protectfrommissiles", 1)
            val manager = deps.manager

            // Protect from Melee blocks 40% of a player's hit.
            manager.queueMeleeHit(player, opponent, damage = 50)
            advance(2)
            assertEquals(99 - 30, opponent.hitpoints)

            // 50% penetration ignores half of the block: 20%.
            hooks.provider.attack = AttackModifiers(prayerPenetrationPercent = 50.0)
            assertEquals(20, ModifierMath.pvpPrayerPenetration(50.0))
            manager.queueMeleeHit(player, opponent, damage = 50)
            advance(2)
            assertEquals(99 - 30 - 40, opponent.hitpoints)

            // Full penetration ignores the prayer, for ranged hits too.
            hooks.provider.attack = AttackModifiers(prayerPenetrationPercent = 100.0)
            manager.queueRangedHit(player, opponent, null, 20, clientDelay = 0, hitDelay = 1)
            advance(2)
            assertEquals(99 - 30 - 40 - 20, opponent.hitpoints)
        }
    }

    @Test
    fun GameTestState.`attack speed applies to real PvP attacks`() {
        val probe = DelayProbe(AttackModifiers(attackSpeedDelta = -1))
        runModifierTest(probe.registry, CombatTestScripts.pvpCombat) { deps ->
            player.placeAt(TEST_COORDS)
            val opponent = opponent()
            passCombatGracePeriod()

            player.withProtectedAccess { opPlayer2(opponent) }
            advanceUntil({ probe.contexts.any { it.isPvp } }, timeoutTicks = 10)

            assertTrue(probe.contexts.all { it.targetPlayer === opponent })
            // Unarmed attacks use the default attack rate.
            assertEquals(constants.combat_default_attackrate - 1, probe.delays.last())
            assertTrue(deps.pipeline.currentAttack(player) == null)
        }
    }

    @Test
    fun GameTestState.`attack range delta lets melee attack a player from further away`() {
        val probe = DelayProbe(AttackModifiers(attackRangeDelta = 2))
        runModifierTest(probe.registry, CombatTestScripts.pvpCombat) { deps ->
            player.placeAt(TEST_COORDS)
            val opponent = opponent(TEST_COORDS.translateX(3))
            assertEquals(3, deps.pipeline.modifyAttackRange(player, opponent, CombatStyle.Melee, 1))
            passCombatGracePeriod()

            player.withProtectedAccess { opPlayer2(opponent) }
            advanceUntil({ probe.delays.any { it > 0 } }, timeoutTicks = 10)

            assertEquals(TEST_COORDS, player.coords)
        }
    }

    private fun GameTestScope.opponent(coords: CoordGrid = TEST_COORDS.translateX(1)): Player {
        val opponent = registerPlayer(coords)
        allocZoneCollision(coords)
        opponent.setBaseLevel("stat.hitpoints", 99)
        opponent.setCurrentLevel("stat.hitpoints", 99)
        return opponent
    }

    private fun GameTestScope.assertRollChance(expectedChance: Int, roll: () -> Boolean) {
        random.next = expectedChance - 1
        assertTrue(roll()) { "Expected a roll of ${expectedChance - 1} to hit." }
        random.next = expectedChance
        assertFalse(roll())
    }

    private fun rollModifier(accuracyPercent: Double): AccuracyRollModifier =
        object : AccuracyRollModifier {
            override fun modifyAttackRoll(attackRoll: Int): Int =
                ModifierMath.scaleAttackRoll(attackRoll, accuracyPercent)
        }

    /**
     * Gives [modifiers] to every attack and records the attacker's attack delay each time it is
     * asked: after the attack sets its delay, that is the delay of the next attack.
     */
    private class DelayProbe(private val modifiers: AttackModifiers) : CombatModifierProvider {
        val contexts: MutableList<AttackContext> = mutableListOf()
        val delays: MutableList<Int> = mutableListOf()
        val registry: CombatModifierRegistry = CombatModifierRegistry.of(providers = listOf(this))

        override fun attackModifiers(context: AttackContext): AttackModifiers {
            contexts += context
            delays += context.attacker.actionDelay - context.attacker.currentMapClock
            return modifiers
        }
    }
}
