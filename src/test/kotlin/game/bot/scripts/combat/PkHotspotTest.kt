package game.bot.scripts.combat

import api.bot.zone.SubZone
import engine.controllers.WildernessLocatableController.levelAt
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.bot.brain.BotPkPreference.Purpose
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Checks combat-level hotspot selection, patrol preference and the recreational Edgeville boundary.
 *
 * @author lare96
 */
class PkHotspotTest {

    private fun bot(level: Int, purpose: Purpose): Bot = mock(Bot::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.combatLevel).thenReturn(level)
        `when`(it.preferences.pk).thenReturn(BotPkPreference(purpose,
            BotPkPreference.Styles.MELEE, BotPkPreference.Build.MAIN))
    }

    @Test fun lowerLevelHuntersVisitAllWildernessHerbAndWineCamps() {
        for (purpose in listOf(Purpose.VALUE, Purpose.GENERIC)) {
            val anchors = PkBotScript.hotspotGroups(bot(69, purpose)).flatten()
            assertTrue(SubZone.EDGEVILLE_DUNGEON_CHAOS_DRUIDS.inside in anchors, purpose.name)
            assertTrue(SubZone.LVL_12_WILDERNESS_CHAOS_TEMPLE.inside in anchors, purpose.name)
            assertTrue(SubZone.LVL_40_WILDERNESS_CHAOS_TEMPLE.inside in anchors, purpose.name)
            assertFalse(Position(3016, 3849) in anchors, purpose.name)
        }
    }

    @Test fun seventyAndHigherHuntersVisitDragonsDemonsDeepWildernessAndAgility() {
        val camps = listOf(
            Position(3016, 3849) to 2,
            Position(2977, 3618) to 15,
            Position(3098, 3815) to 20,
            Position(3144, 3705) to 15,
            Position(3339, 3690) to 20,
            Position(3200, 3834) to 30,
            Position(3292, 3880) to 20,
            Position(3060, 3885) to 40,
            Position(2998, 3916) to 17)
        for (level in listOf(70, 71)) {
            for (purpose in listOf(Purpose.VALUE, Purpose.GENERIC, Purpose.CLAN)) {
                val anchors = PkBotScript.hotspotGroups(bot(level, purpose)).flatten()
                for ((camp, radius) in camps) {
                    if (purpose == Purpose.CLAN && levelAt(camp) < 30) continue
                    assertTrue(anchors.any { it.computeLongestDistance(camp) <= radius },
                        "$purpose level $level has no hotspot near $camp")
                }
                assertFalse(SubZone.EDGEVILLE_DUNGEON_CHAOS_DRUIDS.inside in anchors)
                assertFalse(SubZone.LVL_12_WILDERNESS_CHAOS_TEMPLE.inside in anchors)
            }
        }
    }

    @Test fun clanHotspotsRetainTheirMinimumWildernessHuntingDepth() {
        assertEquals(listOf(SubZone.LVL_40_WILDERNESS_CHAOS_TEMPLE.inside),
            PkBotScript.hotspotGroups(bot(69, Purpose.CLAN)).flatten())
        for (level in listOf(3, 69, 70, 71, 126)) {
            val bot = bot(level, Purpose.CLAN)
            assertTrue(PkBotScript.hotspotGroups(bot).flatten().all { levelAt(it) >= 30 })
            assertTrue(PkBotScript.patrolGroups(bot).flatten().all { levelAt(it) >= 30 })
        }
    }

    @Test fun loneClanRalliesStayStableAndUseEveryHotspotGroupAcrossTeamCapes() {
        for (level in listOf(69, 70)) {
            val rallies = mutableSetOf<Position>()
            for (cape in 4315..4413 step 2) {
                val bot = bot(level, Purpose.CLAN)
                `when`(bot.username).thenReturn("hotspot")
                `when`(bot.world.botManager.clanRegistry.capeFor("hotspot")).thenReturn(cape)
                `when`(bot.world.players.stream()).thenAnswer { java.util.stream.Stream.empty<Player>() }
                val script = PkBotScript(bot, 30.minutes, soloClanOuting = false)
                val rally = script.selectPatrolDestination()
                assertTrue(PkBotScript.hotspotGroups(bot).any { rally in it })
                repeat(3) { assertEquals(rally, script.selectPatrolDestination()) }
                rallies += rally
            }
            assertTrue(PkBotScript.hotspotGroups(bot(level, Purpose.CLAN)).all { group ->
                rallies.any { it in group }
            }, "level $level clan rallies omit a hotspot group")
        }
    }

    @Test fun hotspotsReceiveTwoThirdsOfPatrolChoicesForEveryHuntingPurpose() {
        for (level in listOf(69, 70, 71)) {
            for (purpose in listOf(Purpose.VALUE, Purpose.GENERIC, Purpose.CLAN)) {
                val bot = bot(level, purpose)
                val hotspots = PkBotScript.hotspotGroups(bot)
                val patrols = PkBotScript.patrolGroups(bot)
                assertTrue(hotspots.isNotEmpty())
                assertTrue(hotspots.all { it in patrols }, "$purpose level $level omits a hotspot")
                assertEquals(patrols.size * 2, patrols.count { it in hotspots } * 3,
                    "$purpose level $level does not prefer hotspots in two thirds of patrol choices")
            }
        }
    }

    @Test fun recreationalPkersKeepTheirLowEdgevilleBoundaryAcrossTheLevelSplit() {
        for (level in listOf(3, 69, 70, 126)) {
            val bot = bot(level, Purpose.SKILL)
            assertTrue(PkBotScript.hotspotGroups(bot).isEmpty())
            val groups = PkBotScript.patrolGroups(bot)
            assertTrue(groups.isNotEmpty())
            assertTrue(groups.all { it.isNotEmpty() })
            assertTrue(groups.flatten().all { it.z == 0 && it.regionId == 12343 && levelAt(it) in 1..8 })
        }
    }

    @Test fun everyPatrolDestinationIsOnTheWildernessGroundPlane() {
        for (purpose in Purpose.values()) {
            for (level in listOf(3, 69, 70, 126)) {
                val bot = bot(level, purpose)
                for (groups in listOf(PkBotScript.hotspotGroups(bot), PkBotScript.patrolGroups(bot))) {
                    assertTrue(groups.all { it.isNotEmpty() }, "$purpose level $level has an empty group")
                    assertTrue(groups.flatten().all { it.z == 0 && levelAt(it) > 0 },
                        "$purpose level $level has a destination outside the Wilderness")
                }
            }
        }
    }
}
