package game.content.fishingTrawler

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.FishingTrawlerBotScript
import game.content.fishingTrawler.FishingTrawler
import game.content.fishingTrawler.FishingTrawler.catchRolls
import game.content.fishingTrawler.FishingTrawler.unclaimedCatch
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportSequence
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.map.MapIndex
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.Locatable
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.Position
import io.luna.game.task.Task
import io.luna.game.task.TaskManager
import io.luna.LunaContext
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.Mockito.mock

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

/**
 * Tests boat failure, repairs, timing and reward accounting independently of map loading.
 *
 * @author lare96
 */
class TrawlerVoyageTest {
    @Test
    fun `unattended leaks flood then sink the boat and prohibit later actions`() {
        val boat = TrawlerVoyage { 0 }
        boat.tick(1)
        assertEquals(4, (0..15).count(boat::leaking))
        repeat(30) { boat.tick(1) }
        assertTrue(boat.flooded)
        assertTrue(boat.sunk)
        assertTrue(boat.finished)
        assertFalse(boat.repairHull(0))
        assertFalse(boat.repairNet())
        assertFalse(boat.bail())
        assertEquals(0, boat.catch)
    }

    @Test
    fun `repair cannot consume a second supply for the same leak`() {
        val boat = TrawlerVoyage { 0 }
        boat.tick(2)
        assertEquals(7, (0..15).count(boat::leaking))
        assertTrue(boat.repairHull(0))
        assertFalse(boat.repairHull(0))
        assertFalse(boat.repairHull(-1))
        assertTrue(boat.repairNet())
        assertFalse(boat.repairNet())
    }

    @Test
    fun `successful voyage ends at two hundred control cycles with equal catch shares`() {
        val boat = TrawlerVoyage { bound -> if (bound == 10) 0 else bound - 1 }
        repeat(199) { boat.tick(2) }
        assertFalse(boat.finished)
        assertEquals(398, boat.catch)
        boat.tick(2)
        assertTrue(boat.finished)
        assertFalse(boat.sunk)
        assertEquals(199, boat.catch / 2)
        boat.tick(2)
        assertEquals(398, boat.catch)
    }

    @Test
    fun `empty boat stops without processing or rewards`() {
        val boat = TrawlerVoyage { error("No random roll expected") }
        boat.tick(0)
        assertTrue(boat.finished)
        assertEquals(0, boat.cycles)
        assertEquals(0, boat.catch)
    }
}

/**
 * Checks the port's object identifiers and hull coordinates against the installed revision 377 cache.
 *
 * @author lare96
 */
class TrawlerMapTest {
    @Test fun cacheContainsAllHullNetLadderAndEscapeInteractions() {
        assumeTrue(Files.exists(Path.of("data/game/cache/main_file_cache.dat")), "Local revision 377 cache required")
        Cache().use { cache ->
            cache.open()
            val decoder = MapDecoder()
            val indices = ImmutableList.builder<MapIndex>()
            decoder.decode(cache, indices)
            val regions = listOf(Position(1889, 4825), Position(1953, 4825), Position(2017, 4825),
                FishingTrawler.DOCK).map { it.region }.toSet()
            decoder.handle(mock(LunaContext::class.java), cache,
                ImmutableList.copyOf(indices.build().filter { it.region in regions }))
            val objects = cache.mapIndexTable.objectSet.objects
            for (base in listOf(1856, 1984)) {
                for (x in 29..36) for (y in listOf(4823, 4826)) {
                    assertTrue(objects.any { it.position == Position(base + x, y) && it.objectId == 2177 },
                        "Missing hull at ${base + x},$y")
                }
                for (id in listOf(2164, 2165, 2174, 2175)) {
                    assertTrue(objects.any { it.objectId == id && it.position.x in base..base + 63 }, "Missing boat object $id")
                }
            }
            for (id in listOf(2159, 2160, 2166, 2178, 2179)) {
                assertTrue(objects.any { it.objectId == id }, "Missing shore or wreck object $id")
            }
        }
    }
}

/**
 * Exercises native area-controller transitions and the shared teleport preflight before costs or callbacks.
 *
 * @author lare96
 */
class TrawlerControllerTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() = InventoryProductionFixtures.initialize()
    }

    @Test fun restrictionFollowsDryFloodedAndWreckMapsThenClearsOnShore() {
        val player = InventoryProductionFixtures.bot()
        val controllers = ControllerManager(player)
        `when`(player.controllers).thenReturn(controllers)
        val action = mock(TeleportAction::class.java)
        for (position in listOf(Position(1889, 4825), Position(1889, 4825, 1),
            Position(2017, 4825), Position(2017, 4825, 1), FishingTrawler.WRECK)) {
            `when`(player.position).thenReturn(position)
            controllers.checkPosition()
            assertTrue(controllers.primary is TrawlerController)
            assertFalse(controllers.checkTeleport(action))
        }
        for (position in listOf(FishingTrawler.ESCAPE, FishingTrawler.DOCK, FishingTrawler.LOBBY)) {
            `when`(player.position).thenReturn(position)
            controllers.checkPosition()
            assertEquals(PlayerController::class.java, controllers.primary.javaClass)
            assertTrue(controllers.checkTeleport(action))
        }
    }

    @Test fun areaUsesSourcePlanesAndInclusiveBoundaries() {
        for (x in listOf(1856, 1919, 1984, 2047)) for (y in listOf(4800, 4863)) {
            assertTrue(TrawlerAreaListener.inside(Position(x, y)))
            assertTrue(TrawlerAreaListener.inside(Position(x, y, 1)))
            assertFalse(TrawlerAreaListener.inside(Position(x, y, 2)))
        }
        assertTrue(TrawlerAreaListener.inside(Position(1920, 4800)))
        assertTrue(TrawlerAreaListener.inside(Position(1983, 4863)))
        assertFalse(TrawlerAreaListener.inside(Position(1953, 4825, 1)))
        assertFalse(TrawlerAreaListener.inside(Position(1855, 4825)))
        assertFalse(TrawlerAreaListener.inside(Position(2048, 4825)))
        assertFalse(TrawlerAreaListener.inside(Position(1889, 4864)))
    }

    @Test fun sharedTeleportPreflightRejectsBeforeRequirementsExperienceAndItemCallbacks() {
        val player = InventoryProductionFixtures.bot()
        `when`(player.position).thenReturn(FishingTrawler.START)
        val controllers = ControllerManager(player)
        `when`(player.controllers).thenReturn(controllers)
        controllers.checkPosition()
        var started = false
        val action = object : TeleportAction(player, level = 99, xp = 100.0,
            destination = FishingTrawler.DOCK, style = TeleportSequence { false }) {
            override fun onTeleport() { started = true }
        }
        action.onLock()
        assertFalse(started)
        verify(player.magic, never()).addExperience(anyDouble())
        verify(player, never()).move(any(Position::class.java))
        verify(player).sendMessage("You're too far from shore to teleport!")
    }

    @Test fun leavingDoesNotUnregisterAnotherAreasController() {
        val player = InventoryProductionFixtures.bot()
        val controllers = ControllerManager(player)
        `when`(player.controllers).thenReturn(controllers)
        val other = mock(PlayerController::class.java)
        controllers.register(other)
        TrawlerAreaListener.exit(player)
        assertSame(other, controllers.primary)
    }
}

/**
 * Checks trawler dispatch prerequisites, persisted attempts and interruption cleanup.
 *
 * @author lare96
 */
class FishingTrawlerBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() = InventoryProductionFixtures.initialize()
    }

    private fun bot() = InventoryProductionFixtures.bot().also {
        `when`(it.asPlr()).thenReturn(it)
        it.wildernessLevel = 0
    }

    @Test fun selectionRequiresFishingSuppliesAndSafeState() {
        val bot = bot()
        `when`(bot.fishing.level).thenReturn(15)
        assertFalse(FishingTrawlerBotScript.eligible(bot))
        InventoryProductionFixtures.bank(bot, Item(1941), Item(954), Item(583))
        assertTrue(FishingTrawlerBotScript.eligible(bot))
        `when`(bot.fishing.level).thenReturn(14)
        assertFalse(FishingTrawlerBotScript.eligible(bot))
        `when`(bot.fishing.level).thenReturn(15)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(FishingTrawlerBotScript.eligible(bot))
    }

    @Test fun earnedCatchCanBeCollectedWithoutSupplies() {
        val bot = bot()
        `when`(bot.fishing.level).thenReturn(15)
        bot.unclaimedCatch.add(317)
        assertTrue(FishingTrawlerBotScript.eligible(bot))
    }

    @Test fun loadoutCombinesSplitSuppliesCapsRopesAndAcceptsAFullBucket() {
        val bot = bot()
        bot.inventory.add(Item(1941, 5))
        bot.inventory.add(Item(954, 3))
        InventoryProductionFixtures.bank(bot, Item(1941, 250), Item(954, 30), Item(585))
        val supplies = FishingTrawlerBotScript.supplies(bot)!!.associate { it.id to it.amount }
        assertEquals(mapOf(1941 to 200, 954 to 20, 585 to 1), supplies)
        assertEquals(5, bot.inventory.computeAmountForId(1941))
        assertEquals(250, bot.bank.computeAmountForId(1941))
    }

    @Test fun expiredAttemptExitsWithoutBoarding() = runBlocking {
        val bot = bot()
        assertTrue(FishingTrawlerBotScript(bot, false, 1).init(true))
        verify(bot, never()).move(any())
    }

    @Test fun restoredAttemptDoesNotJoinAnotherVoyage() = runBlocking {
        val bot = bot()
        val original = FishingTrawlerBotScript(bot, true, System.currentTimeMillis() + 60_000)
        val encoded = JsonObject().also { original.snapshot().save(it) }
        val restored = FishingTrawlerBotScript.VoyageData().also { it.load(encoded) }
        assertTrue(restored.boarded)
        assertEquals(encoded.get("deadline").asLong, restored.deadline)
        assertTrue(FishingTrawlerBotScript(bot, restored).init(true))
        verify(bot, never()).move(any())
    }

    @Test fun interruptionReturnsStrandedBotToShoreAndCancelsMovement() {
        val bot = bot()
        `when`(bot.position).thenReturn(FishingTrawler.START)
        bot.unclaimedCatch.add(317)
        FishingTrawlerBotScript(bot).paused()
        verify(bot).move(FishingTrawler.ESCAPE)
        verify(bot.navigator).cancel()
        verify(bot.walking).clear()
        assertEquals(listOf(317), bot.unclaimedCatch)
    }
}
