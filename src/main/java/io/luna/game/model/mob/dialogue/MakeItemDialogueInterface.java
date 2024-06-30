package io.luna.game.model.mob.dialogue;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.DialogueInterface;
import io.luna.net.msg.out.WidgetItemModelMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;

/**
 * A {@link DialogueInterface} implementation that opens a "Make item" dialogue.
 *
 * @author lare96 
 */
public class MakeItemDialogueInterface extends DialogueInterface {

    /**
     * The items.
     */
    private final int[] items;

    /**
     * Creates a new {@link MakeItemDialogueInterface}.
     *
     * @param items The items.
     */
    public MakeItemDialogueInterface(int... items) {
        super(DialogueUtils.makeItemDialogue(items.length));
        this.items = items;
    }

    /**
     * Applies {@link #makeItem(Player, int, int, int)} for {@code index}.
     *
     * @param player The player.
     * @param index The index.
     * @param forAmount The amount.
     */
    public final void makeItemIndex(Player player, int index, int forAmount) {
        makeItem(player, items[index], index, forAmount);
    }

    /**
     * A function invoked when the "Make ..." buttons are clicked.
     *
     * @param player The player.
     * @param id The identifier of the item clicked.
     * @param index The item index.
     * @param forAmount The amount clicked.
     */
    public void makeItem(Player player, int id, int index, int forAmount) {

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
            String itemName = ItemDefinition.ALL.retrieve(id).getName();
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