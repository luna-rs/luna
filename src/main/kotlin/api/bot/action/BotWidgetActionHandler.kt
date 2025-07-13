package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.predef.ext.get
import api.predef.ext.isOpen
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface
import io.luna.game.model.mob.dialogue.OptionDialogueInterface

/**
 * A [BotActionHandler] implementation for widget related actions.
 */
class BotWidgetActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * An action that forces a [Bot] to click one of the options on a [OptionDialogueInterface].
     *
     * @param option The option to click, between 1 and 5.
     */
    fun clickDialogueOption(option: Int): Boolean {
        val activeInterface = bot.interfaces.get(OptionDialogueInterface::class) ?: return false
        when (activeInterface.unsafeGetId()) {
            14443 ->
                when (option) {
                    1 -> bot.output.clickButton(14445)
                    2 -> bot.output.clickButton(14446)
                }

            2469 ->
                when (option) {
                    1 -> bot.output.clickButton(2471)
                    2 -> bot.output.clickButton(2472)
                    3 -> bot.output.clickButton(2473)
                }

            8207 ->
                when (option) {
                    1 -> bot.output.clickButton(8209)
                    2 -> bot.output.clickButton(8210)
                    3 -> bot.output.clickButton(8211)
                    4 -> bot.output.clickButton(8212)
                }

            8219 ->
                when (option) {
                    1 -> bot.output.clickButton(8221)
                    2 -> bot.output.clickButton(8222)
                    3 -> bot.output.clickButton(8223)
                    4 -> bot.output.clickButton(8224)
                    5 -> bot.output.clickButton(8225)
                }

            else -> return false
        }
        return true
    }

    /**
     * Clicks the widget to logout the [Bot] how a regular player would do it.
     */
    fun clickLogout() {
        bot.output.clickLogout()
    }

    /**
     * Clicks the widget to close interfaces.
     */
    fun clickCloseInterface(): SuspendableFuture {
        val suspendCond = SuspendableCondition({ !bot.interfaces.isStandardOpen && !bot.interfaces.isInputOpen })
        bot.output.sendCloseInterface()
        return suspendCond.submit()
    }

    /**
     * Clicks the widget to destroy an item if `value` is true, otherwise the destroy will be cancelled. The returned
     * future unsuspends when the destroy interface closes.
     */
    fun clickDestroyItem(value: Boolean = true): SuspendableFuture {
        val isOpen = { !bot.interfaces.isOpen(DestroyItemDialogueInterface::class) }
        val suspendCond = SuspendableCondition(isOpen)
        if (value) {
            bot.output.clickButton(14175)
        } else {
            bot.output.clickButton(14176)
        }
        return suspendCond.submit()
    }
}