package engine.bot.coordinator.skill

import api.bot.zone.SubZone
import com.google.gson.JsonParser
import game.bot.scripts.skills.PickpocketBotScript
import game.skill.thieving.pickpocketNpc.ThievingNpcType
import io.luna.LunaContext
import io.luna.game.model.Position
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.bot.Bot
import io.luna.game.plugin.PluginBootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.OptionalInt

class ThievingScriptFactoryTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initializeScriptFixtures() {
            // Factory construction initializes drop tables through the Kotlin script bindings.
            // Use a mock context without starting a server, and fixture names for drop item lookup.
            PluginBootstrap::class.java.getDeclaredMethod("setBindings", LunaContext::class.java).apply {
                isAccessible = true
                invoke(null, mock(LunaContext::class.java, RETURNS_DEEP_STUBS))
            }
            Files.newBufferedReader(Path.of("data/dumps/items.json")).use { reader ->
                for (entry in JsonParser.parseReader(reader).asJsonArray) {
                    val item = entry.asJsonObject
                    if (item.getAsJsonObject("unnoted_id").get("is_present").asBoolean) continue
                    ItemDefinition.ALL.storeDefinition(ItemDefinition(
                        item.get("id").asInt, item.get("name").asString, "", 0, 0, 0, 0, 0, 0, 0,
                        false, 0, false, emptyArray(), emptyArray(), OptionalInt.empty(),
                        0, 0, 0, OptionalInt.empty(), 0.0, false
                    ))
                }
            }
        }
    }

    @Test
    fun focusedTrainingUsesWarriorSpawnsOnlyBetweenLevels25And39() {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val routes = listOf(
            Triple(24, ThievingNpcType.FARMER, SubZone.NORTH_LUMBRIDGE_FARMING),
            Triple(25, ThievingNpcType.WARRIOR, SubZone.ARDOUGNE_WARRIOR_THIEVING),
            Triple(39, ThievingNpcType.WARRIOR, SubZone.ARDOUGNE_WARRIOR_THIEVING),
            Triple(40, ThievingNpcType.GUARD, SubZone.ARDOUGNE_SQUARE_THIEVING),
            Triple(55, ThievingNpcType.KNIGHT_OF_ARDOUGNE, SubZone.ARDOUGNE_SQUARE_THIEVING)
        )
        for ((level, npc, zone) in routes) {
            val script = ThievingScriptFactory.getTrainingScript(bot, level, mutableListOf()) as PickpocketBotScript
            assertEquals(setOf(npc), script.npcs, "level $level")
            assertEquals(listOf(zone), script.zones, "level $level")
        }
    }

    @Test
    fun lessFocusedWarriorTrainingRetainsLowerLevelAlternatives() {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.personality.isDextrous).thenReturn(false)
        `when`(bot.personality.isDumb).thenReturn(true)
        `when`(bot.personality.dexterity).thenReturn(0.0)
        `when`(bot.personality.intelligence).thenReturn(1.0)
        val script = ThievingScriptFactory.getTrainingScript(bot, 25, mutableListOf()) as PickpocketBotScript
        assertEquals(setOf(ThievingNpcType.MAN_AND_WOMAN, ThievingNpcType.FARMER, ThievingNpcType.WARRIOR), script.npcs)
        assertEquals(listOf(SubZone.NORTH_LUMBRIDGE_FARMING, SubZone.LUMBRIDGE_COURT_YARD,
            SubZone.ARDOUGNE_WARRIOR_THIEVING), script.zones)
    }

    @Test
    fun warriorZoneSearchCoversBothSpawnWanderEnvelopesWithoutOverlappingOtherZones() {
        val zone = SubZone.ARDOUGNE_WARRIOR_THIEVING
        val spawns = JsonParser.parseString(Files.readString(Path.of("data/game/world/npc_spawns.jsonc")))
            .asJsonArray.map { it.asJsonObject }.filter {
                val pos = it.getAsJsonObject("position")
                it.get("id").asInt == 15 && pos.get("x").asInt in 2620..2640 && pos.get("y").asInt in 3280..3315
            }
        assertEquals(2, spawns.size)
        assertTrue(zone.area.contains(zone.inside))
        for (spawn in spawns) {
            val pos = spawn.getAsJsonObject("position")
            val radius = spawn.getAsJsonObject("wander").get("radius").asInt
            val x = pos.get("x").asInt
            val y = pos.get("y").asInt
            for (dx in -radius..radius) for (dy in -radius..radius) {
                val tile = Position(x + dx, y + dy, pos.get("z").asInt)
                assertTrue(zone.area.contains(tile), "wander tile $tile")
                assertTrue(zone.area.centerPosition.computeLongestDistance(tile) <= zone.area.tileRadius,
                    "search must reach $tile")
            }
        }
        for (other in SubZone.entries.filter { it != zone }) {
            assertFalse(zone.area.computePositions().any { other.area.contains(it) }, "overlap with $other")
        }
    }
}
