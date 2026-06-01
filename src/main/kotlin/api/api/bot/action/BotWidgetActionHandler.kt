package api.bot.action

import api.bot.Suspendable.naturalDelay
import api.bot.SuspendableCondition
import api.predef.ext.*
import engine.widget.make.ButtonIndex
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.overlay.NumberInput

/**
 * Handles bot interactions with client widgets, overlays, and interface controls.
 *
 * This handler covers UI-driven actions that a normal player would perform through the client, such as selecting
 * make-item dialogue options, choosing dialogue responses, closing interfaces, toggling run, and logging out.
 *
 * @param bot The bot performing widget actions.
 * @param handler The parent action handler.
 * @author lare96
 */
class BotWidgetActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    companion object {

        /**
         * Maps make-item dialogue slot indexes to their button id groups.
         *
         * The 317/377 make-item dialogue can show multiple craftable entries. Each entry has separate buttons for
         * fixed amounts and custom amounts. The map key is the zero-based slot index shown in the dialogue.
         */
        private val INDEX_MAP = mapOf<Int, ButtonIndex>(
            0 to ButtonIndex(make1 = 8949, index = 0),
            1 to ButtonIndex(make1 = 8953, index = 1),
            2 to ButtonIndex(make1 = 8957, index = 2),
            3 to ButtonIndex(make1 = 8961, index = 3),
            4 to ButtonIndex(make1 = 8965, index = 4)
        )
    }

    /**
     * Clicks a make-item dialogue entry for the requested slot and amount.
     *
     * Amounts of `1`, `5`, and `10` use their direct buttons. Any other amount uses the custom "Make X" button, waits
     * for the number-input overlay, enters the requested amount, and performs a short natural delay.
     *
     * This method returns silently if no make-item dialogue is open or if [index] does not map to a known dialogue slot.
     *
     * @param index The zero-based make-item option index.
     * @param amount The amount to make.
     */
    suspend fun clickMakeItem(index: Int, amount: Int) {
        bot.overlays[MakeItemDialogue::class] ?: return
        val button = INDEX_MAP[index] ?: return
        when (amount) {
            1 -> bot.output.clickButton(button.make1)
            5 -> bot.output.clickButton(button.make5)
            10 -> bot.output.clickButton(button.make10)
            else -> {
                val enterAmountCond = SuspendableCondition {
                    NumberInput::class in bot.overlays
                }
                bot.output.clickButton(button.makeX)
                if (enterAmountCond.submit().await()) {
                    bot.log("Entering amount (${amount}).")
                    bot.output.enterAmount(amount)
                    bot.naturalDelay()
                }
            }
        }
    }

    /**
     * Clicks one of the options on the currently open [OptionDialogue].
     *
     * The active dialogue interface id determines which button ids are valid. This supports the common two-option,
     * three-option, four-option, and five-option dialogue layouts.
     *
     * @param option The one-based option index to click.
     *
     * @return `true` if a supported dialogue interface was open and a click was attempted, or `false` if no supported
     * dialogue interface was active.
     */
    fun clickDialogueOption(option: Int): Boolean {
        val activeInterface = bot.overlays[OptionDialogue::class] ?: return false
        when (activeInterface.id) {
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

            else -> {
                bot.log("Unrecognized dialogue interface ${activeInterface.id}.")
                return false
            }
        }
        bot.log("Clicking dialogue option $option.")
        return true
    }

    /**
     * Clicks the logout button.
     *
     * This uses the normal client logout control instead of forcibly disconnecting the bot.
     */
    fun clickLogout() {
        bot.output.clickLogout()
        bot.log("Clicking logout button.")
    }

    /**
     * Closes the currently open interface window.
     *
     * If no window is open, this returns `true` immediately. Otherwise, it sends the close-interface action and waits
     * until the overlay manager reports that no window is open.
     *
     * @return `true` if no window was open or if the active window closed successfully.
     */
    suspend fun clickCloseInterface(): Boolean {
        if (bot.overlays.hasWindow()) {
            val suspendCond = SuspendableCondition { !bot.overlays.hasWindow() }
            bot.output.sendCloseInterface()
            bot.log("Clicking close interface button.")
            return suspendCond.submit().await()
        }
        return true
    }

    /**
     * Toggles the bot's running state.
     *
     * This updates the walking state directly instead of clicking the run widget. Future movement requests will use the
     * updated run state.
     *
     * @param enabled `true` to run, or `false` to walk.
     */
    fun clickRunning(enabled: Boolean) {
        bot.walking.isRunning = enabled
    }

    /**
     * Clicks the auto-retaliate button.
     */
    fun clickAutoRetaliate(enabled: Boolean) {
        if(enabled) {
            bot.output.clickButton(151)
        } else {
            bot.output.clickButton(150)
        }
    }
}