package game.content.randomEvents

import api.bot.script.InventoryProductionFixtures
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import game.skill.woodcutting.cutTree.Axe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

/**
 * Verifies tool conservation across equipped hazards, full inventories, recovery and stale repairs.
 *
 * @author lare96
 */
class WoodcuttingRandomEventsTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() {
            InventoryProductionFixtures.initializeCombat()
        }
    }

    private fun player(): Player = InventoryProductionFixtures.bot().also {
        val equipment = Equipment(it)
        `when`(it.equipment).thenReturn(equipment)
    }

    @Test
    fun equippedAxeReplacementPreservesAFullInventoryAndAllOtherAxes() {
        val player = player()
        repeat(28) { player.inventory[it] = Item(Axe.BRONZE.id) }
        player.equipment[Equipment.WEAPON] = Item(Axe.RUNE.id)
        assertTrue(WoodcuttingRandomEvents.replaceAxe(player, Axe.RUNE, WoodcuttingRandomEvents.HANDLE))
        assertEquals(WoodcuttingRandomEvents.HANDLE, player.equipment[Equipment.WEAPON]!!.id)
        assertEquals(28, player.inventory.computeAmountForId(Axe.BRONZE.id))
        assertEquals(28, player.inventory.size())
        assertFalse(WoodcuttingRandomEvents.replaceAxe(player, Axe.RUNE, WoodcuttingRandomEvents.HANDLE))
    }

    @Test
    fun inventoryAxeReplacementKeepsItsSlotAndDoesNotTakeAnotherTier() {
        val player = player()
        player.inventory[11] = Item(Axe.RUNE.id)
        player.inventory[20] = Item(Axe.DRAGON.id)
        assertTrue(WoodcuttingRandomEvents.replaceAxe(player, Axe.RUNE, 506))
        assertEquals(506, player.inventory[11]!!.id)
        assertEquals(Axe.DRAGON.id, player.inventory[20]!!.id)
        assertEquals(2, player.inventory.size())
    }

    @Test
    fun headAndHandleReattachWithNoFreeSlotAndCannotBeReplayed() {
        val player = player()
        repeat(28) { player.inventory[it] = Item(Axe.BRONZE.id) }
        player.inventory[0] = Item(WoodcuttingRandomEvents.HANDLE)
        player.inventory[27] = Item(520)
        assertTrue(WoodcuttingRandomEvents.reattach(player, 520))
        assertEquals(Axe.RUNE.id, player.inventory[0]!!.id)
        assertNull(player.inventory[27])
        assertEquals(27, player.inventory.size())
        assertFalse(WoodcuttingRandomEvents.reattach(player, 520))
        assertEquals(1, player.inventory.computeAmountForId(Axe.RUNE.id))
    }

    @Test
    fun absentHandleDoesNotConsumeARecoveredHead() {
        val player = player()
        player.inventory[0] = Item(6743)
        assertFalse(WoodcuttingRandomEvents.reattach(player, 6743))
        assertEquals(6743, player.inventory[0]!!.id)
        player.inventory[1] = Item(WoodcuttingRandomEvents.HANDLE)
        assertTrue(WoodcuttingRandomEvents.reattach(player, 6743))
        assertEquals(1, player.inventory.computeAmountForId(Axe.DRAGON.id))
    }

    @Test
    fun paidRepairRechecksFundsBeforeTakingTheAxeOrCoins() {
        val player = player()
        player.inventory[5] = Item(506)
        for (coins in listOf(1, 426)) {
            player.inventory[6] = Item(995, coins)
            assertFalse(WoodcuttingRandomEvents.repair(player, WoodcuttingRandomEvents.Parts.RUNE))
            assertEquals(506, player.inventory[5]!!.id)
            assertEquals(coins, player.inventory.computeAmountForId(995))
        }
        player.inventory[6] = Item(995, 427)
        assertTrue(WoodcuttingRandomEvents.repair(player, WoodcuttingRandomEvents.Parts.RUNE))
        assertEquals(Axe.RUNE.id, player.inventory[5]!!.id)
        assertFalse(player.inventory.contains(995))
        assertFalse(WoodcuttingRandomEvents.repair(player, WoodcuttingRandomEvents.Parts.RUNE))
        assertEquals(1, player.inventory.computeAmountForId(Axe.RUNE.id))
    }

    @Test
    fun staleRepairOfferDoesNotChargeForAnAbsentBrokenAxe() {
        val player = player()
        player.inventory[0] = Item(995, 1000)
        assertFalse(WoodcuttingRandomEvents.repair(player, WoodcuttingRandomEvents.Parts.RUNE))
        assertEquals(1000, player.inventory.computeAmountForId(995))
    }

    @Test
    fun freeRepairWorksWithAFullInventory() {
        val player = player()
        repeat(28) { player.inventory[it] = Item(Axe.BRONZE.id) }
        player.inventory[17] = Item(494)
        assertTrue(WoodcuttingRandomEvents.repair(player, WoodcuttingRandomEvents.Parts.BRONZE))
        assertEquals(28, player.inventory.computeAmountForId(Axe.BRONZE.id))
    }

    @Test
    fun everyWornHazardFormHasAZeroBonusEquipmentDefinition() {
        val ids = WoodcuttingRandomEvents.Parts.entries.map { it.broken } + WoodcuttingRandomEvents.HANDLE
        for (id in ids) {
            val definition = EquipmentDefinition.ALL.retrieve(id)
            assertEquals(Equipment.WEAPON, definition.index)
            repeat(12) { assertEquals(0, definition.getBonus(it)) }
        }
    }
}
