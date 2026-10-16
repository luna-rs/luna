package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.event.Matcher
import api.predef.*
import api.bot.zone.SubZone
import com.google.gson.JsonObject
import engine.bot.coordinator.GeneralActivityCoordinator
import game.bot.scripts.UseLampBotScript.Companion.LampData
import game.item.experienceLamp.ExperienceLampInterface
import game.item.experienceLamp.ExperienceLampInterface.InterfaceSkill
import io.luna.game.event.Event
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class UseLampBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initialize()
            if (WidgetDefinition.ALL.get(2808).isEmpty) {
                val widget = mock(WidgetDefinition::class.java)
                `when`(widget.id()).thenReturn(2808)
                WidgetDefinition.ALL.storeDefinition(widget)
            }
            val lampVarp = PersistentVarp.XP_LAMP.clientId
            if (VarpDefinition.ALL.get(lampVarp).isEmpty) {
                val varp = mock(VarpDefinition::class.java)
                `when`(varp.id()).thenReturn(lampVarp)
                VarpDefinition.ALL.storeDefinition(varp)
            }
            Class.forName("game.item.experienceLamp.Lamp").getConstructor(Array<String>::class.java)
                .newInstance(emptyArray<String>() as Any)
        }

        /** Dispatches the actual compiled player callbacks without a network client. */
        private fun <E : Event> dispatch(matcher: Matcher<E, Int>, event: E) {
            val match = Matcher::class.java.getDeclaredMethod("match", Event::class.java)
            match.isAccessible = true
            assertEquals(true, match.invoke(matcher, event))
        }

        private fun button(bot: Bot, id: Int) = dispatch(Matcher.get<ButtonClickEvent, Int>(), ButtonClickEvent(bot, id))

        private fun lampBot(): Bot {
            val bot = InventoryProductionFixtures.bot()
            val varps = PersistentVarpManager(bot)
            `when`(bot.varpManager).thenReturn(varps)
            `when`(bot.overlays.player).thenReturn(bot)
            `when`(bot.overlays.has(ExperienceLampInterface::class.java)).thenReturn(true)
            return bot
        }
    }

    @Test fun everyInterfaceSkillConsumesOneLampAndAwardsItsExistingLevelBasedReward() {
        for (skill in InterfaceSkill.values()) {
            val bot = lampBot()
            val rewardSkill = bot.skills.getSkill(skill.skill)
            `when`(rewardSkill.staticLevel).thenReturn(37)
            InventoryProductionFixtures.inventory(bot, Item(UseLampBotScript.LAMP, 2))
            button(bot, skill.button)
            assertEquals(skill.varpValue, bot.varpManager.getValue(PersistentVarp.XP_LAMP))
            button(bot, UseLampBotScript.CONFIRM_BUTTON)
            assertEquals(1, bot.inventory.computeAmountForId(UseLampBotScript.LAMP))
            verify(rewardSkill).addExperience(370.0)
            verify(bot.overlays).closeWindows()
        }
    }

    @Test fun confirmationWithoutLampOrAfterFailedRemovalCannotAwardExperience() {
        for (failedRemoval in listOf(false, true)) {
            val bot = lampBot()
            val rewardSkill = bot.skills.getSkill(SKILL_CRAFTING)
            button(bot, InterfaceSkill.CRAFTING.button)
            if (failedRemoval) {
                InventoryProductionFixtures.inventory(bot, Item(UseLampBotScript.LAMP))
                val inventory = spy(bot.inventory)
                `when`(bot.inventory).thenReturn(inventory)
                doReturn(false).`when`(inventory).remove(UseLampBotScript.LAMP)
            }
            button(bot, UseLampBotScript.CONFIRM_BUTTON)
            verify(rewardSkill, never()).addExperience(anyDouble())
            if (failedRemoval) assertEquals(1, bot.inventory.computeAmountForId(UseLampBotScript.LAMP))
        }
    }

    @Test fun closedInterfaceCannotSelectOrRedeemALamp() {
        val bot = lampBot()
        InventoryProductionFixtures.inventory(bot, Item(UseLampBotScript.LAMP))
        `when`(bot.overlays.has(ExperienceLampInterface::class.java)).thenReturn(false)
        button(bot, InterfaceSkill.HERBLORE.button)
        button(bot, UseLampBotScript.CONFIRM_BUTTON)
        assertEquals(0, bot.varpManager.getValue(PersistentVarp.XP_LAMP))
        assertEquals(1, bot.inventory.computeAmountForId(UseLampBotScript.LAMP))
    }

    @Test fun coordinatorSelectsOwnedLampsAndKeepsPreferredSupportedSkill() {
        val bot = InventoryProductionFixtures.bot()
        assertNull(GeneralActivityCoordinator.getScript(bot))
        InventoryProductionFixtures.bank(bot, Item(UseLampBotScript.LAMP, 40))
        `when`(bot.preferences.skills).thenReturn(setOf(SKILL_HERBLORE, -1))
        val script = GeneralActivityCoordinator.getScript(bot) as UseLampBotScript
        assertEquals(InterfaceSkill.HERBLORE, script.skill)
        assertTrue(script.isEligible())
        assertEquals(listOf(Item(UseLampBotScript.LAMP, 28)), script.bankBatch())
        `when`(bot.preferences.skills).thenReturn(emptySet())
        assertNotNull((GeneralActivityCoordinator.getScript(bot) as UseLampBotScript).skill)
    }

    @Test fun scriptUsesPlayerButtonsAndWaitsForConfirmedSelectionBeforeRedeeming() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }.`when`(world).schedule(any(Task::class.java))
        try {
            val bot = lampBot()
            InventoryProductionFixtures.inventory(bot, Item(UseLampBotScript.LAMP, 2))
            val rewardSkill = bot.skills.getSkill(SKILL_HERBLORE)
            `when`(rewardSkill.staticLevel).thenReturn(99)
            `when`(bot.personality.isDextrous).thenReturn(true)
            `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
            `when`(bot.actionHandler.inventory.clickItem(1, UseLampBotScript.LAMP)).thenAnswer {
                dispatch(Matcher.get<ItemFirstClickEvent, Int>(), ItemFirstClickEvent(bot, UseLampBotScript.LAMP, 0, 3214))
                true
            }
            `when`(bot.output.clickButton(anyInt())).thenAnswer { button(bot, it.getArgument(0)); true }
            val script = InventoryProductionFixtures.active(UseLampBotScript(bot, InterfaceSkill.HERBLORE, 10.minutes))
            assertTrue(withTimeout(8_000) { script.executeInZone() })
            assertEquals(1, bot.inventory.computeAmountForId(UseLampBotScript.LAMP))
            assertEquals(0, script.snapshot().failures)
            verify(rewardSkill).addExperience(990.0)
            val order = inOrder(bot.output)
            order.verify(bot.output).clickButton(InterfaceSkill.HERBLORE.button)
            order.verify(bot.output).clickButton(UseLampBotScript.CONFIRM_BUTTON)
            bot.varpManager.setValue(PersistentVarp.XP_LAMP, InterfaceSkill.ATTACK.varpValue)
            `when`(bot.output.clickButton(InterfaceSkill.HERBLORE.button)).thenReturn(false)
            script.executeInZone()
            assertEquals(1, bot.inventory.computeAmountForId(UseLampBotScript.LAMP))
            verify(bot.output, times(1)).clickButton(UseLampBotScript.CONFIRM_BUTTON)
            assertEquals(1, script.snapshot().failures)
        } finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }

    @Test fun exhaustedStockAndUnsafeBotsStopWithoutRequestingLamps() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        assertFalse(InventoryProductionFixtures.active(UseLampBotScript(bot, InterfaceSkill.PRAYER, 10.minutes)).onInit(false))
        verifyNoInteractions(bot.preferences)
        InventoryProductionFixtures.bank(bot, Item(UseLampBotScript.LAMP))
        for (state in 0..2) {
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            assertFalse(InventoryProductionFixtures.active(UseLampBotScript(bot, InterfaceSkill.PRAYER, 10.minutes)).onInit(false))
        }
    }

    @Test fun partialBankBatchAndUnverifiedWithdrawalUseNativeBankingPolicy() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(UseLampBotScript.LAMP, 3))
        val script = InventoryProductionFixtures.active(UseLampBotScript(bot, InterfaceSkill.PRAYER, 10.minutes))
        assertEquals(listOf(Item(UseLampBotScript.LAMP, 3)), script.bankBatch())
        assertTrue(script.onInit(false))
        `when`(bot.actionHandler.banking.withdrawAll(anyList())).thenReturn(true)
        script.onBankOpen(false)
        verify(bot.actionHandler.banking).clickBankingMode(false)
        assertTrue(script.isTerminated())
    }

    @Test fun centralRegistrationAndJsonRetainSkillZonesDurationAndExhaustedBudgets() = runBlocking<Unit> {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = LampData().apply {
                selectedSkill = InterfaceSkill.SLAYER.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 3
                bankFailures = 2
            }
            val json = JsonObject()
            data.save(json)
            val loaded = LampData().apply { load(json) }
            val restored = registry.loadScript(UseLampBotScript::class.qualifiedName, InventoryProductionFixtures.bot(), loaded) as UseLampBotScript
            val snapshot = restored.snapshot()
            assertEquals(data.selectedSkill, snapshot.selectedSkill)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
            assertFalse(InventoryProductionFixtures.active(restored).onInit(true))
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}
