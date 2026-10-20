package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.predef.world
import api.predef.ext.totalItemAmount
import com.google.common.collect.ImmutableSet
import com.google.gson.JsonObject
import game.content.partyRoom.PartyRoomTestFixtures
import game.content.partyRoom.dropParty.DropPartyOption
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.item.Item
import io.luna.game.model.item.economy.ItemPriceData
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPreference
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

/**
 * Checks both volunteering personalities against real wanted-stock retention, surplus thresholds and value caps.
 *
 * @author lare96
 */
class PartyRoomDonationsTest {
    private lateinit var bot: Bot
    private var id = 0

    @BeforeEach fun setup() {
        InventoryProductionFixtures.initialize()
        bot = InventoryProductionFixtures.bot()
        id = PartyRoomTestFixtures.stackId
        `when`(bot.rights).thenReturn(io.luna.game.model.mob.PlayerRights.PLAYER)
        val wanted = WantedItemDefinition(id, 1_500, 1_000, -1, -1, WantedItemPriority.STANDARD, ImmutableSet.of())
        val preferences = BotPreference(mutableMapOf(BotActivity.MERCHANTING to 0.5, BotActivity.MINIGAMES to 0.7),
            mutableSetOf(), mutableMapOf(id to wanted), mutableSetOf(), mutableMapOf())
        preferences.bot = bot
        `when`(bot.preferences).thenReturn(preferences)
        `when`(world.economy.prices[id]).thenReturn(mock(ItemPriceData::class.java))
        `when`(world.economy.getRealPrice(id)).thenReturn(10.0)
        DropPartyOption.chest.locked = false
    }

    @Test fun kindMinigameEnthusiastsAndDumbBotsAreIndependentVolunteeringPaths() {
        assertFalse(PartyRoomDonations.eligible(bot))
        `when`(bot.personality.isKind).thenReturn(true)
        assertTrue(PartyRoomDonations.eligible(bot))
        `when`(bot.personality.isKind).thenReturn(false)
        `when`(bot.personality.isDumb).thenReturn(true)
        assertTrue(PartyRoomDonations.eligible(bot))
        assertTrue(PartyRoomDonations.candidates(bot).isEmpty(), "Being dumb cannot donate nonexistent stock")
    }

    @Test fun trimThresholdDoesNotDonateNormalSuppliesAndHigherMinimumIsRetained() {
        bot.bank.add(Item(id, 2_500))
        assertTrue(PartyRoomDonations.candidates(bot).isEmpty())
        bot.bank.add(Item(id, 1))
        val donation = PartyRoomDonations.candidates(bot).single()
        assertEquals(1_001, donation.amount)
        assertEquals(2_501, bot.totalItemAmount(id), "Planning cannot move any items")
    }

    @Test fun largeSurplusIsCappedByDonationValueBudget() {
        bot.bank.add(Item(id, 100_000))
        assertEquals(10_000, PartyRoomDonations.candidates(bot).single().amount)
        `when`(world.economy.getRealPrice(id)).thenReturn(Double.NaN)
        assertTrue(PartyRoomDonations.candidates(bot).isEmpty())
    }

    @Test fun expiredOrAlreadyAttemptedDonationCannotWithdrawAgain() = runBlocking<Unit> {
        assertTrue(DepositPartyRoomItemBotScript(bot, id, 100, 0).run())
        assertTrue(DepositPartyRoomItemBotScript(bot, id, 100, System.currentTimeMillis() + 60_000, true).run())
        verifyNoInteractions(bot.actionHandler)
    }

    @Test fun savedAttemptRetainsAuthorizationWithoutRepeatingConfirmation() = runBlocking<Unit> {
        val original = DepositPartyRoomItemBotScript(bot, id, 100, System.currentTimeMillis() + 60_000, true)
        val json = JsonObject().also { original.snapshot().save(it) }
        val restored = DepositPartyRoomItemBotScript.DonationData().also { it.load(json) }
        assertEquals(id, restored.itemId)
        assertEquals(100, restored.amount)
        assertTrue(restored.attempted)
        assertTrue(DepositPartyRoomItemBotScript(bot, restored).run())
        verifyNoInteractions(bot.actionHandler)
    }
    @Test fun depositUsesNativeStageAndConfirmationWithoutTouchingRetainedStock() = runBlocking<Unit> {
        exerciseDeposit(false)
    }

    @Test fun rejectedConfirmationReturnsTheStagedDonationAndDoesNotRetry() = runBlocking<Unit> {
        exerciseDeposit(true)
    }

    private suspend fun exerciseDeposit(reject: Boolean) {
        val chest = mock(io.luna.game.model.`object`.GameObject::class.java)
        val handler = bot.actionHandler
        val banking = handler.banking
        val interactions = handler.interactions
        val output = bot.botClient.output
        val overlays = bot.overlays
        val locator = world.locator
        val position = game.content.partyRoom.PartyRoom.TELEPORT_POSITIONS.first()
        var open = false
        if (io.luna.game.model.def.WidgetDefinition.ALL.get(2156).isEmpty) {
            val definition = mock(io.luna.game.model.def.WidgetDefinition::class.java)
            `when`(definition.id()).thenReturn(2156)
            io.luna.game.model.def.WidgetDefinition.ALL.storeDefinition(definition)
        }
        DropPartyOption.chest.items.clear()
        bot.bank.add(Item(id, 4_000))
        `when`(banking.travelToBankDepositAll()).thenReturn(true)
        `when`(banking.travelToBankWithdraw(anyList())).thenAnswer {
            assertTrue(bot.bank.remove(Item(id, 1_000)))
            assertTrue(bot.inventory.add(Item(id, 1_000)))
            true
        }
        `when`(handler.widgets.clickCloseInterface()).thenReturn(true)
        `when`(handler.travelTo(api.bot.zone.SubZone.PARTY_ROOM)).thenReturn(true)
        `when`(overlays.has(game.content.partyRoom.dropParty.DropPartyInterface::class.java)).thenAnswer { open }
        doReturn(setOf(chest)).`when`(locator).findObjects(
            same(position), eq(20), any<java.util.function.Predicate<io.luna.game.model.`object`.GameObject>>())
        `when`(interactions.interact(1, chest)).thenAnswer { open = true; true }
        `when`(output.sendItemWidgetClick(eq(4), anyInt(), eq(5064), eq(id))).thenAnswer {
            DropPartyOption.stageDeposit(bot, id, it.getArgument(1), 1_000)
        }
        `when`(output.clickButton(2246)).thenAnswer {
            DropPartyOption.chest.locked = reject
            DropPartyOption.confirmDeposit(bot)
        }
        doAnswer {
            game.content.partyRoom.dropParty.DropPartyInterface().onClose(bot)
            open = false
            null
        }.`when`(overlays).closeWindows()
        val runTask = io.luna.game.task.Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer {
            val task = it.getArgument<io.luna.game.task.Task>(0)
            repeat(100) { if (task.state != io.luna.game.task.TaskState.CANCELLED) runTask.invoke(task) }
            null
        }.`when`(world).schedule(any(io.luna.game.task.Task::class.java))
        val script = DepositPartyRoomItemBotScript(bot, id, 1_000, System.currentTimeMillis() + 60_000)
        try {
            assertTrue(kotlinx.coroutines.withTimeout(5_000) { script.run() })
            script.finish()
            assertEquals(3_000, bot.bank.computeAmountForId(id))
            assertEquals(if (reject) 1_000 else 0, bot.inventory.computeAmountForId(id))
            assertEquals(if (reject) 0 else 1_000, DropPartyOption.chest.items.computeAmountForId(id))
            assertEquals(0, bot.gameDepositSize())
            assertTrue(script.snapshot().attempted)
            assertTrue(script.run())
            verify(output, times(1)).clickButton(2246)
        } finally {
            DropPartyOption.chest.locked = false
            doNothing().`when`(world).schedule(any(io.luna.game.task.Task::class.java))
        }
    }

    private fun Bot.gameDepositSize(): Int = with(game.content.partyRoom.dropParty.DropPartyOption) { depositItems.size() }

}
