package io.luna.game.model.mob.dialogue;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.DialogueInterface;
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter;

import java.util.OptionalInt;

/**
 * A {@link DialogueInterface} implementation that opens a dialogue which allows Players to destroy
 * untradeable items.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class DestroyItemDialogueInterface extends DialogueInterface {

    /**
     * The inventory index.
     */
    private final OptionalInt index;

    /**
     * The item identifier.
     */
    private final int itemId;

    /**
     * Creates a new {@link DestroyItemDialogueInterface}.
     *
     * @param index The inventory index.
     * @param itemId The item identifier.
     */
    public DestroyItemDialogueInterface(int index, int itemId) {
        super(14170);
        this.index = index == -1 ? OptionalInt.empty() : OptionalInt.of(index);
        this.itemId = itemId;
    }

    /**
     * Creates a new {@link DestroyItemDialogueInterface}.
     *
     * @param itemId The item identifier.
     */
    public DestroyItemDialogueInterface(int itemId) {
        this(-1, itemId);
    }

    @Override
    public boolean init(Player player) {

        // Send packets that build the interface.
        IndexedItem item = new IndexedItem(0, itemId, 1);
        player.queue(new WidgetIndexedItemsMessageWriter(14171, item));

        player.sendText("Are you sure you want to destroy this item?", 14174);
        player.sendText("Yes", 14175);
        player.sendText("No", 14176);
        player.sendText("There is no way to get items", 14182);
        player.sendText("back after you have destroyed them.", 14183);
        player.sendText(getDestroyItemName(), 14184);
        return true;
    }

    /**
     * Destroys the item by removing it from the inventory.
     *
     * @param player The Player to destroy the item for.
     */
    public void destroyItem(Player player) {
        Inventory inventory = player.getInventory();

        int destroyIndex = index.orElse(inventory.computeIndexForId(itemId).orElse(-1));
        if (destroyIndex == -1 || inventory.get(destroyIndex).getId() != itemId) {
            player.getInterfaces().close();
            return;
        }

        inventory.remove(destroyIndex, inventory.get(destroyIndex));
        player.sendMessage("You destroy the " + getDestroyItemName() + ".");
        player.getInterfaces().close();
    }

    /**
     * Returns the to-be destroyed item's name.
     *
     * @return The item name.
     */
    private String getDestroyItemName() {
        return ItemDefinition.ALL.retrieve(itemId).getName();
    }
}