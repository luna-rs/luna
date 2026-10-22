package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.predef.world
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.combat.CombatBotScript
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonality
import io.luna.game.model.mob.bot.brain.BotPersonalityManager
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.util.parser.impl.EquipmentDefinitionFileParser
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises GENERIC initiation and target preference using real personality thresholds and worn item definitions.
 * Visibility, combat state and legality are fixture-controlled; no server or equipment acquisition is started.
 *
 * @author lare96
 */
class PkGenericTargetingTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() {
            InventoryProductionFixtures.initialize()
            if (EquipmentDefinition.ALL.get(1127).isEmpty) EquipmentDefinitionFileParser().run()
        }
    }

    private lateinit var bot: Bot
    private lateinit var session: PkBotScript
    private lateinit var policy: BotCombatActionHandler
    private var cycle = 0L

    @BeforeEach fun setup() {
        cycle = 0
        bot = InventoryProductionFixtures.bot()
        `when`(bot.asPlr()).thenReturn(bot)
        `when`(bot.isAlive).thenReturn(true)
        `when`(bot.position).thenReturn(Position(3100, 3540))
        `when`(bot.combatLevel).thenReturn(50)
        `when`(bot.healthPercent).thenReturn(100)
        `when`(bot.cycles).thenAnswer { cycle }
        `when`(bot.combat.isAttackable).thenReturn(true)
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.GENERIC, Styles.TRIBRID, Build.MAIN))
        `when`(bot.scriptStack).thenReturn(BotScriptStack(bot, mock(BotScriptManager::class.java)))
        bot.wildernessLevel = 3
        equipment(bot)
        personality()
        policy = BotCombatActionHandler(bot, bot.actionHandler)
        session = InventoryProductionFixtures.active(PkBotScript(bot, 5.minutes))
        bot.scriptStack.pushTail(session)
    }

    private fun personality(intelligence: Double = 0.5, confidence: Double = 0.5, kindness: Double = 0.5) {
        val personality = BotPersonality.Builder(mock(BotPersonalityManager::class.java))
            .setIntelligence(intelligence).setConfidence(confidence)
            .setKindness(kindness).setSocial(0.5).setDexterity(0.5).build()
        `when`(bot.personality).thenReturn(personality)
    }

    private fun equipment(player: Player): Equipment = Equipment(player).also {
        // Only reading worn definitions is under test; equipment listener output is covered by switch tests.
        it.setListeners()
        `when`(player.equipment).thenReturn(it)
    }

    private fun target(x: Int = 3101, level: Int = 50, health: Int = 100): Player {
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(target.position).thenReturn(Position(x, 3540))
        `when`(target.state).thenReturn(EntityState.ACTIVE)
        `when`(target.isAlive).thenReturn(true)
        `when`(target.isViewableFrom(bot)).thenReturn(true)
        `when`(target.combatLevel).thenReturn(level)
        `when`(target.healthPercent).thenReturn(health)
        `when`(target.skullIcon).thenReturn(SkullIcon.NONE)
        `when`(target.combat.target).thenReturn(null)
        `when`(target.combat.isAttackable).thenReturn(true)
        `when`(bot.controllers.checkCombat(target)).thenReturn(true)
        `when`(bot.combat.checkMultiCombat(target)).thenReturn(true)
        equipment(target)
        return target
    }

    @Test fun nativePersonalityBoundariesSelectRecklessUncertainAndOpportunisticBehavior() {
        val strong = target(3101, level = 52)
        val weak = target(3102, level = 48, health = 50)
        personality(intelligence = 0.3, confidence = 0.7)
        assertTrue(bot.personality.isStupidlyConfident)
        assertTrue(session.wantsToAttack(strong))
        assertEquals(listOf(strong, weak), session.orderTargets(listOf(strong, weak)))
        personality(intelligence = 0.3001, confidence = 0.7)
        assertFalse(bot.personality.isStupidlyConfident)
        assertEquals(listOf(weak, strong), session.orderTargets(listOf(strong, weak)))
        personality(confidence = 0.3)
        assertFalse(session.wantsToAttack(weak))
        personality(confidence = 0.3001)
        assertTrue(session.wantsToAttack(weak))
    }

    @Test fun opportunistsPreferVulnerableTargetsButMayChallengeAnOtherwiseEqualLegalOpponent() {
        val peer = target()
        assertTrue(session.wantsToAttack(peer))
        assertEquals(0, policy.pkTargetVulnerability(peer, fleeing = false))
        val weak = target(3102, level = 48, health = 50)
        assertEquals(listOf(weak, peer), session.orderTargets(listOf(peer, weak)))
    }

    @Test fun visibleVulnerabilitySignalsAreRelativeAndEquallyWeighted() {
        bot.equipment.set(Equipment.CHEST, Item(1127))
        val weak = target(level = 49, health = 99)
        weak.equipment.set(Equipment.CHEST, Item(1115))
        assertEquals(3, policy.pkTargetVulnerability(weak, fleeing = false))
        assertEquals(4, policy.pkTargetVulnerability(weak, fleeing = true))
        `when`(bot.healthPercent).thenReturn(50)
        assertEquals(2, policy.pkTargetVulnerability(weak, fleeing = false))
    }

    @Test fun bankedOrCarriedUpgradesDoNotPretendTheBotIsWearingStrongerGear() {
        val peer = target()
        InventoryProductionFixtures.bank(bot, Item(1127))
        InventoryProductionFixtures.inventory(bot, Item(1127))
        assertEquals(0, policy.pkTargetVulnerability(peer, fleeing = false))
        bot.equipment.set(Equipment.CHEST, Item(1127))
        assertEquals(1, policy.pkTargetVulnerability(peer, fleeing = false))
    }

    @Test fun equipmentStackSizeDoesNotMultiplyItsCombatTierAndTiesKeepExistingOrder() {
        val first = target(3101)
        val second = target(3102)
        bot.equipment.set(Equipment.AMMUNITION, Item(882, 1))
        first.equipment.set(Equipment.AMMUNITION, Item(882, 1000))
        second.equipment.set(Equipment.AMMUNITION, Item(882, 10))
        assertEquals(0, policy.pkTargetVulnerability(first, fleeing = false))
        assertEquals(listOf(first, second), session.orderTargets(listOf(first, second)))
    }

    @Test fun uncertainBotsOnlyEngageWhenTheyHaveAnIncomingAttackAttemptOrRecentReceivedHit() {
        personality(confidence = 0.3)
        val other = target(health = 1)
        assertFalse(session.wantsToAttack(other))
        assertFalse(session.allowsPursuit(other))
        `when`(other.combat.target).thenReturn(bot)
        assertTrue(session.allowsPursuit(other))
        `when`(other.combat.target).thenReturn(null)
        val incoming = mock(CombatAttack::class.java)
        doReturn(other).`when`(incoming).attacker
        `when`(incoming.timestamp).thenReturn(Instant.now())
        `when`(bot.combat.lastAttackReceived).thenReturn(incoming)
        `when`(bot.combat.lastCombatWith).thenReturn(other)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertTrue(session.allowsPursuit(other))
        `when`(bot.combat.inCombat()).thenReturn(false)
        assertFalse(session.allowsPursuit(other))
        `when`(bot.combat.inCombat()).thenReturn(true)
        val unrelated = target(3102)
        assertFalse(session.allowsPursuit(unrelated))
    }

    @Test fun staleReceivedHitsCannotAuthorizeRetaliationDuringUnrelatedCombatOrAfterTimeout() {
        val other = target()
        val unrelated = target(3102)
        val now = Instant.parse("2026-10-22T20:00:00Z")
        val incoming = mock(CombatAttack::class.java)
        doReturn(other).`when`(incoming).attacker
        `when`(incoming.timestamp).thenReturn(now.minusSeconds(5))
        `when`(bot.combat.lastAttackReceived).thenReturn(incoming)
        `when`(bot.combat.inCombat()).thenReturn(true)
        `when`(bot.combat.lastCombatWith).thenReturn(unrelated)
        assertFalse(policy.canRetaliateAgainst(other, now))
        `when`(bot.combat.lastCombatWith).thenReturn(other)
        assertTrue(policy.canRetaliateAgainst(other, now))
        `when`(incoming.timestamp).thenReturn(now.minusSeconds(11))
        assertFalse(policy.canRetaliateAgainst(other, now))
        `when`(incoming.timestamp).thenReturn(now.plusSeconds(1))
        assertFalse(policy.canRetaliateAgainst(other, now))
        `when`(incoming.timestamp).thenReturn(null)
        assertFalse(policy.canRetaliateAgainst(other, now))
    }

    @Test fun uncertainPatrolDoesNotInitiateButItsExistingCombatResponseCanRetaliate() = runBlocking<Unit> {
        personality(confidence = 0.2)
        val aggressor = target(health = 1)
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(setOf(aggressor))
        assertFalse(session.searchAndAttack())
        assertSame(session, bot.scriptStack.current())
        `when`(bot.combat.inCombat()).thenReturn(true)
        `when`(bot.combat.lastCombatWith).thenReturn(aggressor)
        `when`(aggressor.combat.target).thenReturn(bot)
        assertTrue(session.checkCombat())
        assertTrue(bot.scriptStack.current() is CombatBotScript)
        verify(bot.combat, never()).attack(aggressor)
    }

    @Test fun actualSelectionPrefersTheVulnerableLegalTargetAndRecklessnessStillRespectsLegality() {
        val strong = target(3101, level = 52)
        val weak = target(3102, level = 48, health = 50)
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(linkedSetOf(strong, weak))
        assertTrue(session.searchAndAttack())
        val child = bot.scriptStack.current() as CombatBotScript
        val focus = CombatBotScript::class.java.getDeclaredField("focus").apply { isAccessible = true }
        assertSame(weak, focus.get(child))
        personality(intelligence = 0.1, confidence = 0.9)
        val next = PkBotScript(bot, 5.minutes)
        `when`(bot.combat.checkMultiCombat(strong)).thenReturn(false)
        `when`(bot.controllers.checkCombat(weak)).thenReturn(false)
        assertFalse(next.searchAndAttack())
    }

    @Test fun nonKindObservationRecognizesFleeingWithoutInterruptingThePatrolToProtectSomeone() {
        val fleeing = target(3105)
        val pursuer = target(3102)
        val peer = target(3107)
        `when`(pursuer.combat.target).thenReturn(fleeing)
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(setOf(fleeing, pursuer, peer))
        BotCombatActionHandler.observePvpMovement(bot)
        assertEquals(listOf(peer, fleeing), session.orderTargets(listOf(peer, fleeing)))
        cycle++
        doReturn(Position(3103, 3540)).`when`(pursuer).position
        doReturn(Position(3106, 3540)).`when`(fleeing).position
        BotCombatActionHandler.observePvpMovement(bot)
        assertEquals(listOf(fleeing, peer), session.orderTargets(listOf(peer, fleeing)))
        assertSame(session, bot.scriptStack.current())
        cycle++
        BotCombatActionHandler.observePvpMovement(bot)
        assertEquals(listOf(peer, fleeing), session.orderTargets(listOf(peer, fleeing)))
    }

    @Test fun kindnessKeepsItsOwnRulesEvenForAnUncertainOrStupidlyConfidentPersonality() {
        val other = target()
        personality(intelligence = 0.1, confidence = 0.9, kindness = 0.7)
        assertFalse(session.wantsToAttack(other))
        `when`(other.skullIcon).thenReturn(SkullIcon.WHITE)
        assertTrue(session.wantsToAttack(other))
        personality(confidence = 0.1, kindness = 0.7)
        assertTrue(session.wantsToAttack(other))
        `when`(other.combat.inCombat()).thenReturn(true)
        assertFalse(session.wantsToAttack(other))
    }
}
