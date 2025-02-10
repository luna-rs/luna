package api.bot.action

import api.predef.ext.*
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.OptionDialogueInterface

/**
 * A [BotActionHandler] implementation for widget related actions.
 */
class BotWidgetActionHandler(bot: Bot) : BotActionHandler(bot) {

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
                    1 -> output.clickButton(14445)
                    2 -> output.clickButton(14446)
                }

            2469 ->
                when (option) {
                    1 -> output.clickButton(2471)
                    2 -> output.clickButton(2472)
                    3 -> output.clickButton(2473)
                }

            8207 ->
                when (option) {
                    1 -> output.clickButton(8209)
                    2 -> output.clickButton(8210)
                    3 -> output.clickButton(8211)
                    4 -> output.clickButton(8212)
                }

            8219 ->
                when (option) {
                    1 -> output.clickButton(8221)
                    2 -> output.clickButton(8222)
                    3 -> output.clickButton(8223)
                    4 -> output.clickButton(8224)
                    5 -> output.clickButton(8225)
                }

            else -> return false
        }
        return true
    }

    /**
     * Clicks the widget to logout the [Bot] how a regular player would do it.
     */
    fun clickLogout() {
        output.clickButton(2458)
    }
}