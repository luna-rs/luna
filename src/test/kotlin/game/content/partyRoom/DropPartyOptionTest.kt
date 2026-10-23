package game.content.partyRoom

import api.bot.script.InventoryProductionFixtures
import game.content.partyRoom.dropParty.DropPartyInterface
import game.content.partyRoom.dropParty.DropPartyOption
import game.content.partyRoom.dropParty.DropPartyOption.depositItems
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.task.TaskManager
import io.luna.game.task.TaskState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Duration

/**
 * Defines a tradeable stack for native transfer and countdown fixtures.
 *
 * @author lare96
 */
internal object PartyRoomTestFixtures {
    /**
     * The lazily registered tradeable stack identifier used by native transfer tests.
     */
    val stackId by lazy {
        InventoryProductionFixtures.initialize()
        val id = (1 until ItemDefinition.SIZE).first { ItemDefinition.ALL.get(it).isEmpty }
        val definition = mock(ItemDefinition::class.java)
        `when`(definition.id()).thenReturn(id)
        `when`(definition.name).thenReturn("Party Room fixture stack")
        `when`(definition.isStackable).thenReturn(true)
        `when`(definition.isTradeable).thenReturn(true)
        `when`(definition.value).thenReturn(2_000_000)
        ItemDefinition.ALL.storeDefinition(definition)
        id
    }
}

/**
 * Verifies chest ownership conservation and the native countdown used by player-funded parties.
 *
 * @author lare96
 */
class DropPartyOptionTest {
    /**
     * Initializes the native fixtures and resets the state used by each test.
     */
    @BeforeEach
    fun setup() {
        InventoryProductionFixtures.initialize()
        DropPartyOption.chest.items.clear()
        DropPartyOption.chest.locked = false
        PartyRoomTestFixtures.stackId
        if (io.luna.game.model.def.WidgetDefinition.ALL.get(2156).isEmpty) {
            val definition = mock(io.luna.game.model.def.WidgetDefinition::class.java)
            `when`(definition.id()).thenReturn(2156)
            io.luna.game.model.def.WidgetDefinition.ALL.storeDefinition(definition)
        }
    }

    /**
     * Creates a regular player with the Party Room deposit interface open.
     */
    private fun player() = InventoryProductionFixtures.bot().also {
        `when`(it.overlays.has(DropPartyInterface::class.java)).thenReturn(true)
        `when`(it.rights).thenReturn(io.luna.game.model.mob.PlayerRights.PLAYER)
    }

    /**
     * Verifies that a full chest leaves all staged items owned by the player.
     */
    @Test
    fun fullChestLeavesEveryStagedItemWithThePlayer() {
        repeat(DropPartyOption.chest.items.capacity()) { assertTrue(DropPartyOption.chest.items.add(Item(526))) }
        val player = player()
        player.depositItems.add(Item(PartyRoomTestFixtures.stackId, 100))
        assertFalse(DropPartyOption.confirmDeposit(player))
        assertEquals(100, player.depositItems.computeAmountForId(PartyRoomTestFixtures.stackId))
        assertEquals(0, DropPartyOption.chest.items.computeAmountForId(PartyRoomTestFixtures.stackId))
    }

    /**
     * Verifies that a partially full chest accepts only the items it can hold.
     */
    @Test
    fun partiallyFullChestTransfersOnlyWhatFits() {
        repeat(DropPartyOption.chest.items.capacity() - 1) { DropPartyOption.chest.items.add(Item(526)) }
        val player = player()
        player.depositItems.add(Item(PartyRoomTestFixtures.stackId, 100))
        player.depositItems.add(Item(592))
        assertTrue(DropPartyOption.confirmDeposit(player))
        assertEquals(100, DropPartyOption.chest.items.computeAmountForId(PartyRoomTestFixtures.stackId))
        assertEquals(0, player.depositItems.computeAmountForId(PartyRoomTestFixtures.stackId))
        assertEquals(1, player.depositItems.computeAmountForId(592))
    }

    /**
     * Verifies that chest locks and closed interfaces prevent transfers of staged items.
     */
    @Test
    fun lockedChestAndClosedInterfaceLeaveStagedItemsUntouched() {
        val player = player()
        player.depositItems.add(Item(PartyRoomTestFixtures.stackId, 100))
        DropPartyOption.chest.locked = true
        assertFalse(DropPartyOption.confirmDeposit(player))
        DropPartyOption.chest.locked = false
        `when`(player.overlays.has(DropPartyInterface::class.java)).thenReturn(false)
        assertFalse(DropPartyOption.confirmDeposit(player))
        assertEquals(100, player.depositItems.computeAmountForId(PartyRoomTestFixtures.stackId))
    }

    /**
     * Verifies that stale slots and invalid quantities cannot transfer inventory items.
     */
    @Test
    fun staleSlotAndNegativeAmountCannotStageAnInventoryItem() {
        val player = player()
        player.inventory.add(Item(PartyRoomTestFixtures.stackId, 100))
        assertFalse(DropPartyOption.stageDeposit(player, 526, 0, 1))
        assertFalse(DropPartyOption.stageDeposit(player, PartyRoomTestFixtures.stackId, 0, -1))
        assertFalse(DropPartyOption.stageDeposit(player, PartyRoomTestFixtures.stackId, -1, 1))
        assertEquals(100, player.inventory.computeAmountForId(PartyRoomTestFixtures.stackId))
        assertEquals(0, player.depositItems.size())
    }

    /**
     * Verifies that rejected staging preserves inventory and successful staging caps requested amount.
     */
    @Test
    fun rejectedStagingPreservesInventoryAndSuccessfulStagingCapsRequestedAmount() {
        val player = player()
        repeat(player.depositItems.capacity()) { player.depositItems.add(Item(526)) }
        player.inventory.add(Item(PartyRoomTestFixtures.stackId, 100))
        assertFalse(DropPartyOption.stageDeposit(player, PartyRoomTestFixtures.stackId, 0, Int.MAX_VALUE))
        assertEquals(100, player.inventory.computeAmountForId(PartyRoomTestFixtures.stackId))
        player.depositItems.clear()
        assertTrue(DropPartyOption.stageDeposit(player, PartyRoomTestFixtures.stackId, 0, Int.MAX_VALUE))
        assertEquals(0, player.inventory.computeAmountForId(PartyRoomTestFixtures.stackId))
        assertEquals(100, player.depositItems.computeAmountForId(PartyRoomTestFixtures.stackId))
    }

    /**
     * Verifies that closing returns unconfirmed donations and preserves overflow.
     */
    @Test
    fun closingReturnsUnconfirmedDonationsAndPreservesOverflow() {
        val player = player()
        player.depositItems.add(Item(PartyRoomTestFixtures.stackId, 100))
        DropPartyInterface().onClose(player)
        assertEquals(100, player.inventory.computeAmountForId(PartyRoomTestFixtures.stackId))
        assertEquals(0, player.depositItems.size())
        player.inventory.clear()
        repeat(player.inventory.capacity()) { player.inventory.add(Item(526)) }
        player.depositItems.add(Item(PartyRoomTestFixtures.stackId, 100))
        DropPartyInterface().onClose(player)
        assertEquals(100, player.depositItems.computeAmountForId(PartyRoomTestFixtures.stackId))
    }

    /**
     * Verifies that value multiplies in long arithmetic and selects the high value countdown.
     */
    @Test
    fun valueMultipliesInLongArithmeticAndSelectsTheHighValueCountdown() {
        DropPartyOption.chest.items.add(Item(PartyRoomTestFixtures.stackId, Int.MAX_VALUE))
        val value = 2_000_000L * Int.MAX_VALUE
        assertEquals(value, DropPartyOption.computeTotalCost())
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        mockStatic(Luna::class.java).use { luna ->
            luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
            assertEquals(Duration.ofMinutes(60), DropPartyOption.computeCountdownTime(value))
            assertEquals(Duration.ofMinutes(1), DropPartyOption.computeCountdownTime(10_000_000))
            assertEquals(Duration.ofMinutes(5), DropPartyOption.computeCountdownTime(10_000_001))
        }
    }

    /**
     * Verifies that one minute countdown waits one hundred game ticks.
     */
    @Test
    fun oneMinuteCountdownWaitsOneHundredGameTicks() {
        val manager = TaskManager()
        val task = DropPartyOption.DropPartyCountdown(mutableListOf(), Duration.ofMinutes(1))
        manager.schedule(task)
        repeat(99) { manager.runTaskIteration() }
        assertEquals(TaskState.RUNNING, task.state)
        assertEquals(1, DropPartyOption.secondsLeft)
        manager.runTaskIteration()
        assertEquals(TaskState.CANCELLED, task.state)
        assertEquals(0, DropPartyOption.secondsLeft)
    }
}
