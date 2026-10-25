package game.content.fishingTrawler

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.content.fishingTrawler.FishingTrawler.catchRolls
import game.content.fishingTrawler.FishingTrawler.unclaimedCatch
import io.luna.game.model.Position
import io.luna.game.model.Locatable
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.item.Item
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.task.Task
import io.luna.game.task.TaskManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Exercises reward delivery with native inventories and persistent player attributes.
 *
 * @author lare96
 */
class FishingTrawlerTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initialize()
            for (id in listOf(75, 391)) {
                if (VarpDefinition.ALL.get(id).isEmpty) VarpDefinition.ALL.storeDefinition(VarpDefinition(id, 0))
            }
            for (id in listOf(3281, 5596, 4564, 11908)) {
                if (WidgetDefinition.ALL.get(id).isEmpty) {
                    val definition = mock(WidgetDefinition::class.java)
                    `when`(definition.id()).thenReturn(id)
                    WidgetDefinition.ALL.storeDefinition(definition)
                }
            }
        }
    }

    private fun player() = InventoryProductionFixtures.bot().also {
        `when`(it.position).thenReturn(FishingTrawler.DOCK)
        `when`(it.isAlive).thenReturn(true)
        `when`(it.fishing.level).thenReturn(81)
        `when`(it.overlays).thenReturn(AbstractOverlaySet(it))
        var model = it.model
        `when`(it.model).thenAnswer { model }
        doAnswer { call -> model = call.getArgument(0); null }.`when`(it).setModel(any(PlayerModelAnimation::class.java))
    }

    @Test fun fullInventoryKeepsCatchAndRepeatedClaimsDoNotAwardExperienceAgain() {
        val player = player()
        repeat(28) { assertTrue(player.inventory.add(Item(317))) }
        player.catchRolls = 3
        FishingTrawler.claim(player)
        assertEquals(0, player.catchRolls)
        assertEquals(3, player.unclaimedCatch.size)
        verify(player.fishing, times(3)).addExperience(anyDouble())
        FishingTrawler.claim(player)
        assertEquals(3, player.unclaimedCatch.size)
        verify(player.fishing, times(3)).addExperience(anyDouble())
        player.inventory.clear()
        FishingTrawler.claim(player)
        while (player.unclaimedCatch.isNotEmpty()) {
            FishingTrawler.takeCatch(player, 0, FishingTrawler.catchItems(player).first().id, true)
        }
        assertTrue(player.unclaimedCatch.isEmpty())
        assertEquals(3, player.inventory.size())
        verify(player.fishing, times(3)).addExperience(anyDouble())
    }

    @Test fun catchClicksRejectClosedWindowWrongSlotAndWrongItem() {
        val player = player()
        player.unclaimedCatch.addAll(listOf(317, 317, 321))
        FishingTrawler.takeCatch(player, 0, 317, true)
        assertEquals(3, player.unclaimedCatch.size)
        FishingTrawler.claim(player)
        FishingTrawler.takeCatch(player, -1, 317, true)
        FishingTrawler.takeCatch(player, 0, 321, true)
        assertEquals(3, player.unclaimedCatch.size)
        FishingTrawler.takeCatch(player, 0, 317, false)
        assertEquals(1, player.inventory.computeAmountForId(317))
        FishingTrawler.takeCatch(player, 0, 317, true)
        FishingTrawler.takeCatch(player, 0, 317, true)
        assertEquals(2, player.inventory.computeAmountForId(317))
        assertEquals(listOf(321), player.unclaimedCatch)
        player.overlays.closeWindows()
        FishingTrawler.takeCatch(player, 0, 321, true)
        assertEquals(listOf(321), player.unclaimedCatch)
    }

    @Test fun departingCrewDoesNotCauseLargeCatchSharesToBeTruncated() {
        val player = player()
        repeat(28) { player.inventory.add(Item(317)) }
        player.catchRolls = 398
        FishingTrawler.claim(player)
        assertEquals(398, player.unclaimedCatch.size)
        verify(player.fishing, times(398)).addExperience(anyDouble())
    }

    @Test fun lowFishingAndRemoteClaimsKeepEarnedRolls() {
        val player = player()
        player.catchRolls = 10
        `when`(player.fishing.level).thenReturn(14)
        FishingTrawler.claim(player)
        assertEquals(10, player.catchRolls)
        `when`(player.fishing.level).thenReturn(81)
        `when`(player.position).thenReturn(Position(3200, 3200))
        FishingTrawler.claim(player)
        assertEquals(10, player.catchRolls)
        verify(player.fishing, never()).addExperience(anyDouble())
    }

    @Test fun shipwreckIsSeparateFromActiveBoatAndReconnectReturnsToShore() {
        assertFalse(FishingTrawler.atSea(FishingTrawler.WRECK))
        assertTrue(FishingTrawler.inWreck(FishingTrawler.WRECK))
        val player = player()
        player.catchRolls = 8
        `when`(player.position).thenReturn(FishingTrawler.WRECK)
        FishingTrawler.login(player)
        verify(player).move(FishingTrawler.ESCAPE)
        assertEquals(8, player.catchRolls)
    }

    @Test fun sharedDepartureRunsOneVoyageAndRewardsOnlyRemainingCrew() {
        val manager = TaskManager()
        val first = player()
        val second = player()
        val normalModel = first.model
        val positions = mutableMapOf(first to FishingTrawler.DOCK, second to FishingTrawler.DOCK)
        for (player in positions.keys) {
            `when`(player.position).thenAnswer { positions.getValue(player) }
            doAnswer { invocation -> positions[player] = invocation.getArgument(0); null }
                .`when`(player).move(any(Position::class.java))
        }
        doAnswer { manager.schedule(it.getArgument<Task>(0)); null }.`when`(world).schedule(any(Task::class.java))
        val locator = world.locator
        doReturn(emptySet<GameObject>()).`when`(locator).findObjects(any(Locatable::class.java), anyInt(), any())
        try {
            FishingTrawler.board(first)
            FishingTrawler.board(second)
            repeat(101) { manager.runTaskIteration() }
            assertEquals(FishingTrawler.START, first.position)
            assertEquals(FishingTrawler.START, second.position)
            val simulation = TrawlerVoyage { bound -> if (bound == 10) 0 else bound - 1 }
            FishingTrawler::class.java.getDeclaredField("voyage").apply {
                isAccessible = true
                set(null, simulation)
            }
            // A net repair consumes one rope and grants Crafting XP only once.
            val damaged = TrawlerVoyage { 0 }.also { it.tick(1) }
            val voyageField = FishingTrawler::class.java.getDeclaredField("voyage").apply { isAccessible = true }
            voyageField.set(null, damaged)
            repeat(35) { manager.runTaskIteration() }
            first.inventory.add(Item(954))
            val net = mock(GameObject::class.java)
            `when`(net.id).thenReturn(2172)
            doReturn(first.position).`when`(net).position
            FishingTrawler.repair(first, net)
            FishingTrawler.repair(first, net)
            assertFalse(damaged.netRipped)
            assertFalse(first.inventory.contains(954))
            verify(first.crafting).addExperience(5.0)
            voyageField.set(null, simulation)
            repeat(500) { manager.runTaskIteration() }
            FishingTrawler.leave(second)
            repeat(570) { manager.runTaskIteration() }
            assertEquals(FishingTrawler.DOCK, first.position)
            assertTrue(first.catchRolls > 200)
            assertEquals(0, second.catchRolls)
            assertFalse(first.overlays.containsType(io.luna.game.model.mob.overlay.OverlayType.WIDGET_WALKABLE))
            first.catchRolls = 0
            FishingTrawler.abandon(second)
            FishingTrawler.board(first)
            repeat(101) { manager.runTaskIteration() }
            FishingTrawler::class.java.getDeclaredField("voyage").apply {
                isAccessible = true
                set(null, TrawlerVoyage { 0 })
            }
            repeat(200) { manager.runTaskIteration() }
            assertTrue(FishingTrawler.inWreck(first.position))
            assertNotSame(normalModel, first.model)
            assertEquals(0, first.catchRolls)
            FishingTrawler.escape(first)
            assertEquals(FishingTrawler.ESCAPE, first.position)
            assertSame(normalModel, first.model)
            verify(first).damage(2)
            positions[first] = FishingTrawler.DOCK
            FishingTrawler.board(first)
            repeat(101) { manager.runTaskIteration() }
            FishingTrawler.abandon(first)
            repeat(50) { manager.runTaskIteration() }
            assertEquals(FishingTrawler.ESCAPE, first.position)
            verify(first, times(4)).unlock()
        } finally {
            FishingTrawler.abandon(first)
            FishingTrawler.abandon(second)
            repeat(1100) { manager.runTaskIteration() }
            doNothing().`when`(world).schedule(any(Task::class.java))
        }
    }

    @Test fun laddersRejectRemoteObjectsAndWrongInteractionIds() {
        val player = player()
        `when`(player.position).thenReturn(FishingTrawler.START)
        val ladder = mock(GameObject::class.java)
        `when`(ladder.id).thenReturn(2174)
        `when`(ladder.position).thenReturn(Position(1899, 4825))
        FishingTrawler.ladder(player, ladder, true)
        `when`(ladder.position).thenReturn(FishingTrawler.START)
        `when`(ladder.id).thenReturn(2175)
        FishingTrawler.ladder(player, ladder, true)
        verify(player, never()).move(any(Position::class.java))
    }
}
