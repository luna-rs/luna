package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import io.luna.game.event.impl.WidgetItemClickEvent;
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter;
import io.luna.net.msg.out.WidgetItemModelMessageWriter;
import io.luna.net.msg.out.WidgetItemsMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;

public final class WidgetDefinition implements Definition {
    public enum WidgetType {
UNKNOWN(-1),

        /**
         * Holds a range of items with option actions. Items can be displayed using {@link WidgetIndexedItemsMessageWriter} or
         * {@link WidgetItemsMessageWriter}. The option actions can be handled by intercepting one of the
         * {@link WidgetItemClickEvent} implementations.
         */
        ITEMS_WITH_ACTIONS(2),

        /**
         * Holds a string of text. Can be modified with {@link WidgetTextMessageWriter}.
         */
        TEXT(4),

        /**
         * Holds an item model. An item can be displayed using {@link WidgetItemModelMessageWriter}.
         */
        ITEM(6);

        private final int opcode;

        WidgetType(int opcode) {
            this.opcode = opcode;
        }

        public int getOpcode() {
            return opcode;
        }
    }

    public static DefinitionRepository<WidgetDefinition> ALL = new ArrayDefinitionRepository<>(18_786);
    private final int id;
    private final int parentId;
    private final int type;
    private final int actionType;
    private final int[][] cs1opcodes;
    private final int[][] cs2opcodes;
    private final int[] children;
    private final boolean inventory;
    private final String[] options;
    private final String disabledText;
    private final String enabledText;
    private final int disabledAnimation;
    private final int enabledAnimation;
    private final String tooltip;

    public WidgetDefinition(int id, int parentId, int type, int actionType, int[][] cs1opcodes, int[][] cs2opcodes,
                            int[] children, boolean inventory, String[] options, String disabledText,
                            String enabledText, int disabledAnimation, int enabledAnimation, String tooltip) {
        this.id = id;
        this.parentId = parentId;
        this.type = type;
        this.actionType = actionType;
        this.cs1opcodes = cs1opcodes;
        this.cs2opcodes = cs2opcodes;
        this.children = children;
        this.inventory = inventory;
        this.options = options;
        this.disabledText = disabledText;
        this.enabledText = enabledText;
        this.disabledAnimation = disabledAnimation;
        this.enabledAnimation = enabledAnimation;
        this.tooltip = tooltip;
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
                .add("actionType", actionType)
                .add("cs1opcodes", cs1opcodes)
                .add("cs2opcodes", cs2opcodes)
                .add("children", children)
                .add("inventory", inventory)
                .add("options", options)
                .add("disabledText", disabledText)
                .add("enabledText", enabledText)
                .add("disabledAnimation", disabledAnimation)
                .add("enabledAnimation", enabledAnimation)
                .add("tooltip", tooltip)
                .toString();
    }

    public int getParentId() {
        return parentId;
    }

    public int getType() {
        return type;
    }

    public int getActionType() {
        return actionType;
    }

    public int[][] getCs1opcodes() {
        return cs1opcodes;
    }

    public int[][] getCs2opcodes() {
        return cs2opcodes;
    }

    public int[] getChildren() {
        return children;
    }

    public boolean isInventory() {
        return inventory;
    }

    public String[] getOptions() {
        return options;
    }

    public String getDisabledText() {
        return disabledText;
    }

    public String getEnabledText() {
        return enabledText;
    }

    public int getDisabledAnimation() {
        return disabledAnimation;
    }

    public int getEnabledAnimation() {
        return enabledAnimation;
    }

    public String getTooltip() {
        return tooltip;
    }
}
