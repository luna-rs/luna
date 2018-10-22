package io.luna.game.model.item;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.InventoryOverlayInterface;

import java.util.ArrayDeque;
import java.util.OptionalInt;
import java.util.Queue;

/**
 * An item container model representing a player's bank.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Bank extends ItemContainer {

    /**
     * The player.
     */
    private final Player player;

    /**
     * The inventory.
     */
    private final Inventory inventory;

    /**
     * Creates a new {@link Bank}.
     *
     * @param player The player.
     */
    public Bank(Player player) {
        super(352, StackPolicy.ALWAYS, 5382);
        this.player = player;
        inventory = player.getInventory();

        setListeners(new RefreshListener(player, "You do not have enough bank space to deposit that."));
    }

    /**
     * Opens the banking interface.
     */
    public void open() {
        player.setWithdrawAsNote(false);
        clearSpaces();

        inventory.setSecondaryRefresh(5064);
        player.getInterfaces().open(new InventoryOverlayInterface(5292, 5063) {
            @Override
            public void onClose(Player player) {
                inventory.resetSecondaryRefresh();
            }
        });

        inventory.refreshSecondary(player);
        refreshPrimary(player);
    }

    /**
     * Deposits an item from the inventory.
     *
     * @param inventoryIndex The index of the item to deposit.
     * @param amount The amount to deposit.
     * @return {@code true} if successful.
     */
    public boolean deposit(int inventoryIndex, int amount) {

        // Return if item doesn't exist or invalid amount.
        Item item = inventory.get(inventoryIndex);
        if (item == null || amount < 1) {
            return false;
        }

        // Get correct item identifier and amount to deposit.
        int id = item.getItemDef().getUnnotedId().orElse(item.getId());
        int existingAmount = inventory.computeAmountForId(item.getId());
        amount = amount > existingAmount ? existingAmount : amount;

        // Determine if enough space in bank.
        Item depositItem = new Item(id, amount);
        if (!hasSpaceFor(depositItem)) {
            fireCapacityExceededEvent();
            return false;
        }

        // Deposit item.
        if (inventory.remove(depositItem)) {
            return add(depositItem);
        }
        return false;
    }

    /**
     * Withdraws an item from the bank.
     *
     * @param bankIndex The index of the item to withdraw.
     * @param amount The amount to withdraw.
     * @return {@code true} if successful.
     */
    public boolean withdraw(int bankIndex, int amount) {

        // Return if item doesn't exist or invalid amount.
        Item item = get(bankIndex);
        if (item == null || amount < 1) {
            return false;
        }

        // No free spaces in inventory.
        int remaining = inventory.computeRemainingSize();
        if (remaining < 1) {
            inventory.fireCapacityExceededEvent();
            return false;
        }

        // Get correct item identifier and amount to withdraw.
        int id = item.getId();
        int existingAmount = item.getAmount();
        amount = amount > existingAmount ? existingAmount : amount;

        if (player.isWithdrawAsNote()) {
            OptionalInt notedId = item.getItemDef().getNotedId();
            if (notedId.isPresent()) {
                id = notedId.getAsInt();
            } else {
                player.sendMessage("This item cannot be withdrawn as a note.");
            }
        }

        // For non-stackable items, make the amount equal to free slots left if necessary.
        ItemDefinition withdrawItemDef = ItemDefinition.ALL.retrieve(id);
        if (!withdrawItemDef.isStackable()) {
            amount = amount > remaining ? remaining : amount;
        }

        // Withdraw the item.
        Item withdrawItem = new Item(id, amount);
        if (remove(withdrawItem)) {
            return inventory.add(withdrawItem);
        }
        return false;
    }

    /**
     * Shifts all items to the left, clearing all {@code null} elements in between {@code non-null} elements. Does
     * not fire any events.
     */
    public void clearSpaces() {
        if (size > 0) {
            // Create queue of pending indexes and cache this container's size.
            Queue<Integer> indexes = new ArrayDeque<>(8);
            int shiftAmount = size;

            for (int index = 0; index < capacity; index++) {
                if (shiftAmount == 0) {
                    // No more items left to shift.
                    break;
                } else if (occupied(index)) {
                    // Item is present on this index.
                    Integer newIndex = indexes.poll();
                    if (newIndex != null) {
                        // Shift it to the left, if needed.
                        items[newIndex] = items[index];
                        items[index] = null;
                        indexes.add(index);
                    }
                    // We've encountered an item, decrement counter.
                    shiftAmount--;
                } else {
                    // No item on this index, add it to pending queue.
                    indexes.add(index);
                }
            }
        }
    }
}