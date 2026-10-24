package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Checks that activity grouping preserves the existing level and personality selection rules.
 *
 * @author lare96
 */
class BotSkillingActivityTest {
    /**
     * Exposes the shared selectors without constructing or running gameplay scripts.
     *
     * @author lare96
     */
    private object Factory : SkillingScriptFactory(0) {
        override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript = error("Unused")
        override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript = error("Unused")
        fun available(level: Int, activities: List<BotSkillingActivity<Int>>) = getSkillingActivities(level, { it }, activities)
        fun select(bot: Bot, level: Int, activities: List<BotSkillingActivity<Int>>) =
            getBestSkillingActivity(bot, level, { it }, activities)
        fun selectToken(bot: Bot, level: Int, tokens: List<Int>) = getBestActivity(bot, level, { it }, tokens)
    }

    private val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)

    @Test
    fun lockedTokensAreRemovedWithoutLosingTheMatchingRouteOrChangingTheDefinition() {
        val method = BotSkillingActivity(setOf(1, 15, 60), setOf(SubZone.FALADOR_WEST_MINE))
        val available = Factory.available(30, listOf(method)).single()
        assertEquals(setOf(1, 15), available.tokens)
        assertEquals(method.zones, available.zones)
        assertEquals(setOf(1, 15, 60), method.tokens)
    }

    @Test
    fun methodsWithNoUsableTokensAreExcluded() {
        val methods = listOf(BotSkillingActivity(setOf(15)), BotSkillingActivity(emptySet()))
        assertTrue(Factory.available(1, methods).isEmpty())
        assertNull(Factory.select(bot, 1, methods))
        assertNull(Factory.selectToken(bot, 1, listOf(15)))
    }

    @Test
    fun efficientPersonalitiesStillChooseTheHighestUnlockedMethod() {
        val methods = listOf(BotSkillingActivity(setOf(1)), BotSkillingActivity(setOf(15, 60)))
        `when`(bot.personality.isIntelligent).thenReturn(true)
        assertEquals(setOf(15), Factory.select(bot, 30, methods)!!.tokens)
        assertEquals(15, Factory.selectToken(bot, 30, listOf(1, 15, 60)))
        `when`(bot.personality.isIntelligent).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        assertEquals(setOf(15), Factory.select(bot, 30, methods)!!.tokens)
    }

    @Test
    fun equalRankedMethodsRetainTheirOriginalOrder() {
        `when`(bot.personality.isDextrous).thenReturn(true)
        val first = BotSkillingActivity(setOf(15), setOf(SubZone.FALADOR_WEST_MINE))
        val second = BotSkillingActivity(setOf(15), setOf(SubZone.DWARVEN_MINE))
        assertEquals(first, Factory.select(bot, 15, listOf(first, second)))
    }
}
