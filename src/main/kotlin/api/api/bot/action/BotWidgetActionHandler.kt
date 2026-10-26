package api.bot.action

import api.bot.Suspendable.naturalDelay
import api.bot.SuspendableCondition
import io.luna.game.model.mob.bot.Bot
import api.predef.ext.*
import engine.widget.make.ButtonIndex
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.def.WidgetDefinition

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

    /**
     * Checks that the expected standard interface is still open and owns this component.
     */
    private fun ownsComponent(interfaceId: Int, componentId: Int): Boolean {
        val active = bot.overlays[StandardInterface::class] ?: return false
        if (active.id != interfaceId) return false
        val pending = ArrayDeque<Int>().apply { add(interfaceId) }
        val visited = mutableSetOf<Int>()
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!visited.add(id)) continue
            if (id == componentId) return true
            WidgetDefinition.ALL[id].orElse(null)?.children?.let(pending::addAll)
        }
        return false
    }

    /**
     * Clicks a component only while its expected interface is open. The caller verifies the resulting action.
     */
    fun clickButton(interfaceId: Int, buttonId: Int): Boolean =
        ownsComponent(interfaceId, buttonId) && bot.output.clickButton(buttonId)

    /**
     * Selects an item's own action on an open grid. Native readers validate the displayed item and slot.
     */
    fun clickItemAction(interfaceId: Int, widgetId: Int, option: Int, index: Int, itemId: Int): Boolean =
        index in 0..32767 && option in 1..5 && ownsComponent(interfaceId, widgetId) &&
            WidgetDefinition.ALL[widgetId].orElse(null)?.type == WidgetDefinition.WidgetType.ITEM_GRID &&
            bot.output.sendItemAction(option, index, widgetId, itemId)

    /**
     * Selects a widget-defined container option, such as Value or Buy, through its native packet family.
     * Item-defined options use [clickItemAction]; the two menus dispatch different server events.
     */
    fun clickContainerAction(interfaceId: Int, widgetId: Int, option: Int, index: Int, itemId: Int): Boolean {
        if (index !in 0..32767 || option !in 1..5 || !ownsComponent(interfaceId, widgetId)) return false
        val widget = WidgetDefinition.ALL[widgetId].orElse(null) ?: return false
        val label = widget.options.getOrNull(option - 1)
        return widget.type == WidgetDefinition.WidgetType.ITEM_GRID && !label.isNullOrBlank() && label != "null" &&
            bot.output.sendItemWidgetClick(option, index, widgetId, itemId)
    }

    /**
     * Answers the current numeric prompt through the native input packet.
     */
    fun enterAmount(amount: Int): Boolean = NumberInput::class in bot.overlays && bot.output.enterAmount(amount)

    /**
     * Drags between two slots of an open item grid through the native arrange-item packet.
     * Slot capacity and move legality remain owned by the interface's content handler.
     */
    fun dragItem(interfaceId: Int, widgetId: Int, from: Int, to: Int): Boolean {
        if (from !in 0..32767 || to !in 0..32767 || from == to ||
            !ownsComponent(interfaceId, widgetId) ||
            WidgetDefinition.ALL[widgetId].orElse(null)?.type != WidgetDefinition.WidgetType.ITEM_GRID) return false
        return bot.output.sendArrangeItem(widgetId, from, to)
    }

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
        val dialogue = bot.overlays[MakeItemDialogue::class] ?: return
        if (index !in 0 until dialogue.length) return
        // The client uses a different button group for each dialogue length (see engine.widget.make.makeItem).
        val button = when (dialogue.length) {
            1 -> ButtonIndex(make1 = 8893, index = 0)
            2 -> ButtonIndex(make1 = 8874 + index * 4, index = index)
            3 -> ButtonIndex(make1 = 8889 + index * 4, index = index)
            4 -> ButtonIndex(make1 = 8909 + index * 4, index = index)
            5 -> INDEX_MAP[index]
            else -> null
        } ?: return
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
                    else -> return false
                }

            2469 ->
                when (option) {
                    1 -> bot.output.clickButton(2471)
                    2 -> bot.output.clickButton(2472)
                    3 -> bot.output.clickButton(2473)
                    else -> return false
                }

            8207 ->
                when (option) {
                    1 -> bot.output.clickButton(8209)
                    2 -> bot.output.clickButton(8210)
                    3 -> bot.output.clickButton(8211)
                    4 -> bot.output.clickButton(8212)
                    else -> return false
                }

            8219 ->
                when (option) {
                    1 -> bot.output.clickButton(8221)
                    2 -> bot.output.clickButton(8222)
                    3 -> bot.output.clickButton(8223)
                    4 -> bot.output.clickButton(8224)
                    5 -> bot.output.clickButton(8225)
                    else -> return false
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
     * Clicks the option with some text on the currently open [OptionDialogue].
     *
     * @param text The text of the option, with any case.
     *
     * @return `true` if a supported dialogue interface was open with the option on it and a click was attempted, or
     * `false` if not.
     */
    fun clickDialogueOption(text: String): Boolean {
        val activeInterface = bot.overlays[OptionDialogue::class] ?: return false
        val index = activeInterface.options.indexOfFirst { it.equals(text, ignoreCase = true) }
        if (index == -1) {
            bot.log("No dialogue option '$text' in ${activeInterface.options}.")
            return false
        }
        return clickDialogueOption(index + 1)
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
