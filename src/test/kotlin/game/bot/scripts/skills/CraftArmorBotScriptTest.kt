package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.skills.CraftArmorBotScript.Companion.CraftArmorData
import game.skill.crafting.armorCrafting.CraftArmorActionItem
import game.skill.crafting.armorCrafting.CraftArmorActionItem.Companion.NEEDLE_ID
import game.skill.crafting.armorCrafting.CraftArmorActionItem.Companion.THREAD_ID
import game.skill.crafting.armorCrafting.CraftStuddedActionItem
import game.skill.crafting.armorCrafting.SoftLeatherInterface
import game.skill.crafting.hideTanning.Hide
import io.luna.game.task.Task
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlinx.coroutines.withTimeout
import game.skill.crafting.armorCrafting.HideArmor
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class CraftArmorBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun executeStudded(action: CraftStuddedActionItem) {
        action.plr.actions.submit(action)
        assertFalse(action.run()) // The native action repeats until its paired materials run out.
        assertTrue(action.run())
    }

    @ParameterizedTest @EnumSource(HideArmor::class)
    fun existingPlayerActionsConsumeTheConfiguredRecipe(recipe: HideArmor) {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, *CraftArmorBotScript.supplies(recipe).toTypedArray())
        val hides = recipe.hidesItem
        if (hides != null) {
            InventoryProductionFixtures.inventory(bot, Item(NEEDLE_ID))
            InventoryProductionFixtures.execute(bot, CraftArmorActionItem(bot, recipe, 1))
            assertFalse(bot.inventory.contains(hides.id))
            assertTrue(bot.inventory.contains(NEEDLE_ID))
        } else {
            val input = CraftArmorBotScript.supplies(recipe).first().id
            executeStudded(CraftStuddedActionItem(bot, recipe, input))
            assertFalse(bot.inventory.contains(input))
            assertFalse(bot.inventory.contains(CraftStuddedActionItem.STUDS))
        }
        assertTrue(bot.inventory.contains(recipe.id))
        verify(bot.crafting).addExperience(recipe.exp)
    }

    @Test fun scriptSelectsEveryExistingRecipeThroughItsPlayerInteraction() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }.`when`(world).schedule(any(Task::class.java))
        try {
            for (recipe in HideArmor.entries) {
                val bot = InventoryProductionFixtures.bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, *CraftArmorBotScript.supplies(recipe).toTypedArray())
                val hides = recipe.hidesItem
                if (hides == null) {
                    val use = bot.actionHandler.inventory.useItem(CraftStuddedActionItem.STUDS)
                    doAnswer {
                        executeStudded(CraftStuddedActionItem(bot, recipe, CraftArmorBotScript.supplies(recipe).first().id))
                        true
                    }.`when`(use).onItem(CraftArmorBotScript.supplies(recipe).first().id)
                } else {
                    InventoryProductionFixtures.inventory(bot, Item(NEEDLE_ID))
                    `when`(bot.actionHandler.inventory.useItem(NEEDLE_ID).onItem(hides.id)).thenReturn(true)
                    if (recipe.hides!!.first == Hide.SOFT_LEATHER) {
                        `when`(bot.overlays.has(SoftLeatherInterface::class.java)).thenReturn(true)
                        val output = bot.botClient.output
                        doAnswer {
                            InventoryProductionFixtures.execute(bot, CraftArmorActionItem(bot, recipe, 1))
                            null
                        }.`when`(output).clickButton(SoftLeatherInterface.BUTTON_MAP.getValue(recipe).third)
                    } else {
                        `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                        val widgets = bot.actionHandler.widgets
                        val index = HideArmor.HIDE_TO_ARMOR.getValue(recipe.hides!!.first).indexOf(recipe.id)
                        doAnswer {
                            InventoryProductionFixtures.execute(bot, CraftArmorActionItem(bot, recipe, 1))
                            null
                        }.`when`(widgets).clickMakeItem(index, 1)
                    }
                }
                val script = InventoryProductionFixtures.active(CraftArmorBotScript(bot, recipe, 10.minutes))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                assertTrue(bot.inventory.contains(recipe.id), recipe.name)
                assertEquals(0, script.snapshot().failures)
                verify(bot.crafting).addExperience(recipe.exp)
            }
        } finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }

    @Test fun softLeatherUsesOneFiveAndTenForRemainingStock() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }.`when`(world).schedule(any(Task::class.java))
        try {
            val recipe = HideArmor.LEATHER_GLOVES
            val buttons = SoftLeatherInterface.BUTTON_MAP.getValue(recipe)
            for ((stock, quantity, button) in listOf(Triple(3, 1, buttons.third), Triple(7, 5, buttons.second), Triple(10, 10, buttons.first))) {
                val bot = InventoryProductionFixtures.bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(SoftLeatherInterface::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val hides = recipe.hidesItem!!.id
                `when`(bot.actionHandler.inventory.useItem(NEEDLE_ID).onItem(hides)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(NEEDLE_ID), Item(hides, stock), Item(THREAD_ID, 10))
                val output = bot.botClient.output
                doAnswer {
                    val action = CraftArmorActionItem(bot, recipe, quantity)
                    bot.actions.submit(action)
                    repeat(quantity) { action.run() }
                    null
                }.`when`(output).clickButton(button)
                val script = InventoryProductionFixtures.active(CraftArmorBotScript(bot, recipe, 10.minutes))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                verify(output).clickButton(button)
                assertEquals(quantity, bot.inventory.computeAmountForId(recipe.id))
                assertEquals(stock - quantity, bot.inventory.computeAmountForId(hides))
                assertEquals(0, script.snapshot().failures)
            }
        } finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }

    @Test fun partialStuddedStockQualifiesInBothFactoryModesWithoutTools() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(HideArmor.LEATHER_BODY.id, 3), Item(CraftStuddedActionItem.STUDS, 100))
        val script = CraftArmorBotScript(bot, HideArmor.STUDDED_BODY, 10.minutes)
        assertTrue(script.isEligible())
        assertEquals(listOf(Item(HideArmor.LEATHER_BODY.id, 3), Item(CraftStuddedActionItem.STUDS, 3)), script.bankBatch())
        for (training in listOf(true, false)) {
            val selected = CraftingScriptFactory.getProductionScript(bot, 99, training)
            assertInstanceOf(CraftArmorBotScript::class.java, selected)
            assertEquals(HideArmor.STUDDED_BODY, (selected as CraftArmorBotScript).armor)
        }
    }

    @Test fun stackableThreadReservesOneSlotAndLimitsPartialHideBatches() {
        val bot = InventoryProductionFixtures.bot()
        val inventory = spy(bot.inventory)
        doReturn(true).`when`(inventory).stackable(THREAD_ID)
        `when`(bot.inventory).thenReturn(inventory)
        val hide = HideArmor.LEATHER_GLOVES.hidesItem!!.id
        InventoryProductionFixtures.bank(bot, Item(NEEDLE_ID), Item(hide, 100), Item(THREAD_ID, 100))
        val script = CraftArmorBotScript(bot, HideArmor.LEATHER_GLOVES, 10.minutes)
        assertEquals(listOf(Item(NEEDLE_ID), Item(hide, 26), Item(THREAD_ID, 26)), script.bankBatch())
        bot.bank.remove(Item(THREAD_ID, 97))
        assertEquals(listOf(Item(NEEDLE_ID), Item(hide, 3), Item(THREAD_ID, 3)), script.bankBatch())
    }

    @Test fun multiHideRecipesRespectCapacityAndMinimumStock() {
        val bot = InventoryProductionFixtures.bot()
        val recipe = HideArmor.SNAKESKIN_BODY
        val hides = recipe.hidesItem!!
        InventoryProductionFixtures.bank(bot, Item(NEEDLE_ID), Item(THREAD_ID, 10), Item(hides.id, 14))
        val script = CraftArmorBotScript(bot, recipe, 10.minutes)
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(hides.id, 1))
        assertTrue(script.isEligible())
        assertEquals(listOf(Item(NEEDLE_ID), hides, Item(THREAD_ID)), script.bankBatch())
        InventoryProductionFixtures.bank(bot, Item(hides.id, 100))
        assertEquals(listOf(Item(NEEDLE_ID), hides, Item(THREAD_ID)), script.bankBatch())
    }

    @Test fun ownedInventoryMinimumQualifiesAndMissingThreadUsesConsumableTarget() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val recipe = HideArmor.LEATHER_GLOVES
        InventoryProductionFixtures.inventory(bot, Item(NEEDLE_ID), *CraftArmorBotScript.supplies(recipe).toTypedArray())
        assertTrue(CraftArmorBotScript.hasSupplies(bot, recipe))
        assertTrue(InventoryProductionFixtures.active(CraftArmorBotScript(bot, recipe, 10.minutes)).onInit(false))
        bot.inventory.remove(THREAD_ID)
        val script = InventoryProductionFixtures.active(CraftArmorBotScript(bot, recipe, 10.minutes))
        assertFalse(script.onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(THREAD_ID, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(NEEDLE_ID, 3)
    }

    @Test fun missingNeedleUsesToolTargetAndLevelLossStopsProduction() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val recipe = HideArmor.BLACK_DHIDE_BODY
        InventoryProductionFixtures.inventory(bot, *CraftArmorBotScript.supplies(recipe).toTypedArray())
        val script = InventoryProductionFixtures.active(CraftArmorBotScript(bot, recipe, 10.minutes))
        assertFalse(script.onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(NEEDLE_ID, 3)
        InventoryProductionFixtures.inventory(bot, Item(NEEDLE_ID))
        val active = InventoryProductionFixtures.active(CraftArmorBotScript(bot, recipe, 10.minutes))
        assertFalse(active.onBankRequested(false))
        `when`(bot.crafting.level).thenReturn(recipe.level - 1)
        active.executeInZone()
        assertTrue(active.isTerminated())
        `when`(bot.crafting.staticLevel).thenReturn(recipe.level - 1)
        assertFalse(active.isEligible())
    }

    @Test fun sharedRegistrationAndLegacyDataKeepRecipeZonesAndRetryCounters() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val original = CraftArmorData().apply {
                armor = HideArmor.STUDDED_CHAPS
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject().also { original.save(it) }
            val data = CraftArmorData().apply { load(json) }
            val restored = registry.loadScript(CraftArmorBotScript::class.qualifiedName, InventoryProductionFixtures.bot(), data)
            assertInstanceOf(CraftArmorBotScript::class.java, restored)
            val saved = (restored as CraftArmorBotScript).snapshot()
            assertEquals(original.armor, saved.armor)
            assertEquals(original.duration, saved.duration)
            assertEquals(original.zones, saved.zones)
            assertEquals(2, saved.failures)
            assertEquals(1, saved.bankFailures)
            json.remove("failures")
            json.remove("bankFailures")
            val legacy = CraftArmorData().apply { load(json) }
            assertEquals(0, legacy.failures)
            assertEquals(0, legacy.bankFailures)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(previous)
        }
    }

    @Test fun failedInteractionsUseSharedPersistentBudget() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, *CraftArmorBotScript.supplies(HideArmor.STUDDED_BODY).toTypedArray())
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(CraftArmorBotScript(bot, HideArmor.STUDDED_BODY, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(CraftArmorBotScript(bot, script.snapshot())).onInit(true))
    }
}
