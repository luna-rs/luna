package game.bot.scripts.skills

import api.bot.action.BotBankingActionHandler
import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.magic.Rune
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.def.ArrayDefinitionRepository
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import java.util.OptionalInt
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies Alchemy target withdrawals restore the actual banking mode on every exit path.
 *
 * @author lare96
 */
class AlchemyBankModeBotScriptTest {
    companion object {
        private const val BASE = 1117
        private const val NOTE = 1118
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initialize()
            val id = PersistentVarp.WITHDRAW_AS_NOTE.clientId
            if (VarpDefinition.ALL.get(id).isEmpty) {
                val definition = mock(VarpDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                VarpDefinition.ALL.storeDefinition(definition)
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = ["success", "failure", "exception", "cancellation"])
    fun notedTargetWithdrawalAlwaysRestoresTheActualBankVarp(outcome: String) = runBlocking<Unit> {
        val base = itemDef(BASE)
        val definitions = ArrayDefinitionRepository::class.java.getDeclaredField("definitions").apply { isAccessible = true }.get(ItemDefinition.ALL)
        val oldNote = java.lang.reflect.Array.get(definitions, NOTE)
        val field = ItemDefinition::class.java.getDeclaredField("notedId").apply { isAccessible = true }
        val oldLink = field.get(base)
        val note = ItemDefinition(NOTE, "Bronze platebody", "", 0, 0, 0, 0, 0, 0, 0,
            false, 0, true, emptyArray(), emptyArray(), OptionalInt.of(BASE),
            0, 0, 0, OptionalInt.empty(), 0.0, true)
        java.lang.reflect.Array.set(definitions, NOTE, note.toNote(base))
        try {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.magic.level).thenReturn(99)
            `when`(bot.spellbook).thenReturn(Spellbook.REGULAR)
            `when`(bot.personality.intelligence).thenReturn(0.0)
            val bank = spy(bot.bank)
            doReturn(true).`when`(bank).isOpen
            `when`(bot.bank).thenReturn(bank)
            val varps = PersistentVarpManager(bot)
            `when`(bot.varpManager).thenReturn(varps)
            InventoryProductionFixtures.inventory(bot, Item(Rune.NATURE.id), Item(Rune.FIRE.id, 9))
            InventoryProductionFixtures.bank(bot, Item(BASE, 3))
            val parent = bot.actionHandler
            val banking = spy(BotBankingActionHandler(bot, parent))
            `when`(parent.banking).thenReturn(banking)
            val script = InventoryProductionFixtures.active(AlchemyBotScript(bot, AlchemyType.LOW, 8.minutes))
            doAnswer {
                assertEquals(1, varps.getValue(PersistentVarp.WITHDRAW_AS_NOTE))
                when (outcome) {
                    "success" -> bank.withdraw(bank.computeIndexForId(BASE), 3)
                    "failure" -> false
                    "exception" -> throw IllegalStateException("Fixture withdrawal failure")
                    else -> throw CancellationException("Fixture withdrawal cancellation")
                }
            }.`when`(banking).withdrawAll(BASE)
            val failure = runCatching { script.onBankOpen(false) }.exceptionOrNull()
            assertEquals(0, varps.getValue(PersistentVarp.WITHDRAW_AS_NOTE))
            val order = inOrder(banking)
            order.verify(banking).clickBankingMode(false)
            order.verify(banking).clickBankingMode(true)
            order.verify(banking).withdrawAll(BASE)
            order.verify(banking).clickBankingMode(false)
            when (outcome) {
                "success" -> {
                    assertNull(failure)
                    assertFalse(script.isTerminated())
                    assertEquals(3, bot.inventory.computeAmountForId(NOTE))
                    assertFalse(bot.inventory.contains(BASE))
                }
                "failure" -> { assertNull(failure); assertTrue(script.isTerminated()) }
                "exception" -> assertTrue(failure is IllegalStateException)
                "cancellation" -> assertTrue(failure is CancellationException)
            }
        } finally {
            field.set(base, oldLink)
            java.lang.reflect.Array.set(definitions, NOTE, oldNote)
        }
    }
}
