package io.luna.game.model.mob.dialogue;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetItemModelMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;

/**
 * A {@link DialogueInterface} implementation that opens a "Make item" dialogue.
 *
 * @author lare96 
 */
public class MakeItemDialogue extends DialogueInterface {

    /**
     * The items.
     */
    private final int[] items;

    /**
     * The text shown under each item, or {@code null} to show the item names.
     */
    private final String[] labels;

    /**
     * Creates a new {@link MakeItemDialogue} that shows the item names.
     *
     * @param items The items.
     */
    public MakeItemDialogue(int... items) {
        this(items, null);
    }

    /**
     * Creates a new {@link MakeItemDialogue} that shows {@code labels} under the items instead of their names.
     *
     * @param items The items.
     * @param labels The text under each item, in the same order, or {@code null} for the item names.
     */
    public MakeItemDialogue(int[] items, String[] labels) {
        super(DialogueUtils.makeItemDialogue(items.length));
        this.items = items;
        this.labels = labels;
    }

    /**
     * Applies {@link #make(Player, int, int, int)} for {@code index}.
     *
     * @param player The player.
     * @param index The index.
     * @param forAmount The amount.
     */
    public final void makeIndex(Player player, int index, int forAmount) {
        make(player, items[index], index, forAmount);
    }

    /**
     * A function invoked when the "Make ..." buttons are clicked.
     *
     * @param player The player.
     * @param id The identifier of the item clicked.
     * @param index The item index.
     * @param forAmount The amount clicked.
     */
    public void make(Player player, int id, int index, int forAmount) {

    }

    @Override
    public final boolean init(Player player) {
        int[] textWidgets = DialogueUtils.makeItemTextWidgets(items.length);
        int[] modelWidgets = DialogueUtils.makeItemModelWidgets(items.length);

        if (items.length == 1) {
            // Because 1 & 3 share the same interface, for some reason.
            player.queue(new WidgetTextMessageWriter("", 8889));
            player.queue(new WidgetTextMessageWriter("", 8897));
            player.queue(new WidgetItemModelMessageWriter(8883, 100, -1));
            player.queue(new WidgetItemModelMessageWriter(8885, 100, -1));
        }

        int index = 0;
        for (int id : items) {
            String itemName = labels != null ? labels[index] : ItemDefinition.ALL.retrieve(id).getName();
            if (items.length == 5) {
                itemName = DialogueUtils.makeItem5OptionsNameFix(itemName);
            }
            player.queue(new WidgetTextMessageWriter(itemName, textWidgets[index]));
            player.queue(new WidgetItemModelMessageWriter(modelWidgets[index], 175 - (items.length * 15), id));
            index++;
        }
        return true;
    }

    /**
     * Returns the amount of items on this dialogue interface.
     *
     * @return The item amount.
     */
    public final int getLength() {
        return items.length;
    }
}