package api.bot.action

import api.bot.script.InventoryProductionFixtures
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*

/**
 * Verifies the bank handler enforces common safety even when invoked outside a zoned script.
 *
 * @author lare96
 */
class BankActionSafetyTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun interrupted(mode: String): Bot = InventoryProductionFixtures.bot().also { bot ->
        `when`(bot.state).thenReturn(EntityState.INACTIVE)
        when (mode) {
            "dead" -> `when`(bot.health).thenReturn(0)
            "locked" -> `when`(bot.isLocked).thenReturn(true)
            "combat" -> `when`(bot.combat.inCombat()).thenReturn(true)
            "strong" -> bot.actions.submit(object : Action<Bot>(bot, ActionType.STRONG) {
                override fun run() = false
            })
        }
    }

    @ParameterizedTest @ValueSource(strings = ["dead", "locked", "combat", "strong"])
    fun depositsDoNotSendInputsAfterRecoveryAborts(mode: String) = runBlocking<Unit> {
        val bot = interrupted(mode)
        val output = bot.output
        val banking = BotBankingActionHandler(bot, bot.actionHandler)
        assertFalse(banking.deposit(Item(995)))
        verifyNoInteractions(output)
    }

    @ParameterizedTest @ValueSource(strings = ["dead", "locked", "combat", "strong"])
    fun withdrawalsDoNotSendInputsAfterRecoveryAborts(mode: String) = runBlocking<Unit> {
        val bot = interrupted(mode)
        val output = bot.output
        val banking = BotBankingActionHandler(bot, bot.actionHandler)
        assertFalse(banking.withdraw(Item(995)))
        verifyNoInteractions(output)
    }

    @ParameterizedTest @ValueSource(strings = ["dead", "locked", "combat", "strong"])
    fun bankingModeCleanupRemainsAvailableWhileUnsafe(mode: String) {
        val bot = interrupted(mode)
        val varps = bot.varpManager
        BotBankingActionHandler(bot, bot.actionHandler).clickBankingMode(false)
        verify(varps).setValue(PersistentVarp.WITHDRAW_AS_NOTE, 0)
    }

    @Test fun travelCannotOpenABankIfTheBotBecomesLockedOnArrival() = runBlocking<Unit> {
        val bot = interrupted("locked")
        val handler = bot.actionHandler
        val interactions = handler.interactions
        val banking = spy(BotBankingActionHandler(bot, handler))
        doReturn(mock(GameObject::class.java)).`when`(banking).travelToNearestBank()
        assertFalse(banking.travelToBankOpen())
        verifyNoInteractions(interactions)
    }
}
