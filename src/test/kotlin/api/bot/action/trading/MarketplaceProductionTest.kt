package api.bot.action.trading

import api.bot.script.InventoryProductionFixtures
import api.bot.script.StationaryInventoryBotScript
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import com.google.common.collect.ImmutableSet
import java.util.EnumSet
import game.bot.scripts.skills.StringBowBotScript
import game.bot.scripts.skills.StringBowBotScript.Companion.StringBowData
import game.bot.scripts.skills.CraftJewelleryBotScript
import game.bot.scripts.skills.MakeCannonballBotScript
import game.bot.scripts.skills.MakeMoltenGlassBotScript
import game.bot.scripts.trading.TradeBotScript
import game.skill.fletching.stringBow.Bow
import io.luna.game.model.Position
import io.luna.game.model.Entity
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies supplied marketplace geometry, collision refresh, skill selection and saved destinations.
 *
 * @author lare96
 */
class MarketplaceProductionTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }
    @AfterEach fun resetCollision() {
        val collision = world.collisionManager
        doReturn(false).`when`(collision).isBlocked(any(Position::class.java), eq(false))
    }

    @Test fun suppliedWidthHeightRectanglesHaveExactTileCountsAndKeepTheirGaps() {
        assertEquals(534, BotMarketplace.VARROCK.positions.size)
        assertEquals(133, BotMarketplace.EDGEVILLE.positions.size)
        assertEquals(66, BotMarketplace.SEERS_VILLAGE.positions.size)
        assertEquals(117, BotMarketplace.AL_KHARID.positions.size)
        assertFalse(BotMarketplace.EDGEVILLE.contains(Position(3091, 3490)))
        assertFalse(BotMarketplace.SEERS_VILLAGE.contains(Position(2722, 3488)))
        assertFalse(BotMarketplace.AL_KHARID.contains(Position(3278, 3164)))
        assertTrue(BotMarketplace.AL_KHARID.contains(Position(3277, 3173)))
    }

    @ParameterizedTest @EnumSource(BotMarketplace::class)
    fun everyMarketUsesItsParentAnchorAndUniqueGroundFloorTiles(market: BotMarketplace) {
        assertEquals(market.parent.anchor, market.arrival)
        assertEquals(market.arrival, market.activityZone.inside)
        assertSame(market, BotMarketplace.forZone(market.activityZone))
        assertEquals(market.positions.size, market.positions.toSet().size)
        assertTrue(market.positions.isNotEmpty())
        for (tile in market.positions) {
            assertTrue(market.contains(tile))
            assertTrue(tile in market.activityZone.area)
            assertFalse(market.contains(tile.setZ(1)))
        }
    }

    @ParameterizedTest @EnumSource(BotMarketplace::class)
    fun currentCollisionIsRecheckedAfterGeometryWasAlreadyUsed(market: BotMarketplace) {
        val tile = market.positions.first()
        val collision = world.collisionManager
        `when`(collision.isBlocked(tile, false)).thenReturn(true)
        assertFalse(tile in market.positions)
        `when`(collision.isBlocked(tile, false)).thenReturn(false)
        assertTrue(tile in market.positions)
    }

    @Test fun marketplaceOverlayPreservesExistingBankAndResourceMemberships() {
        val bot = InventoryProductionFixtures.bot()
        val matches = EnumSet.noneOf(SubZone::class.java)
        val local = ImmutableSet.of(SubZone.SEERS_VILLAGE_MARKETPLACE, SubZone.SEERS_VILLAGE_MAIN)
        val tile = Position(2723, 3485)
        `when`(bot.position).thenReturn(tile)
        // Locatable.contains(Entity) reads Entity.position directly rather than the mocked getter.
        Entity::class.java.getDeclaredField("position").apply { isAccessible = true; set(bot, tile) }
        doReturn(matches).`when`(bot).subZones
        doReturn(local).`when`(bot).localSubZones
        SubZone.updateSubZone(bot)
        assertEquals(setOf(SubZone.SEERS_VILLAGE_MARKETPLACE, SubZone.SEERS_VILLAGE_MAIN), matches)
    }

    @Test fun fletchingCraftingAndPrayerUseRelevantMarketsAndRetainDraynor() {
        val fletching = StationaryInventoryBotScript.zonesForSkill(SKILL_FLETCHING)
        assertTrue(SubZone.SEERS_VILLAGE_MARKETPLACE in fletching)
        assertTrue(SubZone.EDGEVILLE_MARKETPLACE in fletching)
        assertFalse(SubZone.AL_KHARID_MARKETPLACE in fletching)
        val crafting = StationaryInventoryBotScript.zonesForSkill(SKILL_CRAFTING)
        assertTrue(BotMarketplace.entries.all { it.activityZone in crafting })
        val prayer = StationaryInventoryBotScript.zonesForSkill(SKILL_PRAYER)
        assertTrue(SubZone.EDGEVILLE_MARKETPLACE in prayer)
        assertFalse(SubZone.SEERS_VILLAGE_MARKETPLACE in prayer)
        for (zones in listOf(fletching, crafting, prayer)) {
            assertTrue(SubZone.DRAYNOR_MAIN in zones)
            assertTrue(SubZone.VARROCK_MARKETPLACE in zones)
            assertEquals(zones.size, zones.toSet().size)
        }
    }

    @Test fun realBowScriptUsesMarketDefaultsAndPersistsThemWithoutChangingLegacyRoutes() {
        val bot = InventoryProductionFixtures.bot()
        val script = StringBowBotScript(bot, Bow.SHORTBOW, 8.minutes)
        assertEquals(StationaryInventoryBotScript.zonesForSkill(SKILL_FLETCHING), script.zones)
        val json = JsonObject()
        script.snapshot().save(json)
        val loaded = StringBowData().also { it.load(json) }
        val restored = StringBowBotScript(bot, loaded)
        assertEquals(script.zones, restored.zones)
        assertEquals(Bow.SHORTBOW, restored.bow)
        val legacy = listOf(SubZone.HOME, SubZone.SEERS_VILLAGE_MAIN, SubZone.DRAYNOR_MAIN)
        loaded.zones = legacy.toMutableList()
        assertEquals(legacy, StringBowBotScript(bot, loaded).zones)
    }

    @Test fun furnaceRecipesRetainVerifiedFacilitiesInsteadOfPortableMarketDestinations() {
        val expected = listOf(SubZone.AL_KHARID_BANK, SubZone.FALADOR_WEST_BANK)
        assertEquals(expected, CraftJewelleryBotScript.DEFAULT_ZONES)
        assertEquals(expected, MakeMoltenGlassBotScript.DEFAULT_ZONES)
        assertEquals(expected, MakeCannonballBotScript.DEFAULT_ZONES)
        assertNull(BotMarketplace.forZone(SubZone.AL_KHARID_BANK))
        assertNull(BotMarketplace.forZone(SubZone.HOME))
        assertNull(BotMarketplace.forZone(SubZone.SEERS_VILLAGE_MAIN))
    }

    @ParameterizedTest @EnumSource(BotMarketplace::class)
    fun explicitTradeMarketDefaultsToItsOwnSessionDestination(market: BotMarketplace) {
        val bot = InventoryProductionFixtures.bot()
        assertEquals(listOf(market.activityZone),
            TradeBotScript(bot, BotTradePurpose.BUY, market, 8.minutes).zones)
    }

    @Test fun dextrousMerchantsChooseAnAvailableNearbyMarket() {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.personality.isDextrous).thenReturn(true)
        `when`(bot.position).thenReturn(BotMarketplace.SEERS_VILLAGE.arrival)
        assertSame(BotMarketplace.SEERS_VILLAGE, BotMarketplace.select(bot))
        val collision = world.collisionManager
        doReturn(true).`when`(collision).isBlocked(any(Position::class.java), eq(false))
        assertSame(BotMarketplace.VARROCK, BotMarketplace.select(bot))
    }

    @ParameterizedTest @EnumSource(BotMarketplace::class)
    fun marketsBlockedAfterStartupHaveNoRandomDestinationToThrowFrom(market: BotMarketplace) = runBlocking<Unit> {
        assertTrue(market.positions.isNotEmpty())
        val collision = world.collisionManager
        doReturn(true).`when`(collision).isBlocked(any(Position::class.java), eq(false))
        assertTrue(market.positions.isEmpty())
        assertNull(market.positions.randomOrNull())
        val bot = InventoryProductionFixtures.bot()
        assertFalse(TradeBotScript(bot, BotTradePurpose.BUY, market, 8.minutes).onInit(false))
    }
}
