package engine.bot.coordinator.skill

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import game.bot.scripts.skills.FishBotScript
import game.skill.fishing.catchFish.Tool.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

/**
 * Verifies fishing factories only select tools with supported routes.
 *
 * @author lare96
 */
class FishingScriptFactoryTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() {
            InventoryProductionFixtures.initialize()
        }
    }

    @Test
    fun focusedBotsProgressThroughMonkfishKarambwanAndSharks() {
        for (dextrous in listOf(false, true)) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.personality.isDextrous).thenReturn(dextrous)
            `when`(bot.personality.isIntelligent).thenReturn(!dextrous)
            for (level in 62..99) {
                val tool = when { level < 65 -> MONKFISH_NET; level < 76 -> KARAMBWAN_VESSEL; else -> SHARK_HARPOON }
                val zone = when { level < 65 -> SubZone.PISCATORIS_FISHING_COLONY_MAIN; level < 76 -> SubZone.KARAMJA_KARAMBWAN_FISHING; else -> SubZone.EAST_CATHERBY_FISHING }
                for (training in listOf(false, true)) {
                    val script = FishingScriptFactory.getScript(bot, level, mutableListOf(), training) as FishBotScript
                    assertEquals(tool, script.selectedTool, "level $level, training $training")
                    assertEquals(listOf(zone), script.zones)
                    assertTrue(script.requirements())
                }
            }
        }
    }

    @Test
    fun declaredRoutesExcludeUnsupportedToolsAndPreserveFreshwaterBait() {
        val groups = FishingScriptFactory.FishingZone.entries
        assertEquals(setOf(SMALL_NET, FISHING_ROD, FLY_FISHING_ROD, PIKE_FISHING_ROD,
                          BIG_NET, HARPOON, LOBSTER_POT, MONKFISH_NET, KARAMBWAN_VESSEL, SHARK_HARPOON),
                     groups.flatMap { it.tools }.toSet())
        assertEquals(setOf(MONKFISH_NET, HARPOON), FishingScriptFactory.FishingZone.SMALL_NET_AND_HARPOON.tools)
        assertEquals(setOf(BIG_NET, SHARK_HARPOON), FishingScriptFactory.FishingZone.BIG_NET_AND_HARPOON.tools)
        assertEquals(setOf(FLY_FISHING_ROD, PIKE_FISHING_ROD), FishingScriptFactory.FishingZone.LURE_AND_BAIT.tools)
        assertTrue(3848 in FishBotScript.FIRST_CLICK_SPOTS[HARPOON])
        assertTrue(3848 in FishBotScript.SECOND_CLICK_SPOTS[MONKFISH_NET])
        assertFalse(1174 in FishBotScript.FIRST_CLICK_SPOTS[MONKFISH_NET])
        assertTrue(1174 in FishBotScript.FIRST_CLICK_SPOTS[KARAMBWANJI_NET])
    }

    @Test
    fun allPersonalityBranchesSelectCompatibleRoutes() {
        for (dumb in listOf(false, true)) for (dextrous in listOf(false, true))
            for (intelligent in listOf(false, true)) for (antiSocial in listOf(false, true)) {
                val bot = InventoryProductionFixtures.bot()
                `when`(bot.personality.isDumb).thenReturn(dumb)
                `when`(bot.personality.isDextrous).thenReturn(dextrous)
                `when`(bot.personality.isIntelligent).thenReturn(intelligent)
                `when`(bot.personality.isAntiSocial).thenReturn(antiSocial)
                for (level in 1..99) {
                    val script = FishingScriptFactory.getTrainingScript(bot, level, mutableListOf()) as FishBotScript
                    assertTrue(script.selectedTool.level <= level)
                    if (script.selectedTool == KARAMBWAN_VESSEL) assertEquals(listOf(SubZone.KARAMJA_KARAMBWAN_FISHING), script.zones)
                    assertTrue(script.requirements())
                    assertTrue(FishingScriptFactory.FishingZone.entries.any {
                        script.selectedTool in it.tools && script.zones == it.zones(bot)
                    }, "${script.selectedTool} at level $level")
                }
            }
    }
}
