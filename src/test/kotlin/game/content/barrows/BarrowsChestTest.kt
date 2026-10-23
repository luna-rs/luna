package game.content.barrows

import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.item.Inventory
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.Attribute
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.random.Random

/**
 * Exercises the two chest interactions, real inventory capacity, saved pending loot and duplicate prevention.
 *
 * @author lare96
 */
class BarrowsChestTest {
    private val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
    private val manager = PersistentVarpManager(player)
    private lateinit var inventory: Inventory
    private val first = object : Random() { override fun nextBits(bitCount: Int) = 0 }
    private val noRoll = object : Random() { override fun nextBits(bitCount: Int): Int = error("Loot rerolled") }

    @BeforeEach
    fun setup() {
        for (id in listOf(452, 453)) {
            if (VarpDefinition.ALL.get(id).isEmpty) VarpDefinition.ALL.storeDefinition(VarpDefinition(id, 0))
        }
        for (id in BarrowsRewardData.uniqueItems.values.flatten() + 1) {
            if (ItemDefinition.ALL.get(id).isEmpty) {
                val definition = mock(ItemDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                ItemDefinition.ALL.storeDefinition(definition)
            }
        }
        `when`(player.attributes()).thenReturn(AttributeMap())
        `when`(player.varpManager).thenReturn(manager)
        `when`(player.cachedVarps).thenReturn(HashMap())
        `when`(player.position).thenReturn(Position(3551, 9694))
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.isAlive).thenReturn(true)
        inventory = Inventory(player).also { it.setListeners() }
        `when`(player.inventory).thenReturn(inventory)
        manager.setValue(PersistentVarp.BARROWS, BarrowsMaze.randomize(0, Random(688)))
        manager.setValue(PersistentVarp.BARROWS_KILLS, BarrowsVarbit.MONSTER_SCORE.write(63, 1000))
    }

    private fun open() {
        BarrowsChest.search(player, noRoll)
        assertEquals(1, BarrowsVarbit.CHEST_OPEN.read(manager.getValue(PersistentVarp.BARROWS_KILLS)))
    }

    @Test
    fun openThenSearchAwardsOnceAndNextDigStartsAnotherRun() {
        open()
        assertEquals(28, inventory.computeRemainingSize())
        BarrowsChest.search(player, first)
        assertEquals(21, inventory.computeRemainingSize())
        assertTrue(BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK != 0)
        repeat(3) { BarrowsChest.search(player, noRoll) }
        assertEquals(21, inventory.computeRemainingSize())
        verify(player, times(3)).sendMessage("The chest is empty.")
        BarrowsRun.enterCrypt(player, Random(42))
        assertEquals(0, manager.getValue(PersistentVarp.BARROWS_KILLS))
        assertEquals(0, BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK)
    }

    @Test
    fun fullInventoryRetainsSameLootAcrossMultipleSaveLoadCycles() {
        open()
        inventory.add(Item(1, 28))
        BarrowsChest.search(player, first)
        assertEquals(0, BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK)
        repeat(2) {
            val gson = Attribute.getGsonInstance()
            val json = gson.toJson(player.attributes())
            val restored = gson.fromJson(json, AttributeMap::class.java)
            `when`(player.attributes()).thenReturn(restored)
            BarrowsChest.restore(player)
        }
        BarrowsChest.search(player, noRoll)
        assertEquals(28, inventory.computeAmountForId(1))
        inventory.remove(Item(1, 7))
        BarrowsChest.search(player, noRoll)
        assertEquals(0, inventory.computeRemainingSize())
        assertEquals(1, inventory.computeAmountForId(4708))
        assertTrue(BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK != 0)
    }

    @Test
    fun emptyRewardStillConsumesRunAndDistantOrDeadPlayersCannotLoot() {
        manager.setValue(PersistentVarp.BARROWS_KILLS, BarrowsVarbit.CHEST_OPEN.write(0, 1))
        `when`(player.position).thenReturn(Position(3200, 3200))
        BarrowsChest.search(player, noRoll)
        `when`(player.position).thenReturn(Position(3551, 9694))
        `when`(player.isAlive).thenReturn(false)
        BarrowsChest.search(player, noRoll)
        assertEquals(0, BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK)
        `when`(player.isAlive).thenReturn(true)
        BarrowsChest.search(player, first)
        assertTrue(BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK != 0)
        assertEquals(28, inventory.computeRemainingSize())
    }
}
