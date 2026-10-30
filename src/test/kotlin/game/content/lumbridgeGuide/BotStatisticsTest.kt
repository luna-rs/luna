package game.content.lumbridgeGuide

import api.bot.script.BotScript
import api.bot.script.InventoryProductionFixtures
import com.google.common.collect.Iterators
import game.bot.scripts.NpcCombatScript
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Verifies the Guide's census against mixed and changing online populations.
 *
 * @author lare96
 */
class BotStatisticsTest {

    companion object {

        @JvmStatic
        @BeforeAll
        fun initialize() = InventoryProductionFixtures.initialize()
    }

    private fun bot(vararg scripts: BotScript, coins: Int = 0, bankCoins: Int = 0): Bot {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.state).thenReturn(EntityState.ACTIVE)
        `when`(bot.personality.type).thenReturn(PersonalityTemplateType.SKILLER)
        `when`(bot.combatLevel).thenReturn(42)
        `when`(bot.inventory.computeAmountForId(995)).thenReturn(coins)
        `when`(bot.bank.computeAmountForId(995)).thenReturn(bankCoins)
        val skill = mock(Skill::class.java)
        `when`(skill.staticLevel).thenReturn(99)
        `when`(bot.skills.iterator()).thenAnswer { Iterators.unmodifiableIterator(listOf(skill).iterator()) }
        val stack = BotScriptStack(bot, mock(BotScriptManager::class.java))
        scripts.forEach(stack::pushTail)
        `when`(bot.scriptStack).thenReturn(stack)
        return bot
    }

    @Test
    fun repeatedScriptsCountOncePerBotAndQueuedTasksDifferFromCurrentTasks() {
        val npcCombat = mock(NpcCombatScript::class.java)
        val pking = mock(PkBotScript::class.java)
        val first = bot(npcCombat, pking, npcCombat)
        val second = bot(pking)
        val human = mock(Player::class.java)
        `when`(human.state).thenReturn(EntityState.ACTIVE)
        val inactive = bot(npcCombat)
        `when`(inactive.state).thenReturn(EntityState.INACTIVE)

        val census = BotStatistics.capture(listOf(first, second, human, inactive),
            setOf(NpcCombatScript::class.java.name, "game.bot.scripts.skills.FishBotScript"))

        assertEquals(2, census.onlineBots)
        assertEquals(1, census.humansOnline)
        assertTrue("NPC combat: 1 (50.0%)" in census.scriptLines())
        assertTrue("Player killing: 2 (100.0%)" in census.scriptLines())
        assertTrue("Player killing: 1 (50.0%)" in census.scriptLines(currentOnly = true))
        assertTrue("Fishing: 0 (0.0%)" in census.scriptLines())
    }

    @Test
    fun emptyWorldReportsZeroWithoutInvalidPercentages() {
        val census = BotStatistics.capture(emptyList(), setOf("game.bot.scripts.NpcCombatScript"))

        assertEquals(0, census.onlineBots)
        assertTrue(census.overviewLines().any { it.contains("No bots are online") })
        assertTrue("NPC combat: 0 (0.0%)" in census.scriptLines())
        assertFalse((census.overviewLines() + census.scriptLines()).any {
            it.contains("NaN") || it.contains("Infinity")
        })
    }

    @Test
    fun capturedReportStaysConsistentAfterLiveQueuesAndLevelsChange() {
        val bot = bot(mock(NpcCombatScript::class.java))
        val census = BotStatistics.capture(listOf(bot), emptySet())
        val original = census.overviewLines()

        bot.scriptStack.pushHead(mock(PkBotScript::class.java))
        `when`(bot.combatLevel).thenReturn(126)

        assertEquals(original, census.overviewLines())
        assertTrue("NPC combat: 1 (100.0%)" in census.scriptLines(currentOnly = true))
        assertTrue("Player killing: 0 (0.0%)" in census.scriptLines())
        assertTrue("Average combat level: 42.0" in census.overviewLines())
    }

    @Test
    fun coinTotalsUseLongsAndInterestsCanOverlap() {
        val first = bot(coins = Int.MAX_VALUE, bankCoins = Int.MAX_VALUE)
        val second = bot(coins = Int.MAX_VALUE, bankCoins = Int.MAX_VALUE)
        `when`(first.preferences.likesActivity(BotActivity.TRAINING_SKILLS)).thenReturn(true)
        `when`(first.preferences.likesActivity(BotActivity.PKING)).thenReturn(true)
        val census = BotStatistics.capture(listOf(first, second), emptySet())

        assertTrue("Coins in inventories and banks: 8,589,934,588" in census.overviewLines())
        assertTrue("Training skills: 1 (50.0%)" in census.overviewLines())
        assertTrue("Player killing: 1 (50.0%)" in census.overviewLines())
        assertTrue("Level 99 skills held: 2" in census.overviewLines())
    }

    @Test
    fun everyRegisteredScriptAppearsOnExactlyOneBoundedPage() {
        val types = (1..137).map { "example.NewTask${it}BotScript" }.toSet()
        val census = BotStatistics.capture(emptyList(), types)
        val pages = (0 until census.scriptPageCount).map { census.scriptLines(page = it) }

        assertEquals(4, census.scriptPageCount)
        assertTrue(pages.all { it.size <= 100 })
        val taskLines = pages.flatten().filter { it.startsWith("New Task") }
        assertEquals(types.size, taskLines.size)
        assertEquals(taskLines.size, taskLines.distinct().size)
        assertThrows(IllegalArgumentException::class.java) { census.scriptLines(page = -1) }
        assertThrows(IllegalArgumentException::class.java) { census.scriptLines(page = census.scriptPageCount) }
    }

    @Test
    fun stackInspectionDoesNotExposeOrDrainTheMutableQueue() {
        val script = mock(NpcCombatScript::class.java)
        val bot = bot(script)
        val copied = bot.scriptStack.scripts

        bot.scriptStack.pushTail(mock(PkBotScript::class.java))

        assertEquals(listOf(script), copied)
        assertEquals(2, bot.scriptStack.size())
        assertSame(script, bot.scriptStack.current())
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (copied as MutableList<BotScript>).clear()
        }
    }
}
