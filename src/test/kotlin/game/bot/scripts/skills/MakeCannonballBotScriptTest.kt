package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.SmithingScriptFactory
import game.bot.scripts.skills.MakeCannonballBotScript.Companion.CannonballData
import game.bot.scripts.skills.MakeCannonballBotScript.Companion.DEFAULT_ZONES
import game.skill.smithing.Smithing
import game.skill.smithing.makeCannonball.MakeCannonballAction
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.AMMO_MOULD
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.STEEL_BAR
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.CANNONBALL
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.AMOUNT
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.LEVEL
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.EXPERIENCE
import io.luna.game.action.ActionState
import io.luna.game.model.EntityState
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.util.OptionalInt
import java.util.function.Predicate
import kotlin.time.Duration.Companion.minutes

@Timeout(180)
class MakeCannonballBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initialize()
            // Smithing scans the complete object cache; fixture repositories initially contain empty slots.
            for (id in 0 until GameObjectDefinition.SIZE) {
                if (GameObjectDefinition.ALL.get(id).isEmpty) {
                    GameObjectDefinition.ALL.storeDefinition(GameObjectDefinition(id, "Fixture", "", 1, 1, 0,
                        false, false, false, OptionalInt.empty(),
                        if (id == 399) ImmutableList.of("Smelt") else ImmutableList.of(), false, null))
                }
            }
        }
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        `when`(it.smithing.staticLevel).thenReturn(LEVEL)
        `when`(it.smithing.level).thenReturn(LEVEL)
        `when`(it.personality.isDextrous).thenReturn(true)
        val inventory = spy(it.inventory)
        doReturn(true).`when`(inventory).stackable(CANNONBALL)
        `when`(it.inventory).thenReturn(inventory)
    }

    private fun script(bot: Bot, zone: SubZone = SubZone.AL_KHARID_BANK) =
        InventoryProductionFixtures.active(MakeCannonballBotScript(bot, 10.minutes)).also {
            ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(it, zone) }
        }

    @Test fun bothFactoryModesRequireLevelAndOwnedMouldAndBarsAndBatchReservesOneToolSlot() {
        val bot = bot()
        val script = MakeCannonballBotScript(bot, 10.minutes)
        InventoryProductionFixtures.bank(bot, Item(STEEL_BAR, 100))
        assertFalse(script.isEligible())
        assertNull(SmithingScriptFactory.getCannonballScript(bot, LEVEL))
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(AMMO_MOULD, 3))
        assertEquals(listOf(Item(AMMO_MOULD), Item(STEEL_BAR, 27)), script.bankBatch())
        assertInstanceOf(MakeCannonballBotScript::class.java,
            SmithingScriptFactory.getTrainingScript(bot, LEVEL, mutableListOf()))
        assertInstanceOf(MakeCannonballBotScript::class.java,
            SmithingScriptFactory.getProfitScript(bot, LEVEL, mutableListOf()))
        assertNull(SmithingScriptFactory.getCannonballScript(bot, LEVEL - 1))
        `when`(bot.smithing.staticLevel).thenReturn(LEVEL - 1)
        assertFalse(script.isEligible())
        assertNull(SmithingScriptFactory.getCannonballScript(bot, LEVEL))
        bot.bank.remove(Item(STEEL_BAR, 97))
        assertEquals(listOf(Item(AMMO_MOULD), Item(STEEL_BAR, 3)), script.bankBatch())
        bot.bank.remove(Item(STEEL_BAR, 3))
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.inventory(bot, Item(STEEL_BAR), Item(AMMO_MOULD))
        `when`(bot.smithing.staticLevel).thenReturn(LEVEL)
        assertTrue(script.isEligible())
    }

    @Test fun missingStartupSuppliesRequestRealisticTargets() = runBlocking<Unit> {
        for (missing in listOf(STEEL_BAR, AMMO_MOULD)) {
            val bot = bot()
            val owned = if (missing == STEEL_BAR) AMMO_MOULD else STEEL_BAR
            InventoryProductionFixtures.inventory(bot, Item(owned))
            assertFalse(script(bot).onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(missing, if (missing == AMMO_MOULD) 3 else 1_000)
            verify(bot.preferences, never()).raiseWantedItemTarget(eq(owned), anyInt())
        }
    }

    @Test fun furnaceInteractionRunsRealTimedActionsForFullAndPartialInventoriesOnBothRoutes() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            assertTrue(Smithing.FURNACE_OBJECTS.isNotEmpty())
            for (zone in DEFAULT_ZONES) for (bars in listOf(1, 27)) {
                val bot = bot()
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(AMMO_MOULD), Item(STEEL_BAR, bars))
                val furnace = mock(GameObject::class.java)
                val furnaceId = Smithing.FURNACE_OBJECTS.first()
                `when`(furnace.id).thenReturn(furnaceId)
                `when`(furnace.position).thenReturn(zone.inside)
                val decoy = mock(GameObject::class.java)
                `when`(decoy.id).thenReturn(-1)
                `when`(decoy.position).thenReturn(zone.inside)
                val outside = mock(GameObject::class.java)
                val distantPosition = Position(1, 1)
                `when`(outside.id).thenReturn(furnaceId)
                `when`(outside.position).thenReturn(distantPosition)
                val locator = fixtureWorld.locator
                val center = zone.area.centerPosition
                doAnswer {
                    val predicate = it.getArgument<Predicate<GameObject>>(2)
                    assertTrue(predicate.test(furnace))
                    assertFalse(predicate.test(decoy))
                    assertFalse(predicate.test(outside))
                    setOf(furnace)
                }.`when`(locator).findObjects(eq(center), eq(zone.area.tileRadius), any())
                val interaction = bot.actionHandler.inventory.useItem(STEEL_BAR)
                doAnswer {
                    val action = MakeCannonballAction(bot)
                    bot.actions.submit(action)
                    repeat(20) { bot.actions.process() }
                    assertEquals(ActionState.COMPLETED, action.state)
                    true
                }.`when`(interaction).onObject(furnace)
                val script = script(bot, zone)
                if (bars == 27) assertTrue(bot.inventory.isFull)
                assertFalse(script.onBankRequested(false))
                repeat(bars) { assertTrue(script.executeInZone()) }
                verify(interaction, times(bars)).onObject(furnace)
                assertEquals(bars * AMOUNT, bot.inventory.computeAmountForId(CANNONBALL))
                assertEquals(1, bot.inventory.computeAmountForId(AMMO_MOULD))
                assertFalse(bot.inventory.contains(STEEL_BAR))
                verify(bot.smithing, times(bars)).addExperience(EXPERIENCE)
                assertEquals(0, script.snapshot().failures)
                assertTrue(script.onBankRequested(false))
            }
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
            `when`(fixtureWorld.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        }
    }

    @Test fun levelLossSafetyMissingMouldAndStackOverflowPreventInteractions() = runBlocking<Unit> {
        for (state in 0..4) {
            val bot = bot()
            InventoryProductionFixtures.inventory(bot, Item(STEEL_BAR), Item(AMMO_MOULD))
            when (state) {
                0 -> `when`(bot.smithing.level).thenReturn(LEVEL - 1)
                1 -> `when`(bot.health).thenReturn(0)
                2 -> `when`(bot.isLocked).thenReturn(true)
                3 -> bot.inventory.remove(Item(AMMO_MOULD))
                4 -> InventoryProductionFixtures.inventory(bot, Item(CANNONBALL, Int.MAX_VALUE - 3))
            }
            val script = script(bot)
            assertTrue(script.executeInZone())
            verify(bot.actionHandler.inventory, never()).useItem(STEEL_BAR, -1)
            assertEquals(1, bot.inventory.computeAmountForId(STEEL_BAR))
            assertEquals(state == 0, script.isTerminated())
            if (state >= 3) assertTrue(script.onBankRequested(false))
        }
    }

    @Test fun missingFurnacesExhaustPersistentBudgetAndFinishInterruptsPendingProduction() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(STEEL_BAR), Item(AMMO_MOULD))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
        `when`(world.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        val script = script(bot)
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        assertEquals(3, script.snapshot().failures)
        assertFalse(InventoryProductionFixtures.active(MakeCannonballBotScript(bot, script.snapshot())).onInit(true))
        val action = MakeCannonballAction(bot)
        bot.actions.submit(action)
        bot.actions.process()
        script.finish()
        assertEquals(ActionState.INTERRUPTED, action.state)
        verify(bot).unlock()
        assertFalse(bot.inventory.contains(CANNONBALL))
    }

    @Test fun bankingRequiresVerifiedUnnotedSuppliesAndPreservesItsRetryBudget() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(STEEL_BAR, 3), Item(AMMO_MOULD))
        val script = script(bot)
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        assertFalse(InventoryProductionFixtures.active(MakeCannonballBotScript(bot, script.snapshot())).onInit(true))
        val requested = listOf(Item(AMMO_MOULD), Item(STEEL_BAR, 3))
        `when`(bot.actionHandler.banking.withdrawAll(requested)).thenReturn(true)
        val failed = script(bot)
        failed.onBankOpen(false)
        assertTrue(failed.isTerminated())
        InventoryProductionFixtures.inventory(bot, *requested.toTypedArray())
        val successful = script(bot)
        assertTrue(successful.onInit(false))
        assertTrue(successful.onBankRequested(false))
        successful.onBankOpen(false)
        assertFalse(successful.isTerminated())
        assertEquals(0, successful.snapshot().bankFailures)
        assertFalse(successful.onBankRequested(false))
        verify(bot.actionHandler.banking, times(2)).clickBankingMode(false)
    }

    @Test fun centralRegistrationAndJsonRestoreConfigurationAndRetryBudgets() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = CannonballData().apply {
                duration = 7.minutes
                zones = mutableListOf(SubZone.FALADOR_WEST_BANK)
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject().also { data.save(it) }
            val restored = registry.loadScript(MakeCannonballBotScript::class.qualifiedName, bot(),
                CannonballData().apply { load(json) }) as MakeCannonballBotScript
            assertEquals(data.duration, restored.snapshot().duration)
            assertEquals(data.zones, restored.snapshot().zones)
            assertEquals(data.failures, restored.snapshot().failures)
            assertEquals(data.bankFailures, restored.snapshot().bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}
