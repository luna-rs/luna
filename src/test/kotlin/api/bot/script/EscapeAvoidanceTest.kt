package api.bot.script

import api.bot.zone.SubZone
import api.predef.*
import engine.bot.coordinator.skill.MiningScriptFactory
import engine.bot.gear.BotGearLocator
import game.bot.scripts.combat.CombatBotScript
import game.bot.scripts.combat.CombatBotScript.InitialState
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional
import kotlin.time.Duration.Companion.minutes

/**
 * Covers how a bot that teleports home to escape an attacker keeps away from that spot afterwards, so its paused
 * skilling script doesn't walk it straight back to the same attacker.
 *
 * @author lare96
 */
class EscapeAvoidanceTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()

        /**
         * Where the Varrock SW mine's Mugger goes for a miner.
         */
        private val MINE = SubZone.VARROCK_SW_MINE.inside
    }

    private lateinit var bot: Bot
    private lateinit var mugger: Npc

    @BeforeEach fun setup() {
        bot = InventoryProductionFixtures.bot()
        `when`(bot.asPlr()).thenReturn(bot)
        `when`(bot.position).thenReturn(MINE)
        `when`(bot.healthPercent).thenReturn(50)
        `when`(bot.personality.type).thenReturn(PersonalityTemplateType.SKILLER)
        mugger = mock(Npc::class.java, RETURNS_DEEP_STUBS)
        `when`(mugger.isAlive).thenReturn(true)
        `when`(mugger.combat.lastCombatWith).thenReturn(bot)
        `when`(mugger.isViewableFrom(bot)).thenReturn(true)
        `when`(bot.combat.lastCombatWith).thenReturn(mugger)
    }

    /**
     * A zoned activity that does nothing but select zones.
     *
     * @author lare96
     */
    private class Activity(bot: Bot, vararg zones: SubZone) : ZonedBotScript(bot, 8.minutes, zones.toMutableList()) {
        var setups = 0
        override suspend fun onInit(resumed: Boolean): Boolean { setups++; return true }
        override suspend fun equipment(): BotGearLocator? = null
        override suspend fun onBankRequested(initial: Boolean) = !initial
        override suspend fun executeInZone() = true
        override fun snapshot(): BotScriptData? = null
    }

    /**
     * Pauses [script] while the bot works in [zone], as the combat reflex does when the bot is attacked.
     */
    private fun pauseIn(script: Activity, zone: SubZone) {
        ZonedBotScript::class.java.getDeclaredField("activeZone").apply {
            isAccessible = true
            set(script, zone)
        }
        script.paused()
        `when`(bot.scriptStack.findActive(ZonedBotScript::class.java)).thenReturn(Optional.of(script))
    }

    /**
     * Puts the bot in a fight with [mugger] that it can't run from, then ends the fight after the bot teleports home.
     */
    private fun attack(energy: Double) {
        `when`(bot.combat.inCombat()).thenReturn(true)
        `when`(mugger.combat.target).thenReturn(bot)
        `when`(bot.runEnergy).thenReturn(energy)
    }

    private fun leaveFight() {
        `when`(bot.combat.inCombat()).thenReturn(false)
        `when`(mugger.combat.target).thenReturn(null)
    }

    @Test fun escapeAvoidsZonesNearWhereTheBotWasAttacked() {
        val now = System.currentTimeMillis()
        val escape = BotEscape(MINE, "Mugger", now, null)
        assertTrue(escape.avoids(SubZone.VARROCK_SW_MINE, now))
        assertFalse(escape.avoids(SubZone.VARROCK_SE_MINE, now))

        // The SW mine's area ends at x=3187: an attack 8 tiles east of it is close, 9 tiles isn't.
        val edge = SubZone.VARROCK_SW_MINE.area.northEast.x
        assertTrue(BotEscape(Position(edge + BotEscape.AVOID_RADIUS, 3370), "Mugger", now, null)
            .avoids(SubZone.VARROCK_SW_MINE, now))
        assertFalse(BotEscape(Position(edge + BotEscape.AVOID_RADIUS + 1, 3370), "Mugger", now, null)
            .avoids(SubZone.VARROCK_SW_MINE, now))

        // Attacked on the way to the mine, far from it.
        val onTheWay = BotEscape(Position(3182, 3440), "Mugger", now, SubZone.VARROCK_SW_MINE)
        assertTrue(onTheWay.avoids(SubZone.VARROCK_SW_MINE, now))
        assertFalse(onTheWay.avoids(SubZone.VARROCK_SE_MINE, now))

        val later = now + BotEscape.AVOID_FOR.inWholeMilliseconds
        assertFalse(escape.avoids(SubZone.VARROCK_SW_MINE, later))
        assertFalse(onTheWay.avoids(SubZone.VARROCK_SW_MINE, later))
    }

    @Test fun tooTiredSkillerRemembersItsEscapeRestsAndThenMinesElsewhere() = runBlocking<Unit> {
        val mining = Activity(bot, SubZone.VARROCK_SW_MINE, SubZone.VARROCK_SE_MINE)
        pauseIn(mining, SubZone.VARROCK_SW_MINE)
        assertEquals(SubZone.VARROCK_SW_MINE, mining.interruptedZone)

        val combat = CombatBotScript(bot, mugger, InitialState.RUN)
        attack(energy = 5.0)
        assertFalse(combat.init(false))
        assertFalse(combat.run())
        verify(bot.botClient.output).sendCommand("home")
        val escape = bot.lastEscape!!
        assertEquals(MINE, escape.position)
        assertEquals(SubZone.VARROCK_SW_MINE, escape.zone)

        // Home, out of the fight, but still too tired to run from anything: the bot waits.
        leaveFight()
        `when`(bot.runEnergy).thenReturn(39.0)
        assertFalse(combat.run())
        `when`(bot.runEnergy).thenReturn(40.0)
        assertTrue(combat.run())

        // The paused mining script resumes and doesn't go back to the Mugger.
        assertFalse(mining.init(true))
        assertEquals(listOf(SubZone.VARROCK_SE_MINE), mining.zones)
    }

    @Test fun botWithEnoughRunEnergyDoesNotRestAfterEscaping() = runBlocking<Unit> {
        val combat = CombatBotScript(bot, mugger, InitialState.RUN)
        attack(energy = 60.0)
        `when`(bot.healthPercent).thenReturn(10)
        assertFalse(combat.init(false))
        assertFalse(combat.run())
        assertNotNull(bot.lastEscape)
        leaveFight()
        assertTrue(combat.run())
    }

    @Test fun pkSessionsDoNotRecordEscapes() = runBlocking<Unit> {
        val combat = CombatBotScript(bot, mugger, InitialState.RUN, mock(PkBotScript::class.java))
        attack(energy = 5.0)
        `when`(bot.healthPercent).thenReturn(10)
        assertFalse(combat.init(false))
        assertFalse(combat.run())
        assertNull(bot.lastEscape)
        leaveFight()
        assertTrue(combat.run())
    }

    @Test fun scriptStopsWhenEveryCandidateZoneIsNearARecentEscape() = runBlocking<Unit> {
        bot.lastEscape = BotEscape(MINE, "Mugger", System.currentTimeMillis(), null)
        val mining = Activity(bot, SubZone.VARROCK_SW_MINE)
        assertTrue(mining.init(false))
        assertTrue(mining.zones.isEmpty())

        bot.lastEscape = BotEscape(MINE, "Mugger", System.currentTimeMillis() - BotEscape.AVOID_FOR.inWholeMilliseconds,
            null)
        val later = Activity(bot, SubZone.VARROCK_SW_MINE)
        assertFalse(later.init(false))
        assertEquals(listOf(SubZone.VARROCK_SW_MINE), later.zones)
    }

    @Test fun miningFactoryPicksAMineAwayFromARecentEscape() {
        val miner = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(miner.attributes()).thenReturn(AttributeMap())
        `when`(miner.mining.staticLevel).thenReturn(85)
        `when`(miner.mining.level).thenReturn(85)
        `when`(miner.position).thenReturn(Position(3013, 3360))
        `when`(miner.personality.isIntelligent).thenReturn(true)

        val zones = mutableListOf<SubZone>()
        MiningScriptFactory.getScript(miner, 85, zones, true)
        assertEquals(listOf(SubZone.MINING_GUILD), zones)

        val escape = BotEscape(SubZone.MINING_GUILD.inside, "Scorpion", System.currentTimeMillis(), null)
        miner.lastEscape = escape
        MiningScriptFactory.getScript(miner, 85, zones, true)
        assertTrue(zones.isNotEmpty())
        assertTrue(zones.none { escape.avoids(it) }, "$zones")
    }
}
