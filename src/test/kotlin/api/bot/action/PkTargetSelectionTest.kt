package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.bot.webwalk.plan.BankAccess
import api.predef.world
import engine.controllers.WildernessLocatableController.levelAt
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.combat.CombatBotScript
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import io.luna.game.model.mob.bot.brain.BotReflex
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

/**
 * Exercises PK recognition, native player eligibility and patrol-to-combat handoff without starting a server.
 * Uses real positions, Wilderness bounds, script-stack queries and snapshot serialization with mocked combat rules.
 *
 * @author lare96
 */
class PkTargetSelectionTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() = InventoryProductionFixtures.initialize()
    }

    private lateinit var bot: Bot
    private lateinit var target: Player
    private lateinit var policy: BotCombatActionHandler

    @BeforeEach fun setup() {
        bot = InventoryProductionFixtures.bot()
        `when`(bot.asPlr()).thenReturn(bot)
        `when`(bot.isAlive).thenReturn(true)
        `when`(bot.position).thenReturn(Position(3100, 3540))
        `when`(bot.combatLevel).thenReturn(50)
        `when`(bot.combat.isAttackable).thenReturn(true)
        `when`(bot.personality.type).thenReturn(PersonalityTemplateType.JACK_OF_ALL_TRADES)
        val stack = BotScriptStack(bot, mock(BotScriptManager::class.java))
        `when`(bot.scriptStack).thenReturn(stack)
        bot.wildernessLevel = 3
        target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(target.attributes()).thenReturn(AttributeMap())
        `when`(target.isAlive).thenReturn(true)
        `when`(target.state).thenReturn(EntityState.ACTIVE)
        `when`(target.position).thenReturn(Position(3101, 3540))
        `when`(target.combatLevel).thenReturn(50)
        `when`(target.combat.isAttackable).thenReturn(true)
        `when`(target.isViewableFrom(bot)).thenReturn(true)
        `when`(target.skullIcon).thenReturn(SkullIcon.NONE)
        `when`(bot.combat.checkMultiCombat(target)).thenReturn(true)
        `when`(bot.controllers.checkCombat(target)).thenReturn(true)
        policy = BotCombatActionHandler(bot, bot.actionHandler)
    }

    @Test fun humansAreRecognizedOnlyByTheirNativeSkull() {
        assertFalse(BotCombatActionHandler.isPker(target))
        for (skull in listOf(SkullIcon.WHITE, SkullIcon.RED)) {
            `when`(target.skullIcon).thenReturn(skull)
            assertTrue(BotCombatActionHandler.isPker(target))
        }
        `when`(target.skullIcon).thenReturn(SkullIcon.NONE)
        assertFalse(BotCombatActionHandler.isPker(target))
    }

    @Test fun botSkullAndSavedBuildAloneDoNotDeclarePkIntent() {
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.VALUE, Styles.TRIBRID, Build.MAIN))
        assertFalse(BotCombatActionHandler.isPker(bot))
        `when`(bot.personality.type).thenReturn(PersonalityTemplateType.PKER)
        assertTrue(BotCombatActionHandler.isPker(bot))
    }

    @Test fun pausedPkParentRemainsRecognizableUnderACombatChild() {
        val session = mock(PkBotScript::class.java)
        `when`(session.isPaused()).thenReturn(true)
        bot.scriptStack.pushTail(session)
        bot.scriptStack.pushHead(mock(CombatBotScript::class.java))
        assertTrue(BotCombatActionHandler.isPker(bot))
        assertTrue(bot.scriptStack.current() is CombatBotScript)
    }

    @Test fun queuedFinishedAndTerminatedSessionsDoNotImplyCurrentPkIntent() {
        val session = mock(PkBotScript::class.java)
        bot.scriptStack.pushTail(session)
        assertFalse(BotCombatActionHandler.isPker(bot))
        `when`(session.isRunning()).thenReturn(true)
        assertTrue(BotCombatActionHandler.isPker(bot))
        `when`(session.isFinished()).thenReturn(true)
        assertFalse(BotCombatActionHandler.isPker(bot))
        `when`(session.isFinished()).thenReturn(false)
        `when`(session.isTerminated()).thenReturn(true)
        assertFalse(BotCombatActionHandler.isPker(bot))
    }

    @Test fun skillersAreNeitherPkersNorEligibleAttackersEvenWithStalePkState() {
        `when`(bot.preferences.isSkiller).thenReturn(true)
        `when`(bot.personality.type).thenReturn(PersonalityTemplateType.PKER)
        val session = mock(PkBotScript::class.java)
        `when`(session.isRunning()).thenReturn(true)
        bot.scriptStack.pushTail(session)
        assertFalse(BotCombatActionHandler.isPker(bot))
        assertFalse(policy.canAttackPlayer(target))
    }

    @Test fun bothWildernessDepthsMustPermitTheCombatLevelDifference() {
        `when`(bot.position).thenReturn(Position(3100, 3592)) // Level 10.
        `when`(target.position).thenReturn(Position(3101, 3591)) // Level 9.
        `when`(target.combatLevel).thenReturn(60)
        assertFalse(policy.canAttackWildernessPlayer(target))
        `when`(target.position).thenReturn(Position(3101, 3592))
        assertTrue(policy.canAttackWildernessPlayer(target))
        `when`(bot.position).thenReturn(Position(3100, 3591))
        assertFalse(policy.canAttackWildernessPlayer(target))
        `when`(target.combatLevel).thenReturn(59)
        assertTrue(policy.canAttackWildernessPlayer(target))
    }

    @Test fun dungeonDepthUsesTheSameCombatRangeAsTheSurface() {
        `when`(bot.position).thenReturn(Position(3100, 9992))
        `when`(target.position).thenReturn(Position(3101, 9991))
        `when`(target.combatLevel).thenReturn(60)
        assertFalse(policy.canAttackWildernessPlayer(target))
        `when`(target.position).thenReturn(Position(3101, 9992))
        assertTrue(policy.canAttackWildernessPlayer(target))
    }

    @Test fun crossingOutOfWildernessRejectsAttackDespiteStaleCachedLevels() {
        target.wildernessLevel = 3
        `when`(target.position).thenReturn(Position(3101, 3518))
        assertFalse(policy.canAttackPlayer(target))
        `when`(target.position).thenReturn(Position(3101, 3540))
        `when`(bot.position).thenReturn(Position(3100, 3518))
        assertFalse(policy.canAttackPlayer(target))
    }

    @Test fun outsideWildernessCombatStillRequiresTheNativeController() {
        `when`(bot.position).thenReturn(Position(3100, 3400))
        `when`(target.position).thenReturn(Position(3101, 3400))
        assertTrue(policy.canAttackPlayer(target))
        assertFalse(policy.canAttackWildernessPlayer(target))
        `when`(bot.controllers.checkCombat(target)).thenReturn(false)
        assertFalse(policy.canAttackPlayer(target))
    }

    @Test fun selfInvisibleWrongPlaneDeadAndInactiveTargetsAreRejected() {
        assertFalse(policy.canAttackPlayer(bot))
        assertTrue(policy.canAttackPlayer(target))
        `when`(target.isViewableFrom(bot)).thenReturn(false)
        assertFalse(policy.canAttackPlayer(target))
        `when`(target.isViewableFrom(bot)).thenReturn(true)
        `when`(target.position).thenReturn(Position(3101, 3540, 1))
        assertFalse(policy.canAttackPlayer(target))
        `when`(target.position).thenReturn(Position(3101, 3540))
        `when`(target.isAlive).thenReturn(false)
        assertFalse(policy.canAttackPlayer(target))
        `when`(target.isAlive).thenReturn(true)
        `when`(target.state).thenReturn(EntityState.INACTIVE)
        assertFalse(policy.canAttackPlayer(target))
        `when`(target.state).thenReturn(EntityState.ACTIVE)
        `when`(bot.isAlive).thenReturn(false)
        assertFalse(policy.canAttackPlayer(target))
        `when`(bot.isAlive).thenReturn(true)
        `when`(bot.state).thenReturn(EntityState.INACTIVE)
        assertFalse(policy.canAttackPlayer(target))
    }

    @Test fun nativeAttackabilityCombatDisableAndSingleCombatAreRespected() {
        `when`(target.combat.isAttackable).thenReturn(false)
        assertFalse(policy.canAttackPlayer(target))
        `when`(target.combat.isAttackable).thenReturn(true)
        `when`(bot.combat.isAttackable).thenReturn(false)
        assertFalse(policy.canAttackPlayer(target))
        `when`(bot.combat.isAttackable).thenReturn(true)
        `when`(bot.combat.isDisabled).thenReturn(true)
        assertFalse(policy.canAttackPlayer(target))
        `when`(bot.combat.isDisabled).thenReturn(false)
        `when`(bot.combat.checkMultiCombat(target)).thenReturn(false)
        assertFalse(policy.canAttackPlayer(target))
        `when`(bot.combat.checkMultiCombat(target)).thenReturn(true)
        `when`(bot.controllers.checkCombat(target)).thenReturn(false)
        assertFalse(policy.canAttackPlayer(target))
    }

    @Test fun patrolQueuesCombatWithoutBypassingItsFirstReactionAndSupplyChecks() {
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(setOf(target))
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.VALUE, Styles.TRIBRID, Build.MAIN))
        val session = InventoryProductionFixtures.active(PkBotScript(bot, 5.minutes))
        bot.scriptStack.pushTail(session)
        assertTrue(session.searchAndAttack())
        assertTrue(bot.scriptStack.current() is CombatBotScript)
        assertTrue(session.isPaused())
        assertTrue(BotCombatActionHandler.isPker(bot))
        verify(bot.combat, never()).attack(target)
    }

    @Test fun patrolRejectsAPlayerThatCannotBeAttackedAtBothWildernessDepths() {
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(setOf(target))
        `when`(target.combatLevel).thenReturn(60)
        assertFalse(PkBotScript(bot, 5.minutes).searchAndAttack())
        assertEquals(0, bot.scriptStack.size())
        verify(bot.combat, never()).attack(target)
    }

    @Test fun sessionSnapshotRetainsMillisecondsAcrossSaveAndLoadAndClampsExpiredTime() {
        val session = PkBotScript(bot, 5.minutes)
        session.expireAt = System.currentTimeMillis() + 123456
        val data = com.google.gson.JsonObject()
        session.snapshot().save(data)
        val restored = PkBotScript.Companion.PkData().apply { load(data) }
        assertTrue(restored.duration.inWholeMilliseconds in 122456..123456)
        assertEquals(data.get("duration").asLong.milliseconds, restored.duration)
        session.expireAt = System.currentTimeMillis() - 1
        assertEquals(0L, session.snapshot().duration.inWholeMilliseconds)
    }

    private fun skillProfile() {
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.SKILL, Styles.TRIBRID, Build.MAIN))
    }

    @Test fun skillPatrolUsesExistingLowEdgevilleAnchorsOnly() {
        skillProfile()
        val session = PkBotScript(bot, 5.minutes)
        val anchors = session.patrolAnchors()
        assertTrue(anchors.isNotEmpty())
        assertTrue(anchors.all { it in PkBotScript.PkArea.LOW_LEVEL.anchors })
        assertTrue(anchors.all { it.regionId == 12343 && it.z == 0 && levelAt(it) in 1..8 })
        assertFalse(session.allowsHuntingAt(Position(3141, 3690)))
        assertFalse(session.allowsHuntingAt(Position(3100, 9940)))
        assertFalse(session.allowsHuntingAt(Position(3100, 3540, 1)))
    }

    @Test fun skillTargetsUseNormalWildernessRangeRatherThanAnAdditionalFiveLevelCap() {
        skillProfile()
        `when`(bot.position).thenReturn(Position(3100, 3583))
        `when`(target.position).thenReturn(Position(3101, 3583))
        `when`(target.combatLevel).thenReturn(58)
        `when`(target.skullIcon).thenReturn(SkullIcon.WHITE)
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(setOf(target))
        assertTrue(PkBotScript(bot, 5.minutes).searchAndAttack())
        assertTrue(bot.scriptStack.current() is CombatBotScript)
        verify(bot.combat, never()).attack(target)
    }

    @Test fun skillPatrolOnlyInitiatesAgainstRecognizedPkersInsideEdgeville() {
        skillProfile()
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(setOf(target))
        val session = PkBotScript(bot, 5.minutes)
        assertFalse(session.searchAndAttack())
        `when`(target.skullIcon).thenReturn(SkullIcon.WHITE)
        `when`(bot.position).thenReturn(Position(3135, 3540))
        `when`(target.position).thenReturn(Position(3136, 3540))
        assertTrue(policy.canAttackWildernessPlayer(target))
        assertFalse(session.searchAndAttack())
        `when`(bot.position).thenReturn(Position(3141, 3690))
        `when`(target.position).thenReturn(Position(3142, 3690))
        assertFalse(session.searchAndAttack())
        assertTrue(policy.canAttackWildernessPlayer(target))
        assertEquals(0, bot.scriptStack.size())
    }

    @Test fun skillSessionReturnsToEdgevilleBeforeHuntingWhenStartedElsewhereInWilderness() = runBlocking<Unit> {
        skillProfile()
        `when`(bot.position).thenReturn(Position(3141, 3690))
        bot.wildernessLevel = 22
        val destinations = mutableListOf<Position>()
        val fallback = Position(0, 0)
        val handler = bot.actionHandler
        doAnswer { call ->
            val destination = call.getArgument<Position>(0)
            destinations += destination
            doReturn(destination).`when`(bot).position
            bot.wildernessLevel = levelAt(destination)
            true
        }.`when`(handler).webWalkTo(any(Position::class.java) ?: fallback, eq(0), eq(1),
            eq(BankAccess.NONE) ?: BankAccess.NONE)
        val session = PkBotScript(bot, 5.minutes)
        assertTrue(session.enterWild())
        assertEquals(1, destinations.size)
        assertTrue(session.allowsHuntingAt(destinations.single()))
        assertTrue(session.enterWild())
        assertEquals(1, destinations.size)
    }

    @Test fun failedReturnToEdgevilleCannotAuthorizeHuntingAtTheOldLocation() = runBlocking<Unit> {
        skillProfile()
        `when`(bot.position).thenReturn(Position(3141, 3690))
        bot.wildernessLevel = 22
        val session = PkBotScript(bot, 5.minutes)
        assertFalse(session.enterWild())
        assertFalse(session.allowsPursuit(target))
    }

    @Test fun nonSkillPurposesKeepTheirExistingPatrolDestinations() {
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.VALUE, Styles.TRIBRID, Build.MAIN))
        val session = PkBotScript(bot, 5.minutes)
        assertTrue(session.allowsHuntingAt(Position(3141, 3690)))
        assertTrue(session.allowsHuntingAt(Position(3100, 9992)))
        assertTrue(session.patrolAnchors().all { anchor -> PkBotScript.PkArea.ALL.any { anchor in it.anchors } })
    }

    @Test fun combatReflexRetainsTheSkillPatrolBoundary() = runBlocking<Unit> {
        skillProfile()
        `when`(bot.position).thenReturn(Position(3141, 3690))
        `when`(target.position).thenReturn(Position(3142, 3690))
        val session = InventoryProductionFixtures.active(PkBotScript(bot, 5.minutes))
        bot.scriptStack.pushTail(session)
        `when`(bot.combat.inCombat()).thenReturn(true)
        `when`(bot.combat.target).thenReturn(target)
        `when`(bot.emotions.getCombatResponse(target)).thenReturn(CombatBotScript.InitialState.ATTACK)
        assertFalse(BotReflex().process(bot))
        assertSame(session, bot.scriptStack.findActive(PkBotScript::class.java).get())
        val child = bot.scriptStack.current() as CombatBotScript
        InventoryProductionFixtures.active(child)
        assertTrue(child.init(false))
        assertTrue(child.isTerminated())
        verify(bot.combat, never()).attack(target)
    }
}
