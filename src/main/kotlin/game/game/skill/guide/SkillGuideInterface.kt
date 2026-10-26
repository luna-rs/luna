package game.skill.guide

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemsMessageWriter
import io.luna.net.msg.out.WidgetPositionMessageWriter
import io.luna.net.msg.out.WidgetScrollMessageWriter
import io.luna.net.msg.out.WidgetVisibilityMessageWriter

/**
 * A [StandardInterface] that shows a [SkillGuide] on the skill guide interface.
 *
 * Whenever the client closes an interface (or opens another one over it, including another guide), it throws away
 * everything that was sent to the interface's components, apart from item grids, and reloads them from the cache. So
 * [open] sets everything the guide uses (title, tab labels, which tabs are shown, tab colours, rows) after the open
 * packet, every time, and forgets what [Player.sendText] remembers having sent to those components.
 *
 * @author Hydrozoa
 */
class SkillGuideInterface(val guide: SkillGuide) : StandardInterface(INTERFACE) {

    companion object {

        /**
         * The skill guide interface.
         */
        const val INTERFACE = 8714

        /**
         * The title text.
         */
        const val TITLE = 8716

        /**
         * The "Members only skill" text. Every player on Luna is a member, so it is always blank.
         */
        const val MEMBERS_TEXT = 8849

        /**
         * The scroll pane holding the rows.
         */
        const val SCROLL_PANE = 8717

        /**
         * The item grid, one slot per row.
         */
        const val ITEM_GRID = 8847

        /**
         * The level text of the first row. The other rows follow on.
         */
        const val FIRST_LEVEL_TEXT = 8720

        /**
         * The description text of the first row. The other rows follow on.
         */
        const val FIRST_DESCRIPTION_TEXT = 8760

        /**
         * The category tab buttons, in tab order.
         */
        val TAB_BUTTONS = listOf(8846, 8823, 8824, 8827, 8837, 8840, 8843, 8859, 8862, 8865, 15303, 15306, 15309)

        /**
         * The layer each tab (and its part of the side panel's background) sits in, in tab order. Tabs 2 and 3 share
         * one layer.
         */
        val TAB_LAYERS = listOf(8844, 8813, 8813, 8825, 8828, 8838, 8841, 8850, 8860, 8863, 15294, 15304, 15307)

        /**
         * The box drawn behind tab 3. It shares a layer with tab 2, so it is moved out of view when a guide has
         * exactly two categories.
         */
        const val TAB_3_BOX = 8822

        /**
         * How far tab 3's box and label are moved to get them out of view.
         */
        const val OUT_OF_VIEW = 1000

        /**
         * The colour of the selected tab.
         */
        const val SELECTED_COLOUR = 0xFF0000

        /**
         * The colour of the other tabs.
         */
        const val TAB_COLOUR = 0xFFFFFF

        /**
         * Every text component the guide sets.
         */
        val TEXT_COMPONENTS = listOf(TITLE, MEMBERS_TEXT) + TAB_BUTTONS +
                (FIRST_LEVEL_TEXT until FIRST_LEVEL_TEXT + SkillGuideCategory.MAX_ENTRIES) +
                (FIRST_DESCRIPTION_TEXT until FIRST_DESCRIPTION_TEXT + SkillGuideCategory.MAX_ENTRIES)
    }

    /**
     * The index of the category being shown.
     */
    var selected = 0
        private set

    override fun open(plr: Player) {
        // The open packet makes the client reset the interface, so everything is sent after it.
        super.open(plr)

        // The client has gone back to the cache's text, so the text cache no longer matches it.
        TEXT_COMPONENTS.forEach(plr::clearText)

        val count = guide.categories.size
        plr.sendText(guide.name, TITLE)
        plr.sendText("", MEMBERS_TEXT)

        // A guide with one category has no tabs. Otherwise, show one tab per category.
        val tabs = if (count > 1) count else 0
        for ((index, button) in TAB_BUTTONS.withIndex()) {
            plr.sendText(if (index < tabs) guide.categories[index].name else "", button)
            plr.queue(WidgetColourMessageWriter(button, TAB_COLOUR))
        }
        for ((index, layer) in TAB_LAYERS.withIndex()) {
            if (index > 0 && TAB_LAYERS[index - 1] == layer) {
                continue
            }
            plr.queue(WidgetVisibilityMessageWriter(layer, index >= tabs))
        }
        val offset = if (tabs == 2) OUT_OF_VIEW else 0
        plr.queue(WidgetPositionMessageWriter(TAB_3_BOX, offset, offset))
        plr.queue(WidgetPositionMessageWriter(TAB_BUTTONS[2], offset, offset))
        show(plr, 0)
    }

    /**
     * Shows the category at [index]: its rows, with the list scrolled back to the top and its tab highlighted.
     */
    fun show(plr: Player, index: Int) {
        val category = guide.categories[index]
        val items = arrayOfNulls<Item>(SkillGuideCategory.MAX_ENTRIES)
        for (row in 0 until SkillGuideCategory.MAX_ENTRIES) {
            val entry = category.entries.getOrNull(row)
            plr.sendText(entry?.level ?: "", FIRST_LEVEL_TEXT + row)
            plr.sendText(entry?.text ?: "", FIRST_DESCRIPTION_TEXT + row)
            items[row] = entry?.item?.let { Item(it) }
        }
        plr.queue(WidgetItemsMessageWriter(ITEM_GRID, items))
        plr.queue(WidgetScrollMessageWriter(SCROLL_PANE, 0))

        if (guide.categories.size > 1) {
            plr.queue(WidgetColourMessageWriter(TAB_BUTTONS[selected], TAB_COLOUR))
            plr.queue(WidgetColourMessageWriter(TAB_BUTTONS[index], SELECTED_COLOUR))
        }
        selected = index
    }
}
