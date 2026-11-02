package engine.bot.gear

import api.bot.script.BotScript
import api.bot.script.InventoryProductionFixtures
import api.bot.action.trading.BotTradeIntentions
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import com.google.common.collect.ImmutableSet
import game.bot.scripts.trading.ObtainGearBotScript
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPreference
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Verifies review cadence, urgent wanted metadata, and a merged shopping follow-up.
 *
 * @author lare96
 */
class BotGearProgressionTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() {
            InventoryProductionFixtures.initializeCombat()
        }
    }

    private fun bot(intelligence: Double = 1.0, shopping: Double = 0.8): Bot {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.attributes()).thenReturn(AttributeMap())
        `when`(bot.asPlr()).thenReturn(bot)
        `when`(bot.personality.intelligence).thenReturn(intelligence)
        `when`(bot.personality.isIntelligent).thenReturn(intelligence >= 0.7)
        Skill.IDS.forEach { `when`(bot.skill(it).staticLevel).thenReturn(99) }
        val preferences = BotPreference(mutableMapOf(BotActivity.MERCHANTING to shopping),
            mutableSetOf(Skill.MINING), mutableMapOf(), mutableSetOf(), mutableMapOf())
        preferences.bot = bot
        `when`(bot.preferences).thenReturn(preferences)
        return bot
    }

    @Test fun reviewsEveryTwoLevelsIncludingSkippedThresholds() {
        val bot = bot()
        assertNull(BotGearProgression.reviewLevels(bot, Skill.ATTACK, 18, 19))
        assertEquals(19..20, BotGearProgression.reviewLevels(bot, Skill.ATTACK, 19, 20))
        assertNull(BotGearProgression.reviewLevels(bot, Skill.ATTACK, 20, 21))
        assertEquals(21..22, BotGearProgression.reviewLevels(bot, Skill.ATTACK, 21, 22))
        assertNull(BotGearProgression.reviewLevels(bot, Skill.ATTACK, 21, 22))
        assertEquals(23..30, BotGearProgression.reviewLevels(bot, Skill.ATTACK, 22, 30))
    }

    @Test fun skillsAndBotsHaveIndependentCadenceAndNinetyNineFlushes() {
        val bot = bot()
        assertEquals(97..98, BotGearProgression.reviewLevels(bot, Skill.ATTACK, 97, 98))
        assertEquals(99..99, BotGearProgression.reviewLevels(bot, Skill.ATTACK, 98, 99))
        assertNull(BotGearProgression.reviewLevels(bot, Skill.ATTACK, 98, 99))
        assertEquals(19..20, BotGearProgression.reviewLevels(bot, Skill.DEFENCE, 19, 20))
        assertEquals(19..20, BotGearProgression.reviewLevels(bot(), Skill.ATTACK, 19, 20))
        assertNull(BotGearProgression.reviewLevels(bot, Skill.MINING, 20, 20))
        assertNull(BotGearProgression.reviewLevels(bot, Skill.MINING, 20, 19))
    }

    @Test fun promotesExistingWantsWithoutLosingStockMetadata() {
        val bot = bot(intelligence = 0.7)
        bot.preferences.addWantedItem(WantedItemDefinition(1333, 2, 5, Skill.ATTACK, 99,
            WantedItemPriority.LOW, ImmutableSet.of(WantedItemTag.MISCELLANEOUS)))
        BotGearProgression.requestUpgrades(bot, Skill.ATTACK, setOf(1333))

        val wanted = bot.preferences.getWantedItem(1333)
        assertEquals(WantedItemPriority.URGENT, wanted.priority)
        assertEquals(2, wanted.min)
        assertEquals(5, wanted.target)
        assertEquals(Skill.ATTACK, wanted.skill)
        assertEquals(99, wanted.maxLevel)
        assertEquals(setOf(WantedItemTag.WEAPON, WantedItemTag.MISCELLANEOUS), wanted.tags)
        verify(bot.scriptStack, never()).softPushHead(any())
    }

    @Test fun onlyVeryIntelligentShoppersQueueErrands() {
        for ((intelligence, shopping) in listOf(0.84 to 0.8, 1.0 to 0.6)) {
            val bot = bot(intelligence, shopping)
            BotGearProgression.requestUpgrades(bot, Skill.ATTACK, setOf(1333))
            assertEquals(WantedItemPriority.URGENT, bot.preferences.getWantedItem(1333).priority)
            verify(bot.scriptStack, never()).softPushHead(any())
        }
    }

    @Test fun repeatedUnlocksShareOneBasketAfterCurrentActivity(): Unit = runBlocking {
        val bot = bot()
        val queued = mutableListOf<BotScript>()
        val stack = bot.scriptStack
        `when`(stack.scripts).thenAnswer { queued.toList() }
        doAnswer { queued += it.getArgument<BotScript>(0); null }
            .`when`(stack).softPushHead(any())
        BotGearProgression.requestUpgrades(bot, Skill.ATTACK, setOf(1329))
        BotGearProgression.requestUpgrades(bot, Skill.ATTACK, setOf(1333))

        assertEquals(1, queued.size)
        assertNull(bot.preferences.getWantedItem(1329))
        assertEquals(WantedItemPriority.URGENT, bot.preferences.getWantedItem(1333).priority)
        assertTrue((queued.single() as ObtainGearBotScript).run())
        verify(bot.actionHandler.shop).obtainWantedItems(setOf(1333))
        verify(bot.scriptStack, never()).pushHead(any())
    }

    @Test fun cancelledShoppingRetainsBasketForResumption(): Unit = runBlocking {
        val bot = bot()
        val script = ObtainGearBotScript(bot, setOf(1333))
        `when`(bot.actionHandler.shop.obtainWantedItems(setOf(1333)))
            .thenThrow(kotlin.coroutines.cancellation.CancellationException("reflex pause"))
            .thenReturn(setOf(1333))
        var cancelled = false
        try {
            script.run()
        } catch (_: kotlin.coroutines.cancellation.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(script.run())
        verify(bot.actionHandler.shop, times(2)).obtainWantedItems(setOf(1333))
    }

    @Test fun levelUpConnectsCadenceIntelligenceAndUrgentGearRequests() {
        val bot = bot(intelligence = 0.7)
        `when`(bot.skill(Skill.ATTACK).staticLevel).thenReturn(20)
        BotGearProgression.onLevelUp(bot, Skill.ATTACK, 18, 19)
        assertNull(bot.preferences.getWantedItem(1329))
        BotGearProgression.onLevelUp(bot, Skill.ATTACK, 19, 20)
        assertEquals(WantedItemPriority.URGENT, bot.preferences.getWantedItem(1329).priority)

        val unaware = bot(intelligence = 0.0)
        BotGearProgression.onLevelUp(unaware, Skill.ATTACK, 18, 20)
        assertNull(unaware.preferences.getWantedItem(1329))
    }

    @Test fun independentlyAcquiredBetterGearRetiresGeneratedWants() = runBlocking {
        val bot = bot(intelligence = 0.7)
        BotGearProgression.requestUpgrades(bot, Skill.ATTACK, setOf(1329))
        bot.itemTracker.add(1333)
        BotGearProgression.requestUpgrades(bot, Skill.ATTACK, emptySet())
        assertNull(bot.preferences.getWantedItem(1329))

        bot.itemTracker.remove(1333)
        BotGearProgression.requestUpgrades(bot, Skill.ATTACK, setOf(1329))
        bot.itemTracker.add(1333)
        assertTrue(ObtainGearBotScript(bot, setOf(1329)).run())
        assertNull(bot.preferences.getWantedItem(1329))
        verifyNoInteractions(bot.actionHandler.shop)
    }

    @Test fun partialErrandRefreshPreservesOtherUnavailableUpgrades() {
        val bot = bot(intelligence = 0.7)
        BotGearProgression.requestUpgrades(bot, Skill.ATTACK, setOf(1333))
        BotGearProgression.requestUpgrades(bot, Skill.DEFENCE, setOf(1127))

        assertEquals(setOf(1127), BotGearProgression.refreshUpgrades(bot, setOf(1127)))
        assertEquals(WantedItemPriority.URGENT, bot.preferences.getWantedItem(1333).priority)
        BotGearProgression.requestUpgrades(bot, Skill.ATTACK, emptySet())
        assertEquals(WantedItemPriority.URGENT, bot.preferences.getWantedItem(1333).priority)
    }

    @Test fun merchantingRetainsUrgencyForAnAdmittedGearUpgrade() {
        val bot = bot(intelligence = 0.7)
        Skill.IDS.forEach { `when`(bot.skill(it).staticLevel).thenReturn(1) }
        `when`(bot.skill(Skill.ATTACK).staticLevel).thenReturn(20)
        val id = BotEquipmentPreferences.progressionCandidates(bot).first {
            io.luna.game.model.def.EquipmentDefinition.ALL.retrieve(it).index == Equipment.WEAPON
        }
        BotGearProgression.requestUpgrades(bot, Skill.ATTACK, setOf(id))

        val purchase = BotTradeIntentions.purchases(bot).single { it.id == id }
        assertEquals(WantedItemPriority.URGENT, purchase.priority)
        assertEquals(1, purchase.target)
    }
}
