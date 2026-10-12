package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.common.collect.ImmutableSet
import game.skill.magic.Rune
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPreference
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies realistic rune demand, owned target counts and overflow-safe Alchemy stock requests.
 *
 * @author lare96
 */
class AlchemyDemandBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.magic.level).thenReturn(99)
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
    }

    private fun script(bot: Bot, type: AlchemyType) =
        InventoryProductionFixtures.active(AlchemyBotScript(bot, type, 8.minutes))

    @ParameterizedTest @EnumSource(AlchemyType::class)
    fun oneOwnedTargetRequestsBulkNatureStockWithoutFireDemand(type: AlchemyType) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1117))
        assertFalse(script(bot, type).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(Rune.NATURE.id, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(eq(Rune.FIRE.id), anyInt())
        verify(bot.preferences, never()).addWantedItem(anyInt(), anyInt())
    }

    @ParameterizedTest @EnumSource(AlchemyType::class)
    fun oneOwnedTargetWithNatureRunesRequestsBulkFireStock(type: AlchemyType) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1117))
        InventoryProductionFixtures.inventory(bot, Item(Rune.NATURE.id))
        assertFalse(script(bot, type).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(Rune.FIRE.id, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(eq(Rune.NATURE.id), anyInt())
    }

    @ParameterizedTest @EnumSource(AlchemyType::class)
    fun largerStockScalesFireDemandByTheSelectedSpellCost(type: AlchemyType) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1117, 2_000), Item(1115, 1_000))
        InventoryProductionFixtures.inventory(bot, Item(Rune.NATURE.id))
        assertFalse(script(bot, type).onInit(false))
        val perCast = if (type == AlchemyType.HIGH) 5 else 3
        verify(bot.preferences).raiseWantedItemTarget(Rune.FIRE.id, 3_000 * perCast)
    }

    @ParameterizedTest @EnumSource(AlchemyType::class)
    fun largeStockCountsAndRuneMultiplicationClampWithoutIntegerOverflow(type: AlchemyType) = runBlocking<Unit> {
        for (requestFire in listOf(false, true)) {
            val bot = bot()
            InventoryProductionFixtures.bank(bot, Item(1117, Int.MAX_VALUE), Item(1115, Int.MAX_VALUE))
            if (requestFire) InventoryProductionFixtures.inventory(bot, Item(Rune.NATURE.id))
            assertFalse(script(bot, type).onInit(false))
            val rune = if (requestFire) Rune.FIRE else Rune.NATURE
            verify(bot.preferences).raiseWantedItemTarget(rune.id, Int.MAX_VALUE)
        }
    }

    @ParameterizedTest @EnumSource(AlchemyType::class)
    fun carriedUnnotedArmourIsEligibleForInitialBankPreparation(type: AlchemyType) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(1117), Item(Rune.NATURE.id), Item(Rune.FIRE.id, 9))
        assertTrue(script(bot, type).onInit(false))
        verifyNoInteractions(bot.preferences)
    }

    @Test fun bulkRequestsPreserveHigherTargetsAndRaiseLowerTargetsWithTheirMetadata() = runBlocking<Unit> {
        fun wanted(id: Int, target: Int) = WantedItemDefinition(id, 25, target, SKILL_MAGIC, 99,
            WantedItemPriority.URGENT, ImmutableSet.of(WantedItemTag.SKILLING_CONSUMABLE))
        val higher = wanted(Rune.NATURE.id, 5_000)
        val lower = wanted(Rune.FIRE.id, 100)
        val preferences = BotPreference(mutableMapOf(), emptySet(), mutableMapOf(higher.id() to higher,
            lower.id() to lower), emptySet(), mutableMapOf())
        val bot = bot()
        `when`(bot.preferences).thenReturn(preferences)
        InventoryProductionFixtures.bank(bot, Item(1117))
        assertFalse(script(bot, AlchemyType.LOW).onInit(false))
        assertSame(higher, preferences.getWantedItem(higher.id()))
        InventoryProductionFixtures.inventory(bot, Item(Rune.NATURE.id))
        assertFalse(script(bot, AlchemyType.LOW).onInit(false))
        val raised = preferences.getWantedItem(lower.id())
        assertEquals(1_000, raised.target())
        assertEquals(lower.min(), raised.min())
        assertEquals(lower.skill(), raised.skill())
        assertEquals(lower.maxLevel(), raised.maxLevel())
        assertEquals(lower.priority(), raised.priority())
        assertEquals(lower.tags(), raised.tags())
    }
}
