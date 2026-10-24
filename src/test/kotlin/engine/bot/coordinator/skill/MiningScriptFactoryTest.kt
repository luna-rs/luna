package engine.bot.coordinator.skill

import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.MineBotScript
import game.skill.mining.Ore
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Verifies that every mining activity carries compatible rocks, access requirements, and valid bounds.
 *
 * @author lare96
 */
class MiningScriptFactoryTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() = ScriptFixtures.initialize()
    }

    private fun bot(level: Int): Bot = mock(Bot::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.mining.staticLevel).thenReturn(level)
        `when`(it.mining.level).thenReturn(level)
        `when`(it.position).thenReturn(Position(3013, 3360))
    }

    @Test
    fun allAvailableMiningMethodsCarryOnlyMineableRocksFoundAtTheirDestination() {
        for (level in listOf(1, 14, 15, 29, 30, 39, 40, 55, 59, 60, 70, 85, 99)) {
            for (intelligence in listOf(0.0, 1.0)) {
                val bot = bot(level)
                `when`(bot.personality.intelligence).thenReturn(intelligence)
                for (training in listOf(true, false)) {
                    for (activity in MiningScriptFactory.activities(bot, level, training)) {
                        assertTrue(activity.tokens.isNotEmpty())
                        assertTrue(activity.tokens.all { it.level <= level })
                        assertTrue(MiningScriptFactory.rocks.getValue(activity.zones.single()).containsAll(activity.tokens))
                        if (level < 60) assertFalse(SubZone.MINING_GUILD in activity.zones)
                    }
                }
            }
        }
    }

    @Test
    fun efficientTrainersAndIntelligentProfitMinersPreferAccessibleGuild() {
        for (dextrous in listOf(true, false)) {
            val bot = bot(85)
            `when`(bot.personality.isDextrous).thenReturn(dextrous)
            `when`(bot.personality.isIntelligent).thenReturn(!dextrous)
            for (training in listOf(true, false)) {
                if (!training && dextrous) continue
                val zones = mutableListOf<SubZone>()
                MiningScriptFactory.getScript(bot, 85, zones, training)
                assertEquals(listOf(SubZone.MINING_GUILD), zones)
            }
            `when`(bot.mining.level).thenReturn(59)
            assertTrue(MiningScriptFactory.activities(bot, 85, true)
                .none { SubZone.MINING_GUILD in it.zones })
        }
    }

    @Test
    fun bothFaladorMinesAreExposedForLowLevelOreProfitAndEssenceRemainsAvailable() {
        val bot = bot(30)
        `when`(bot.personality.intelligence).thenReturn(1.0)
        val options = MiningScriptFactory.activities(bot, 30, false)
        assertTrue(options.any { SubZone.FALADOR_WEST_MINE in it.zones })
        assertTrue(options.any { SubZone.DWARVEN_MINE in it.zones })
        `when`(bot.personality.intelligence).thenReturn(0.0)
        val zones = mutableListOf<SubZone>()
        val script = MiningScriptFactory.getProfitScript(bot, 30, zones) as MineBotScript
        assertEquals(setOf(Ore.RUNE_ESSENCE), script.ores)
        assertEquals(listOf(SubZone.ESSENCE_MINE), zones)
    }

    @Test
    fun newMineBoundsContainTheirAnchorsWithoutCoveringFaladorBank() {
        for (zone in listOf(SubZone.FALADOR_WEST_MINE, SubZone.DWARVEN_MINE, SubZone.MINING_GUILD)) {
            assertTrue(zone.area.contains(zone.inside), zone.name)
            assertFalse(zone.area.contains(Position(3013, 3360)), zone.name)
            assertTrue(zone.area.width() <= 110, zone.name)
            assertTrue(zone.area.length() <= 104, zone.name)
        }
    }
}
