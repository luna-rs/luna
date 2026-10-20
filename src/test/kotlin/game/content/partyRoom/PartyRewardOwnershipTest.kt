package game.content.partyRoom

import api.bot.script.InventoryProductionFixtures
import api.predef.ctx
import api.predef.world
import game.content.partyRoom.dropParty.DropPartyOption
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.GroundItemList
import io.luna.game.model.item.Item
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.util.function.Predicate

/**
 * Exercises the actual native merge algorithm so claimed rewards retain their correct ground entity and owner.
 *
 * @author lare96
 */
class PartyRewardOwnershipTest {
    @Test fun mergedRewardUsesNewOwnerStackRatherThanAnExistingCoincidentDrop() {
        exercise(false)
    }
    @Test fun rejectedOverflowCannotClaimAnExistingStackAsItsReward() {
        exercise(true)
    }
    private fun exercise(overflow: Boolean) {
        InventoryProductionFixtures.initialize()
        val previousItems = world.items
        val locator = world.locator
        val items = GroundItemList(world)
        val owner = InventoryProductionFixtures.bot()
        val other = InventoryProductionFixtures.bot()
        val position = Position(2736, 3475)
        val id = PartyRoomTestFixtures.stackId
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().mergeStackableGroundItems()).thenReturn(true)
        `when`(world.items).thenReturn(items)
        doAnswer {
            val predicate = it.getArgument<Predicate<GroundItem>>(2)
            items.filter { candidate -> predicate.test(candidate) }.toSet()
        }.`when`(locator).findItems(any(Locatable::class.java), eq(0), any<Predicate<GroundItem>>())
        try {
            mockStatic(Luna::class.java).use { luna ->
                luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
                val existing = GroundItem(ctx, id, if (overflow) Int.MAX_VALUE else 100, position,
                    ChunkUpdatableView.localView(owner))
                val foreign = GroundItem(ctx, id, 200, position, ChunkUpdatableView.localView(other))
                assertTrue(items.register(existing))
                assertTrue(items.register(foreign))
                val reward = DropPartyOption.spawnReward(Item(id, 100), position, owner)
                if (overflow) {
                    assertNull(reward)
                    assertTrue(items.any { it === existing })
                } else {
                    assertNotNull(reward)
                    assertNotSame(existing, reward)
                    assertNotSame(foreign, reward)
                    assertEquals(200, reward!!.amount)
                    assertTrue(reward.view.isViewableFor(owner))
                    assertFalse(reward.view.isViewableFor(other))
                }
                assertTrue(items.any { it === foreign })
                assertEquals(200, foreign.amount)
            }
        } finally {
            `when`(world.items).thenReturn(previousItems)
            doReturn(emptySet<GroundItem>()).`when`(locator)
                .findItems(any(Locatable::class.java), eq(0), any<Predicate<GroundItem>>())
        }
    }
}
