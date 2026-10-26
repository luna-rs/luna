package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import game.player.QuestJournalInterface;
import io.luna.game.event.impl.WidgetItemClickEvent;
import io.luna.net.msg.out.InventoryOverlayMessageWriter;
import io.luna.net.msg.out.WidgetAnimationMessageWriter;
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter;
import io.luna.net.msg.out.WidgetItemModelMessageWriter;
import io.luna.net.msg.out.WidgetItemsMessageWriter;
import io.luna.net.msg.out.WidgetMobModelMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.luna.net.msg.out.WidgetVisibilityMessageWriter;

import static java.util.Objects.requireNonNull;

/**
 * Server-side metadata decoded from a 377 client interface widget.
 * <p>
 * Stores the widget's numeric identity, type, child ids, CS1 instruction blocks, item options, text and animation ids.
 * Layout and rendering details such as coordinates, fonts, colors and sprites remain client-side.
 *
 * @author lare96
 */
public final class WidgetDefinition implements Definition {

    /**
     * Widget kinds identified by the numeric type stored in the 377 interface cache.
     */
    public enum WidgetType {

        /**
         * A container of positioned child widgets, optionally scrollable or hidden until hovered.
         * {@link QuestJournalInterface} is one example. Hidden containers can be controlled with
         * {@link WidgetVisibilityMessageWriter}; {@link InventoryOverlayMessageWriter} opens an interface as an
         * inventory overlay.
         */
        CONTAINER(0),

        /**
         * A legacy type decoded with additional fields and text-style settings. The 377 client has no normal
         * rendering branch for this type.
         */
        LEGACY(1),

        /**
         * A grid of item icons and quantities with optional item actions and dragging behavior.
         * Contents can be updated with {@link WidgetIndexedItemsMessageWriter} or {@link WidgetItemsMessageWriter}.
         * Item actions are delivered through {@link WidgetItemClickEvent} implementations.
         */
        ITEM_GRID(2),

        /**
         * A filled or outlined rectangle with enabled, disabled and hover colors.
         */
        RECTANGLE(3),

        /**
         * Text with font, alignment, shadow and state-dependent text and colors.
         * Text can be updated with {@link WidgetTextMessageWriter}.
         */
        TEXT(4),

        /**
         * A two-dimensional sprite selected from enabled and disabled images.
         */
        SPRITE(5),

        /**
         * A three-dimensional model with state-dependent model and animation ids, zoom and rotation.
         * Models can be updated with {@link WidgetItemModelMessageWriter} or {@link WidgetMobModelMessageWriter},
         * and animated with {@link WidgetAnimationMessageWriter}.
         */
        MODEL(6),

        /**
         * A grid of item names and quantities rendered as text rather than item icons.
         * Contents can be updated with {@link WidgetIndexedItemsMessageWriter} or {@link WidgetItemsMessageWriter}.
         */
        ITEM_LIST(7),

        /**
         * A delayed hover tooltip rendered as text inside a bordered background box.
         * Its text supports CS1 substitutions and can be updated with {@link WidgetTextMessageWriter}.
         */
        TOOLTIP(8);

        /**
         * An immutable map of numeric cache type ids to widget kinds.
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
         * The numeric widget type id stored in the cache.
         */
        private final int opcode;

        /**
         * Creates a new {@link WidgetType}.
         *
         * @param opcode The numeric widget type id stored in the cache.
         */
        WidgetType(int opcode) {
            this.opcode = opcode;
        }

        /**
         * @return The numeric widget type id stored in the cache.
         */
        public int getOpcode() {
            return opcode;
        }
    }

    /**
     * The repository of decoded widget definitions.
     */
    public static final DefinitionRepository<WidgetDefinition> ALL = new ArrayDefinitionRepository<>(18_786);

    /**
     * The widget id.
     */
    private final int id;

    /**
     * The parent interface id, or {@code -1} when no parent is specified.
     */
    private final int parentId;

    /**
     * The widget type.
     */
    private final WidgetType type;

    /**
     * CS1 instruction blocks evaluated by the client for widget expressions.
     */
    private final int[][] cs1opcodes;

    /**
     * The immutable list of child widget ids in cache order.
     */
    private final ImmutableList<Integer> children;

    /**
     * The inventory flag retained by the decoder for text item lists.
     */
    private final boolean inventory;

    /**
     * The immutable list of item-slot option labels in cache order.
     */
    private final ImmutableList<String> options;

    /**
     * Text used when the widget's CS1 conditions are not met, or the tooltip text for type 8.
     */
    private final String disabledText;

    /**
     * Alternate text used when the widget's CS1 conditions are met.
     */
    private final String enabledText;

    /**
     * The model animation id used when CS1 conditions are not met, or {@code -1} for no animation on model widgets.
     */
    private final int disabledAnimation;

    /**
     * The model animation id used when CS1 conditions are met, or {@code -1} for no animation on model widgets.
     */
    private final int enabledAnimation;

    /**
     * Creates a new {@link WidgetDefinition}.
     *
     * @param id The widget id.
     * @param parentId The parent interface id, or {@code -1} when unspecified.
     * @param type The widget type.
     * @param cs1opcodes The client-side CS1 instruction blocks.
     * @param children The child widget ids, copied into an immutable list.
     * @param inventory The inventory flag retained by the decoder.
     * @param options The item-slot option labels, copied into an immutable list.
     * @param disabledText The default or disabled-state text.
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
    public int id() {
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
     * @return The parent interface id, or {@code -1} when unspecified.
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
     * Returns an instruction value from a CS1 block.
     *
     * @param csIndex The instruction block index.
     * @param dataIndex The value index within the block.
     * @return The instruction value at the supplied indexes.
     * @throws ArrayIndexOutOfBoundsException If either index is outside its array bounds.
     */
    public int getCs1(int csIndex, int dataIndex) {
        return cs1opcodes[csIndex][dataIndex];
    }

    /**
     * @return The immutable list of child widget ids in cache order.
     */
    public ImmutableList<Integer> getChildren() {
        return children;
    }

    /**
     * @return The inventory flag retained by the decoder for text item lists.
     */
    public boolean isInventory() {
        return inventory;
    }

    /**
     * @return The immutable list of item-slot option labels in cache order.
     */
    public ImmutableList<String> getOptions() {
        return options;
    }

    /**
     * @return The default or disabled-state text, including tooltip text for type 8.
     */
    public String getDisabledText() {
        return disabledText;
    }

    /**
     * @return The alternate text used when CS1 conditions are met.
     */
    public String getEnabledText() {
        return enabledText;
    }

    /**
     * @return The disabled-state model animation id, or {@code -1} for no animation on model widgets.
     */
    public int getDisabledAnimation() {
        return disabledAnimation;
    }

    /**
     * @return The enabled-state model animation id, or {@code -1} for no animation on model widgets.
     */
    public int getEnabledAnimation() {
        return enabledAnimation;
    }
}
