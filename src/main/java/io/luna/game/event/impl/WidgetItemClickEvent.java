package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;

/**
 * An item widget-click based event. Not intended for interception.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class WidgetItemClickEvent extends PlayerEvent {

    /**
     * An event sent when a player clicks a widget item's first index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class WidgetItemFirstClickEvent extends WidgetItemClickEvent {

        /**
         * Creates a new {@link WidgetItemFirstClickEvent}.
         *
         * @param player The player.
         * @param index The item's index.
         * @param widgetId The widget identifier.
         * @param itemId The item identifier.
         */
        public WidgetItemFirstClickEvent(Player player, int index, int widgetId, int itemId) {
            super(player, index, widgetId, itemId);
        }
    }

    /**
     * An event sent when a player clicks a widget item's second index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class WidgetItemSecondClickEvent extends WidgetItemClickEvent {

        /**
         * Creates a new {@link WidgetItemSecondClickEvent}.
         *
         * @param player The player.
         * @param index The item's index.
         * @param widgetId The widget identifier.
         * @param itemId The item identifier.
         */
        public WidgetItemSecondClickEvent(Player player, int index, int widgetId, int itemId) {
            super(player, index, widgetId, itemId);
        }
    }

    /**
     * An event sent when a player clicks a widget item's third index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class WidgetItemThirdClickEvent extends WidgetItemClickEvent {

        /**
         * Creates a new {@link WidgetItemThirdClickEvent}.
         *
         * @param player The player.
         * @param index The item's index.
         * @param widgetId The widget identifier.
         * @param itemId The item identifier.
         */
        public WidgetItemThirdClickEvent(Player player, int index, int widgetId, int itemId) {
            super(player, index, widgetId, itemId);
        }
    }

    /**
     * An event sent when a player clicks a widget item's fourth index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class WidgetItemFourthClickEvent extends WidgetItemClickEvent {

        /**
         * Creates a new {@link WidgetItemFourthClickEvent}.
         *
         * @param player The player.
         * @param index The item's index.
         * @param widgetId The widget identifier.
         * @param itemId The item identifier.
         */
        public WidgetItemFourthClickEvent(Player player, int index, int widgetId, int itemId) {
            super(player, index, widgetId, itemId);
        }
    }

    /**
     * An event sent when a player clicks a widget item's fifth index.
     *
     * @author lare96 <http://github.org/lare96>
     */
    public static final class WidgetItemFifthClickEvent extends WidgetItemClickEvent {

        /**
         * Creates a new {@link WidgetItemFifthClickEvent}.
         *
         * @param player The player.
         * @param index The item's index.
         * @param widgetId The widget identifier.
         * @param itemId The item identifier.
         */
        public WidgetItemFifthClickEvent(Player player, int index, int widgetId, int itemId) {
            super(player, index, widgetId, itemId);
        }
    }

    /**
     * The item's index.
     */
    private final int index;

    /**
     * The widget identifier.
     */
    private final int widgetId;

    /**
     * The item identifier.
     */
    private final int itemId;

    /**
     * Creates a new {@link WidgetItemClickEvent}.
     *
     * @param player The player.
     * @param index The item's index.
     * @param widgetId The widget identifier.
     * @param itemId The item identifier.
     */
    public WidgetItemClickEvent(Player player, int index, int widgetId, int itemId) {
        super(player);
        this.index = index;
        this.widgetId = widgetId;
        this.itemId = itemId;
    }

    /**
     * @return The item's index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return The widget identifier.
     */
    public int getWidgetId() {
        return widgetId;
    }

    /**
     * @return The item identifier.
     */
    public int getItemId() {
        return itemId;
    }
}