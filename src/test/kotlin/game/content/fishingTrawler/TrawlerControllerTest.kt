package game.content.fishingTrawler

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import api.predef.ext.*
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportSequence
import io.luna.game.model.Position
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.game.model.mob.controller.PlayerController
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

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
