package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.magic.CombinationRune
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.item.Item
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies large and mixed Fire-rune stocks remain valid for native Alchemy casting.
 *
 * @author lare96
 */
class AlchemyRuneCountBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }
    private lateinit var luna: MockedStatic<Luna>
    @BeforeEach fun normalMode() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().betaMode()).thenReturn(false)
        luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }
    @AfterEach fun restoreMode() { luna.close() }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.magic.level).thenReturn(99)
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(it.rights).thenReturn(PlayerRights.PLAYER)
        val inventory = spy(it.inventory)
        for (id in Rune.entries.map { rune -> rune.id } + CombinationRune.entries.map { rune -> rune.id }) {
            doReturn(true).`when`(inventory).stackable(id)
        }
        `when`(it.inventory).thenReturn(inventory)
        InventoryProductionFixtures.inventory(it, Item(841), Item(Rune.NATURE.id))
    }
    private fun script(bot: Bot, type: AlchemyType) =
        InventoryProductionFixtures.active(AlchemyBotScript(bot, type, 8.minutes))

    @ParameterizedTest @EnumSource(AlchemyType::class)
    fun largeCombinedBankStocksRemainEligibleForInitialBanking(type: AlchemyType) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Rune.FIRE.id, Int.MAX_VALUE), Item(CombinationRune.LAVA.id, Int.MAX_VALUE))
        assertTrue(script(bot, type).onInit(false))
        verifyNoInteractions(bot.preferences)
    }

    @ParameterizedTest @EnumSource(AlchemyType::class)
    fun inventoryPlusBankStockDoesNotOverflowAcrossTheBoundary(type: AlchemyType) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(Rune.FIRE.id))
        InventoryProductionFixtures.bank(bot, Item(CombinationRune.LAVA.id, Int.MAX_VALUE))
        assertTrue(script(bot, type).onInit(false))
        verifyNoInteractions(bot.preferences)
    }

    @ParameterizedTest @EnumSource(AlchemyType::class)
    fun largeCarriedRuneStacksPassNativeValidationAndReachTheCast(type: AlchemyType) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(Rune.FIRE.id, Int.MAX_VALUE), Item(CombinationRune.LAVA.id, Int.MAX_VALUE))
        val script = script(bot, type)
        assertTrue(script.onInit(false))
        assertNotNull(Magic.checkRequirements(bot, type.level, type.requirements))
        `when`(bot.actionHandler.inventory.useSpellOnItem(type.spellId, 841, -1)).thenReturn(false)
        assertTrue(script.executeInZone())
        verify(bot.actionHandler.inventory).useSpellOnItem(type.spellId, 841, -1)
        assertFalse(script.isTerminated())
        verifyNoInteractions(bot.preferences)
    }

    @ParameterizedTest @EnumSource(AlchemyType::class)
    fun partialBaseAndCombinationStacksSatisfyTheNormalPlayerValidator(type: AlchemyType) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(Rune.FIRE.id), Item(CombinationRune.SMOKE.id, 2))
        if (type == AlchemyType.HIGH) InventoryProductionFixtures.inventory(bot, Item(CombinationRune.STEAM.id, 2))
        assertTrue(script(bot, type).onInit(false))
        val costs = Magic.checkRequirements(bot, type.level, type.requirements)
        assertNotNull(costs)
        assertEquals(if (type == AlchemyType.HIGH) 6 else 4, costs!!.sumOf { it.amount })
        verifyNoInteractions(bot.preferences)
    }

    @ParameterizedTest @EnumSource(value = CombinationRune::class, names = ["MIST", "DUST", "MUD"])
    fun nonFireCombinationStockCannotSatisfyFireRequirements(rune: CombinationRune) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(rune.id, Int.MAX_VALUE))
        assertFalse(script(bot, AlchemyType.LOW).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(Rune.FIRE.id, 1_000)
        assertNull(Magic.checkRequirements(bot, AlchemyType.LOW.level, AlchemyType.LOW.requirements))
    }
}
