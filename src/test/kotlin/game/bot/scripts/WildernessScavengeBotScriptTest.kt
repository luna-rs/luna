package game.bot.scripts

import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.BotTradePurpose
import api.bot.script.InventoryProductionFixtures
import api.predef.SKILL_ATTACK
import api.predef.world
import com.google.common.collect.ImmutableSet
import com.google.gson.JsonObject
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import engine.bot.coordinator.ScavengingCoordinator
import engine.bot.coordinator.MerchantingCoordinator
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.Position
import io.luna.game.model.EntityState
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.economy.ItemPriceData
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotSettings
import io.luna.game.model.mob.bot.brain.BotBrain
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.bot.brain.BotPreference
import io.luna.game.model.mob.bot.brain.BotScavengingPreference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises durable introductory limits, PK detour cooldowns, live combat XP policy and saved area restrictions.
 * Fixtures do not start a server or claim live Wilderness travel, escape or pickup coverage.
 *
 * @author lare96
 */
class WildernessScavengeBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() = InventoryProductionFixtures.initialize()
    }

    private fun bot(introductory: Boolean = true): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.preferences.scavenging).thenReturn(BotScavengingPreference(if (introductory) 60_000 else 0))
        `when`(it.combatLevel).thenReturn(3)
        `when`(it.personality.confidence).thenReturn(0.0)
        `when`(it.personality.intelligence).thenReturn(1.0)
    }

    @Test fun onlineAllowanceSurvivesReloggingWithoutCountingOfflineTimeOrRenewing() {
        val preference = BotScavengingPreference(60_000)
        preference.advance(100_000)
        preference.advance(125_000)
        val restored = BotScavengingPreference.load(preference.save())
        assertEquals(35_000, restored.save().get("remainingMillis").asLong)
        // The first post-login checkpoint includes no time spent offline.
        restored.advance(125_000)
        restored.advance(159_999)
        assertTrue(restored.isIntroductory)
        restored.advance(160_000)
        assertFalse(restored.isIntroductory)
        assertFalse(BotScavengingPreference.load(restored.save()).isIntroductory)
    }

    @Test fun legacyAndOversizedAllowancesCannotCreateAnUnlimitedPhase() {
        assertFalse(BotScavengingPreference.load(JsonObject()).isIntroductory)
        val bounded = BotScavengingPreference(Long.MAX_VALUE)
        bounded.advance(0)
        bounded.advance(BotScavengingPreference.MAX_MILLIS)
        assertFalse(bounded.isIntroductory)
        assertFalse(BotScavengingPreference(-1).isIntroductory)
    }

    @Test fun configurationDefaultsToTwoPercentAndKeepsTheRequestedPopulationRange() {
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        assertEquals(0.02, gson.fromJson("{}", BotSettings::class.java).newAccountScavengingChance())
        for ((supplied, expected) in listOf(0.0 to 0.0, 0.001 to 0.01, 0.02 to 0.02, 0.5 to 0.03)) {
            val settings = gson.fromJson("{\"new_account_scavenging_chance\":$supplied}", BotSettings::class.java)
            assertEquals(expected, settings.newAccountScavengingChance())
        }
    }

    @Test fun detourCooldownSurvivesSaveAndIncludesFailedVisits() {
        val preference = BotScavengingPreference(0)
        assertTrue(preference.tryDetour(1_000, 60_000))
        val restored = BotScavengingPreference.load(preference.save())
        assertFalse(restored.tryDetour(60_999, 60_000))
        assertTrue(restored.tryDetour(61_000, 60_000))
    }

    @Test fun newAccountAssignmentCannotRerollAndLegacyProfilesStayOrdinary() {
        val bot = bot()
        val preferences = BotPreference(mutableMapOf(), mutableSetOf(), mutableMapOf(), mutableSetOf(), mutableMapOf())
        preferences.setBot(bot)
        preferences.initializeNewScavenger(1.0)
        assertTrue(preferences.scavenging.isIntroductory)
        preferences.scavenging.completeIntroduction()
        preferences.initializeNewScavenger(1.0)
        assertFalse(preferences.scavenging.isIntroductory)
        val legacy = preferences.save().also { it.remove("scavenging") }
        val restored = BotPreference(mutableMapOf(), mutableSetOf(), mutableMapOf(), mutableSetOf(), mutableMapOf())
        restored.setBot(bot)
        restored.load(legacy)
        restored.initializeNewScavenger(1.0)
        assertFalse(restored.scavenging.isIntroductory)
    }

    @Test fun introductoryAccountsChooseScavengingAndCannotGainCombatXp() {
        val bot = bot()
        assertSame(ScavengingCoordinator, BotBrain().process(bot))
        assertFalse(BotCombatActionHandler.wantsToTrain(bot, SKILL_ATTACK))
        assertFalse(BotCombatActionHandler.mayGainExperience(bot, SKILL_ATTACK))
        val skill = mock(Skill::class.java)
        `when`(skill.id).thenReturn(SKILL_ATTACK)
        assertEquals(0.0, BotCombatActionHandler.limitExperience(bot, skill, 500.0))
        bot.preferences.scavenging.completeIntroduction()
        assertTrue(BotCombatActionHandler.mayGainExperience(bot, SKILL_ATTACK))
    }

    @Test fun savedSkillPkersStayInLowEdgevilleEvenWithRecklessTraits() {
        val bot = bot(false)
        val pk = mock(BotPkPreference::class.java)
        `when`(pk.purpose()).thenReturn(BotPkPreference.Purpose.SKILL)
        `when`(bot.preferences.pk).thenReturn(pk)
        `when`(bot.personality.confidence).thenReturn(1.0)
        `when`(bot.personality.intelligence).thenReturn(0.0)
        val script = WildernessScavengeBotScript(bot, false, 20.minutes)
        assertTrue(script.allowsPosition(Position(3091, 3528)))
        assertFalse(script.allowsPosition(Position(3207, 3587)))
        assertFalse(script.allowsPosition(Position(3091, 3528, 1)))
        assertEquals(8, script.maximumDepth())
    }

    @Test fun personalityDepthChangesWithoutReassigningAnAccountBuild() {
        val bot = bot()
        val script = WildernessScavengeBotScript(bot, true, 20.minutes)
        assertEquals(8, script.maximumDepth())
        assertFalse(script.allowsPosition(Position(3140, 3705)))
        `when`(bot.personality.confidence).thenReturn(1.0)
        `when`(bot.personality.intelligence).thenReturn(0.0)
        assertEquals(48, script.maximumDepth())
        assertTrue(script.allowsPosition(Position(3140, 3705)))
        assertFalse(script.allowsPosition(Position(3100, 3950)))
        assertFalse(script.allowsPosition(Position(3100, 9950)))
        assertEquals(28, WildernessScavengeBotScript.maximumDepth(Double.NaN, Double.POSITIVE_INFINITY))
    }

    @Test fun savedVisitsKeepTheirDeadlineAndRevalidateInvalidWalkingGoals() {
        val bot = bot()
        val script = WildernessScavengeBotScript(bot, true, 20.minutes)
        val saved = JsonObject().also { script.snapshot().save(it) }
        val data = WildernessScavengeBotScript.ScavengeData().also { it.load(saved) }
        data.destination = Position(3100, 3950)
        val restored = WildernessScavengeBotScript(bot, data)
        assertEquals(saved.get("deadline").asLong, restored.snapshot().deadline)
        assertTrue(restored.introductory)
        assertFalse(restored.allowsPosition(data.destination!!))
    }

    @Test fun reactiveLootStopsOnDangerPhaseExpiryOrAnOutOfBoundsPile() {
        val bot = bot()
        val script = WildernessScavengeBotScript(bot, true, 20.minutes)
        `when`(bot.position).thenReturn(Position(3091, 3528))
        `when`(bot.healthPercent).thenReturn(100)
        val item = mock(GroundItem::class.java)
        `when`(item.position).thenReturn(Position(3092, 3528))
        `when`(item.state).thenReturn(EntityState.ACTIVE)
        assertFalse(script.allowsLoot(item))
        // Model a running collection phase without native banking or a live server.
        WildernessScavengeBotScript::class.java.getDeclaredField("collecting").apply {
            isAccessible = true
            setBoolean(script, true)
        }
        assertTrue(script.allowsLoot(item))
        `when`(item.position).thenReturn(Position(3140, 3705))
        assertFalse(script.allowsLoot(item))
        `when`(item.position).thenReturn(Position(3092, 3528))
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(script.allowsLoot(item))
        `when`(bot.combat.inCombat()).thenReturn(false)
        `when`(bot.healthPercent).thenReturn(40)
        assertFalse(script.allowsLoot(item))
        `when`(bot.healthPercent).thenReturn(100)
        bot.preferences.scavenging.completeIntroduction()
        assertFalse(script.allowsLoot(item))
    }

    @Test fun scavengersSuppressAttacksAndRestorePriorCombatStateOnCompletion() = runBlocking<Unit> {
        val bot = bot()
        val script = WildernessScavengeBotScript(bot, true, 20.minutes)
        assertFalse(script.init(false))
        verify(bot.combat).setDisabled(true)
        verify(bot.combat).setTarget(null)
        script.finish()
        verify(bot.combat).setDisabled(false)
        verify(bot.combat, never()).attack(any())
        verifyNoInteractions(bot.actionHandler)
    }

    private fun withSaleStock(merchant: Boolean, risingPrice: Boolean = true, target: Int = 1,
                              check: (Bot) -> Unit) {
        val bot = bot(false)
        `when`(bot.asPlr()).thenReturn(bot)
        bot.wildernessLevel = 0
        val wanted = WantedItemDefinition(436, 0, target, -1, -1,
            WantedItemDefinition.WantedItemPriority.STANDARD,
            ImmutableSet.of(WantedItemDefinition.WantedItemTag.MISCELLANEOUS))
        val preferences = BotPreference(mutableMapOf(BotActivity.MERCHANTING to if (merchant) 1.0 else 0.4),
            mutableSetOf(), mutableMapOf(436 to wanted), mutableSetOf(), mutableMapOf())
        preferences.setBot(bot)
        `when`(bot.preferences).thenReturn(preferences)
        InventoryProductionFixtures.bank(bot, Item(436, 10))
        val prices = world.economy.prices
        val previous = prices[436]
        `when`(prices[436]).thenReturn(ItemPriceData(436, 100.0, if (risingPrice) 90.0 else 100.0, 0, 0))
        try {
            check(bot)
        } finally {
            `when`(prices[436]).thenReturn(previous)
        }
    }

    @Test fun nonMerchantsSellNormallyAndMerchantsPreferEligibleProfitStock() {
        withSaleStock(false) { bot ->
            assertFalse(bot.preferences.isMerchant)
            assertEquals(BotTradePurpose.SELL, MerchantingCoordinator.sellingPurpose(bot))
            assertFalse(bot.preferences.isMerchant)
        }
        withSaleStock(true) { bot ->
            assertEquals(BotTradePurpose.MERCHANT_SELL, MerchantingCoordinator.sellingPurpose(bot))
        }
    }

    @Test fun merchantsCanSellOrdinarySurplusWhenNoProfitStockQualifies() {
        withSaleStock(true, risingPrice = false) { bot ->
            assertEquals(BotTradePurpose.SELL, MerchantingCoordinator.sellingPurpose(bot))
        }
        withSaleStock(false, target = 1000) { bot ->
            assertNull(MerchantingCoordinator.sellingPurpose(bot))
            assertNull(MerchantingCoordinator.getSellingScript(bot))
        }
    }

    @Test fun completedScavengingQueuesSellingAfterTheWholeIntroductionNotEachShortVisit() {
        val introductory = bot()
        runBlocking { WildernessScavengeBotScript(introductory, true, 20.minutes).completed() }
        verifyNoInteractions(introductory.scriptStack)
        withSaleStock(false) { bot ->
            runBlocking { WildernessScavengeBotScript(bot, true, 20.minutes).completed() }
            verify(bot.scriptStack).softPushHead(any(TradeBotScript::class.java))
            assertFalse(bot.preferences.isMerchant)
        }
    }
}
