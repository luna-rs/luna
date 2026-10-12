package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import game.bot.scripts.skills.TanHideBotScript.Companion.TanningData
import game.skill.crafting.hideTanning.Hide
import game.skill.crafting.hideTanning.TanInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import io.luna.game.model.mob.bot.script.BotScriptStack
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class TanningRetriesBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.crafting.staticLevel).thenReturn(1)
        `when`(it.personality.isDextrous).thenReturn(true)
        val inventory = spy(it.inventory)
        doReturn(true).`when`(inventory).stackable(995)
        `when`(it.inventory).thenReturn(inventory)
    }

    private fun script(bot: Bot, failures: Int = 0): TanHideBotScript {
        val data = TanningData().apply {
            duration = 8.minutes
            zones = mutableListOf(SubZone.AL_KHARID_BANK)
            this.failures = failures
        }
        val script = InventoryProductionFixtures.active(TanHideBotScript(bot, data))
        ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(script, SubZone.AL_KHARID_BANK) }
        TanHideBotScript::class.java.getDeclaredField("tannerNpc").apply { isAccessible = true; set(script, mock(Npc::class.java)) }
        script.withdraw()
        return script
    }

    @Test fun failedInteractionsExhaustAndPersistTheProductionBudget() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(Hide.SOFT_LEATHER.hide, 3), Item(995, 6))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        val script = script(bot)
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        val json = JsonObject().also { script.snapshot().save(it) }
        val data = TanningData().apply { load(json) }
        assertEquals(3, data.failures)
        assertFalse(InventoryProductionFixtures.active(TanHideBotScript(bot, data)).onInit(true))
    }

    @Test fun unresolvedBankRequestsExhaustAndPersistTheirOwnBudget() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Hide.SOFT_LEATHER.hide, 3), Item(995, 6))
        val script = script(bot)
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        assertTrue(script.isTerminated())
        val json = JsonObject().also { script.snapshot().save(it) }
        val data = TanningData().apply { load(json) }
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(TanHideBotScript(bot, data)).onInit(true))
    }

    @Test fun thirdAllowedBankRequestCanSucceedAndResetOnlyBankingFailures() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Hide.SOFT_LEATHER.hide, 3), Item(995, 6))
        val script = script(bot, failures = 2)
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        val batch = listOf(Item(995, 6), Item(Hide.SOFT_LEATHER.hide, 3))
        val banking = bot.actionHandler.banking
        doAnswer {
            for (item in batch) {
                assertTrue(bot.bank.remove(item))
                assertTrue(bot.inventory.add(item))
            }
            true
        }.`when`(banking).withdrawAll(batch)
        script.onBankOpen(true)
        assertEquals(0, script.snapshot().bankFailures)
        assertEquals(2, script.snapshot().failures)
        assertFalse(script.onBankRequested(false))
    }

    @Test fun nativeTanAllConsumesCoinsAndHidesAndResetsProductionFailures() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }.`when`(world).schedule(any(Task::class.java))
        val previousButtons = TanInterface.hideToMakeAll.toMap()
        try {
            val nativeClass = Class.forName("game.skill.crafting.hideTanning.TanHides")
            val native = nativeClass.getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
            val tan = nativeClass.getMethod("tan", Player::class.java, Hide::class.java, Int::class.javaPrimitiveType)
            val hide = Hide.SOFT_LEATHER
            TanInterface.hideToMakeAll[hide] = 14793
            val bot = bot()
            InventoryProductionFixtures.inventory(bot, Item(hide.hide, 3), Item(995, 6))
            val script = script(bot, failures = 2)
            val tanner = TanHideBotScript::class.java.getDeclaredField("tannerNpc").apply { isAccessible = true }.get(script) as Npc
            `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
            `when`(bot.actionHandler.interactions.interact(2, tanner)).thenReturn(true)
            `when`(bot.overlays.player).thenReturn(bot)
            `when`(bot.overlays.has(TanInterface::class.java)).thenReturn(true)
            val output = bot.botClient.output
            doAnswer { tan.invoke(native, bot, hide, Int.MAX_VALUE); null }.`when`(output).clickButton(14793)
            assertTrue(withTimeout(5_000) { script.executeInZone() })
            assertEquals(3, bot.inventory.computeAmountForId(hide.tan))
            assertFalse(bot.inventory.contains(hide.hide))
            assertFalse(bot.inventory.contains(995))
            assertEquals(0, script.snapshot().failures)
        } finally {
            TanInterface.hideToMakeAll.clear()
            TanInterface.hideToMakeAll.putAll(previousButtons)
            doNothing().`when`(world).schedule(any(Task::class.java))
        }
    }

    @Test fun actualLegacyStackAndNewRetryDataRestoreThroughTheExistingRegistry() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try { InventoryProductionFixtures.register("Scripts") }
        finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
        for (data in listOf(
            ZonedBotScriptData().apply { duration = 6.minutes; zones = mutableListOf(SubZone.AL_KHARID_BANK) },
            TanningData().apply { duration = 7.minutes; zones = mutableListOf(SubZone.AL_KHARID_BANK); failures = 2; bankFailures = 1 })) {
            val json = JsonObject().also { data.save(it) }
            val stack = BotScriptStack(bot(), registry)
            stack.load(listOf(BotScriptSnapshot(0, TanHideBotScript::class.java.name, data.javaClass.name, json)))
            assertEquals(1, stack.size())
            val restored = stack.current() as TanHideBotScript
            assertEquals(data.duration, restored.snapshot().duration)
            assertEquals(data.zones, restored.snapshot().zones)
            assertEquals(if (data is TanningData) data.failures else 0, restored.snapshot().failures)
            assertEquals(if (data is TanningData) data.bankFailures else 0, restored.snapshot().bankFailures)
        }
    }
}
