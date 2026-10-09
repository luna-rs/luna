package game.bot.scripts.combat

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.PkingCoordinator
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.combat.PkBotScript.Companion.PkData
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotReflex
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.movement.NavigationResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PkBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.asPlr()).thenReturn(it)
        `when`(it.isAlive).thenReturn(true)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        `when`(it.healthPercent).thenReturn(100)
        `when`(it.position).thenReturn(Position(3092, 3517))
        `when`(it.equipment.weapon).thenReturn(Item(1323))
        `when`(it.equipment[Equipment.CHEST]).thenReturn(Item(1115))
        `when`(it.equipment[Equipment.LEGS]).thenReturn(Item(1067))
        `when`(it.combat.weapon.type).thenReturn(Weapon.SCIMITAR)
        `when`(it.combat.isAttackable).thenReturn(true)
    }

    private fun inWild(bot: Bot) {
        bot.wildernessLevel = 5
        `when`(bot.position).thenReturn(Position(3092, 3558))
    }

    private fun readyInWild(): Bot = bot().also {
        InventoryProductionFixtures.inventory(it, Item(379, 8))
        inWild(it)
    }

    private fun target(bot: Bot): Player = bot().also {
        inWild(it)
        `when`(it.isAlive).thenReturn(true)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        `when`(it.isViewableFrom(bot)).thenReturn(true)
        `when`(it.combat.isAttackable).thenReturn(true)
        `when`(bot.controllers.checkCombat(it)).thenReturn(true)
        `when`(bot.combat.checkMultiCombat(it)).thenReturn(true)
    }

    @Test fun pkDispatchQueuesOneEligibleSessionWithCooldown() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(379, 8))
        assertSame(PkingCoordinator, BotActivity.PKING.coordinator)
        assertTrue(PkBotScript.isEligible(bot))
        PkingCoordinator.accept(bot)
        PkingCoordinator.accept(bot)
        verify(bot.scriptStack, times(1)).push(isA(PkBotScript::class.java))
    }

    @Test fun insufficientFoodOrUnsupportedGearDoesNotQueuePk() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(379, 7), Item(315, 200))
        assertFalse(PkBotScript.isEligible(bot))
        PkingCoordinator.accept(bot)
        verify(bot.scriptStack, never()).push(any())
        InventoryProductionFixtures.bank(bot, Item(379))
        assertTrue(PkBotScript.isEligible(bot))
        `when`(bot.combat.weapon.isRanged).thenReturn(true)
        assertFalse(PkBotScript.isEligible(bot))
        `when`(bot.combat.weapon.isRanged).thenReturn(false)
        `when`(bot.equipment[Equipment.CHEST]).thenReturn(null)
        assertFalse(PkBotScript.isEligible(bot))
    }

    @Test fun newSessionsDoNotStartInWildernessOrDuringCombat() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(379, 8))
        `when`(bot.combat.inCombat()).thenReturn(true)
        PkingCoordinator.accept(bot)
        verify(bot.scriptStack, never()).push(any())
        inWild(bot)
        assertFalse(PkBotScript.isEligible(bot))
    }

    @Test fun millisecondSnapshotRoundTripRetainsDurationAndFailureBudgets() {
        val bot = bot()
        val data = PkData().apply {
            duration = 15.minutes
            returning = true
            routeFailures = 2
            emptySearches = 59
            escapeAttempts = 2
        }
        val script = PkBotScript(bot, data)
        val json = JsonObject().also { script.snapshot().save(it) }
        val loaded = PkData().apply { load(json) }
        assertTrue(loaded.duration.inWholeMilliseconds in 899_000..900_000)
        assertTrue(loaded.returning)
        assertEquals(2, loaded.routeFailures)
        assertEquals(59, loaded.emptySearches)
        assertEquals(2, loaded.escapeAttempts)
        val restored = PkBotScript(bot, loaded)
        assertTrue(restored.snapshot().duration > 14.minutes)
    }

    @Test fun legacyDurationLoadsAndExpiredOrOversizedDurationsAreBounded() {
        val data = PkData().apply { load(JsonObject().apply { addProperty("duration", 60_000) }) }
        assertEquals(60.seconds, data.duration)
        assertFalse(data.returning)
        assertEquals(0, data.escapeAttempts)
        assertTrue(PkBotScript(bot(), 500.minutes).snapshot().duration.inWholeMilliseconds in 1_799_000..1_800_000)
        val expired = PkBotScript(bot(), 15.minutes).apply { expireAt = System.currentTimeMillis() - 1 }
        assertEquals(kotlin.time.Duration.ZERO, expired.snapshot().duration)
    }

    @Test fun actualLaunchRegistrationRestoresPkInTheScriptStack() {
        val manager = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(manager)
        InventoryProductionFixtures.register("Scripts")
        val bot = bot()
        val stack = BotScriptStack(bot, manager)
        stack.push(PkBotScript(bot, 10.minutes))
        val saved = stack.save()
        assertEquals(PkData::class.java.name, saved.single().scriptDataClass)
        stack.load(saved)
        assertInstanceOf(PkBotScript::class.java, stack.current())
        assertTrue((stack.current() as PkBotScript).snapshot().duration > 9.minutes)
    }

    @Test fun targetSelectionRejectsSelfSafePlayersDeadPlayersControllersAndSingleCombatConflicts() {
        val bot = readyInWild()
        val other = target(bot)
        val script = PkBotScript(bot, 15.minutes)
        assertTrue(script.isValidTarget(other))
        assertFalse(script.isValidTarget(bot))
        other.wildernessLevel = 0
        assertFalse(script.isValidTarget(other))
        other.wildernessLevel = 5
        `when`(other.isAlive).thenReturn(false)
        assertFalse(script.isValidTarget(other))
        `when`(other.isAlive).thenReturn(true)
        `when`(bot.controllers.checkCombat(other)).thenReturn(false)
        assertFalse(script.isValidTarget(other))
        `when`(bot.controllers.checkCombat(other)).thenReturn(true)
        `when`(bot.combat.checkMultiCombat(other)).thenReturn(false)
        assertFalse(script.isValidTarget(other))
    }

    @Test fun selectingTargetQueuesChildWithoutDirectAttackOrDiscardingParent() {
        val bot = readyInWild()
        val stack = BotScriptStack(bot, BotScriptManager())
        `when`(bot.scriptStack).thenReturn(stack)
        val script = PkBotScript(bot, 15.minutes)
        stack.push(script)
        val other = target(bot)
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(setOf(other))
        assertTrue(script.searchAndAttack())
        assertEquals(2, stack.size())
        assertInstanceOf(CombatBotScript::class.java, stack.current())
        verify(bot.combat, never()).attack(any())
    }

    @Test fun expiryEndsCombatChildBeforeItCanSendAnotherAttack() = runBlocking<Unit> {
        val bot = readyInWild()
        val other = target(bot)
        val parent = PkBotScript(bot, 15.minutes).apply { expireAt = 0 }
        val child = CombatBotScript(bot, other, pkSession = parent)
        assertTrue(child.init(false))
        assertTrue(child.run())
        child.finish()
        verify(bot.botClient.output, never()).sendPlayerInteraction(anyInt(), any())
        verify(bot.combat, never()).attack(any())
        verify(bot.combat).target = null
        verify(bot.navigator).cancel()
    }

    @Test fun exhaustedEscapeBudgetEndsWithoutForcedTeleportOrFurtherNavigation() = runBlocking<Unit> {
        val bot = readyInWild()
        val script = PkBotScript(bot, PkData().apply { returning = true; escapeAttempts = 3 })
        assertTrue(script.run())
        verify(bot.navigator, never()).navigate(any(Position::class.java), anyBoolean())
        verify(bot.actionHandler, never()).travelTo(api.bot.zone.SubZone.HOME)
        verify(bot.botClient.output, never()).sendCommand(anyString())
    }

    @Test fun failedEntryReachesItsBudgetAndPersistsReturnState() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(379, 8))
        `when`(bot.navigator.navigate(any(Position::class.java), anyBoolean()))
            .thenReturn(CompletableFuture.completedFuture(NavigationResult.NO_VALID_PATH))
        val script = PkBotScript(bot, PkData().apply { duration = 15.minutes; routeFailures = 2 })
        assertFalse(script.run())
        assertTrue(script.snapshot().returning)
        assertEquals(3, script.snapshot().routeFailures)
        assertTrue(script.run())
    }

    @Test fun emptyTargetSearchEndsAfterItsBudget() = runBlocking<Unit> {
        val bot = readyInWild()
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(emptySet())
        val script = PkBotScript(bot, PkData().apply { duration = 15.minutes; emptySearches = 59 })
        assertFalse(script.run())
        assertTrue(script.snapshot().returning)
        assertEquals(60, script.snapshot().emptySearches)
    }

    @Test fun preparationFailureEndsAndRestoredWildSessionNeverBanks() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(379, 8))
        `when`(bot.actionHandler.banking.travelToBankDepositAll()).thenReturn(false)
        assertTrue(PkBotScript(bot, 15.minutes).init(false))
        clearInvocations(bot.actionHandler.banking)
        inWild(bot)
        val restored = PkBotScript(bot, 15.minutes)
        assertFalse(restored.init(true))
        assertTrue(restored.snapshot().returning)
        verify(bot.actionHandler.banking, never()).travelToBankDepositAll()
    }

    @Test fun deathEndsSessionAndPauseCancelsItsWalking() = runBlocking<Unit> {
        val bot = readyInWild()
        val script = PkBotScript(bot, 15.minutes)
        script.paused()
        verify(bot.navigator).cancel()
        verify(bot.walking).clear()
        `when`(bot.isAlive).thenReturn(false)
        assertTrue(script.run())
    }

    @Test fun combatReflexLeavesPkResponseToItsBoundedSession() {
        val bot = readyInWild()
        val script = PkBotScript(bot, 15.minutes)
        `when`(bot.scriptStack.current()).thenReturn(script)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertTrue(BotReflex().process(bot))
        verify(bot.scriptStack, never()).pushHead(any())
    }

    @Test fun validPkCombatCycleSendsPlayerAttackPacketWithoutDirectAttack() = runBlocking<Unit> {
        val bot = readyInWild()
        val other = target(bot)
        val parent = PkBotScript(bot, 15.minutes)
        val child = CombatBotScript(bot, other, pkSession = parent)
        assertTrue(child.run()) // No combat feedback: child finishes instead of looping forever.
        verify(bot.botClient.output).sendPlayerInteraction(1, other)
        verify(bot.combat, never()).attack(any())
    }

    @Test fun failedCombatEngagementConsumesParentRetryBudget() = runBlocking<Unit> {
        val bot = readyInWild()
        val other = target(bot)
        `when`(bot.actionHandler.interactions.interact(1, other)).thenReturn(false)
        val parent = PkBotScript(bot, PkData().apply { duration = 15.minutes; routeFailures = 2 })
        assertTrue(CombatBotScript(bot, other, pkSession = parent).init(false))
        assertTrue(parent.snapshot().returning)
        assertEquals(3, parent.snapshot().routeFailures)
    }

    @Test fun successfulPreparationUsesOwnedFoodAndClosesBank() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(379, 8))
        `when`(bot.actionHandler.banking.travelToBankDepositAll()).thenReturn(true)
        `when`(bot.actionHandler.banking.withdrawAnyFood(8, 10, Int.MAX_VALUE, false)).thenAnswer {
            InventoryProductionFixtures.inventory(bot, Item(379, 8))
            true
        }
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
        assertFalse(PkBotScript(bot, 15.minutes).init(false))
        verify(bot.actionHandler.banking).clickBankingMode(false)
        verify(bot.actionHandler.widgets).clickCloseInterface()
    }

    @Test fun resumeInWildernessRetainsOriginalDeadlineAndDoesNotPrepareAgain() = runBlocking<Unit> {
        val bot = readyInWild()
        val script = PkBotScript(bot, 15.minutes)
        val deadline = script.expireAt
        script.paused()
        assertFalse(script.init(true))
        assertEquals(deadline, script.expireAt)
        assertFalse(script.snapshot().returning)
        verify(bot.actionHandler.banking, never()).travelToBankDepositAll()
    }
}
