package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.luna.game.event.impl.WidgetItemClickEvent;
import io.luna.net.msg.out.InventoryOverlayMessageWriter;
import io.luna.net.msg.out.WidgetAnimationMessageWriter;
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter;
import io.luna.net.msg.out.WidgetItemModelMessageWriter;
import io.luna.net.msg.out.WidgetItemsMessageWriter;
import io.luna.net.msg.out.WidgetMobModelMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.luna.net.msg.out.WidgetVisibilityMessageWriter;
import world.player.QuestJournalInterface;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Definition} implementation representing data for an interface-based widget.
 *
 * @author lare96
 */
public final class WidgetDefinition implements Definition {

    /**
     * An enum representing the different widget types.
     */
    public enum WidgetType {

        /**
         * Holds a potentially scrollable interface with child widgets, may also hold hidden widgets. An example of
         * this is the {@link QuestJournalInterface}. Visibility of hidden widget(s) can be toggled with
         * {@link WidgetVisibilityMessageWriter}. These types of widgets are also used with
         * {@link InventoryOverlayMessageWriter}.
         */
        DEFAULT(0),

        /**
         * Holds a range of items with option actions. Items can be displayed using {@link WidgetIndexedItemsMessageWriter} or
         * {@link WidgetItemsMessageWriter}. The option actions can be handled by intercepting one of the
         * {@link WidgetItemClickEvent} implementations.
         */
        ITEMS_WITH_ACTIONS(2),

        /**
         * Holds a space that can be filled with a color.
         */
        COLOR(3),

        /**
         * Holds a string of text. Can be modified with {@link WidgetTextMessageWriter}.
         */
        TEXT(4),

        /**
         * Holds two static images (enabled and disabled).
         */
        STATIC_MODEL(5),

        /**
         * Holds a model. A model can be displayed using {@link WidgetItemModelMessageWriter} or
         * {@link WidgetMobModelMessageWriter}. The displayed model can be animated using
         * {@link WidgetAnimationMessageWriter}.
         */
        MODEL(6),

        /**
         * Holds item models similar to {@link #ITEMS_WITH_ACTIONS}. Seems to only be used by the trading and
         * dueling interfaces.
         */
        UNKNOWN(7),

        /**
         * Holds a string of text on a fullscreen interface. Can be modified with {@link WidgetTextMessageWriter}.
         */
        FULLSCREEN_TEXT(8);

        /**
         * An immutable map of opcodes to instances.
         */
        public static final ImmutableMap<Integer, WidgetType> ALL;

        static {
            ImmutableMap.Builder<Integer, WidgetType> all = ImmutableMap.builder();
            for (WidgetType widgetType : values()) {
                all.put(widgetType.opcode, widgetType);
            }
            ALL = all.build();
        }

        /**
         * The widget type opcode.
         */
        private final int opcode;

        /**
         * Creates a new {@link WidgetDefinition}.
         *
         * @param opcode The widget type opcode.
         */
        WidgetType(int opcode) {
            this.opcode = opcode;
        }

        /**
         * @return The widget type opcode.
         */
        public int getOpcode() {
            return opcode;
        }
    }

    /**
     * The repository that will hold these definitions.
     */
    public static final DefinitionRepository<WidgetDefinition> ALL = new ArrayDefinitionRepository<>(18_786);

    /**
     * The widget id.
     */
    private final int id;

    /**
     * The parent widget id.
     */
    private final int parentId;

    /**
     * The widget type.
     */
    private final WidgetType type;

    /**
     * The CS1 opcodes.
     */
    private final int[][] cs1opcodes;

    /**
     * The widget children.
     */
    private final ImmutableList<Integer> children;

    /**
     * If this widget is an inventory.
     */
    private final boolean inventory;

    /**
     * The widget action click options.
     */
    private final ImmutableList<String> options;

    /**
     * The disabled text on the widget.
     */
    private final String disabledText;

    /**
     * The enabled text on the widget.
     */
    private final String enabledText;

    /**
     * The disabled animation on the widget.
     */
    private final int disabledAnimation;

    /**
     * The enabled animation on the widget.
     */
    private final int enabledAnimation;

    /**
     * Creates a new {@link WidgetDefinition}.
     *
     * @param id The widget id.
     * @param parentId The parent widget id.
     * @param type The widget type.
     * @param cs1opcodes The CS1 opcodes.
     * @param children The widget children.
     * @param inventory If this widget is an inventory.
     * @param options The widget action click options.
     * @param disabledText The disabled text on the widget
     * @param enabledText The enabled text on the widget.
     * @param disabledAnimation The disabled animation on the widget.
     * @param enabledAnimation The enabled animation on the widget.
     */
    public WidgetDefinition(int id, int parentId, WidgetType type, int[][] cs1opcodes,
                            Integer[] children, boolean inventory, String[] options, String disabledText,
                            String enabledText, int disabledAnimation, int enabledAnimation) {
        requireNonNull(type, "Widget type cannot be [null].");
        this.id = id;
        this.parentId = parentId;
        this.type = type;
        this.cs1opcodes = cs1opcodes;
        this.children = ImmutableList.copyOf(children);
        this.inventory = inventory;
        this.options = ImmutableList.copyOf(options);
        this.disabledText = disabledText;
        this.enabledText = enabledText;
        this.disabledAnimation = disabledAnimation;
        this.enabledAnimation = enabledAnimation;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("parentId", parentId)
                .add("type", type)
                .add("cs1opcodes", cs1opcodes)
                .add("children", children)
                .add("inventory", inventory)
                .add("options", options)
                .add("disabledText", disabledText)
                .add("enabledText", enabledText)
                .add("disabledAnimation", disabledAnimation)
                .add("enabledAnimation", enabledAnimation)
                .toString();
    }

    /**
     * @return The parent widget id.
     */
    public int getParentId() {
        return parentId;
    }

    /**
     * @return The widget type.
     */
    public WidgetType getType() {
        return type;
    }

    /**
     * @return The CS1 data at {@code csIndex} and {@code dataIndex}.
     */
    public int getCs1(int csIndex, int dataIndex) {
        return cs1opcodes[csIndex][dataIndex];
    }

    /**
     * @return The widget children.
     */
    public ImmutableList<Integer> getChildren() {
        return children;
    }

    /**
     * @return If this widget is an inventory.
     */
    public boolean isInventory() {
        return inventory;
    }

    /**
     * @return The widget action click options.
     */
    public ImmutableList<String> getOptions() {
        return options;
    }

    /**
     * @return The disabled text on the widget.
     */
    public String getDisabledText() {
        return disabledText;
    }

    /**
     * @return The enabled text on the widget.
     */
    public String getEnabledText() {
        return enabledText;
    }

    /**
     * @return The disabled animation on the widget.
     */
    public int getDisabledAnimation() {
        return disabledAnimation;
    }

    /**
     * @return The enabled animation on the widget.
     */
    public int getEnabledAnimation() {
        return enabledAnimation;
    }
}
