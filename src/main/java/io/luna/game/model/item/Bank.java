package io.luna.game.model.item;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.RefreshListener.PlayerRefreshListener;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.InventoryOverlayInterface;
import io.luna.net.msg.out.ConfigMessageWriter;

import java.util.OptionalInt;

/**
 * An item container model representing a player's bank.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Bank extends ItemContainer {

    /**
     * The interface that will be displayed when the bank opens.
     */
    public final class BankInterface extends InventoryOverlayInterface {

        /**
         * Creates a new {@link BankInterface}.
         */
        public BankInterface() {
            super(5292, 5063);
        }

        @Override
        public void onClose(Player player) {
            inventory.resetSecondaryRefresh();
        }
    }

    /**
     * The player.
     */
    private final Player player;

    /**
     * The inventory.
     */
    private final Inventory inventory;

    /**
     * The banking interface.
     */
    private BankInterface bankInterface = new BankInterface();

    /**
     * If currently withdrawing items noted.
     */
    private boolean withdrawAsNote;

    /**
     * Creates a new {@link Bank}.
     *
     * @param player The player.
     */
    public Bank(Player player) {
        super(352, StackPolicy.ALWAYS, 5382);
        this.player = player;
        inventory = player.getInventory();

        setListeners(new PlayerRefreshListener(player, "You do not have enough bank space to deposit that."));
    }

    /**
     * Opens the banking interface.
     */
    public void open() {
        if (!isOpen()) {
            disableEvents();
            try {
                setWithdrawAsNote(false);

                // Display items on interface.
                clearSpaces();
                inventory.setSecondaryRefresh(5064);
                inventory.refreshSecondary(player); // Refresh inventory onto bank.
                refreshPrimary(player); // Refresh bank.

                // Open interface.
                player.getInterfaces().open(bankInterface);
            } finally {
                enableEvents();
            }
        }
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
        item = item.withAmount(amount);

        // Determine if enough space in bank.
        Item depositItem = new Item(id, amount);
        if (!hasSpaceFor(depositItem)) {
            fireCapacityExceededEvent();
            return false;
        }

        // Deposit item.
        if (inventory.remove(item)) {
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

        if (withdrawAsNote) {
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
        item = item.withAmount(amount);
        if (remove(item)) {
            Item withdrawItem = new Item(id, amount);
            return inventory.add(withdrawItem);
        }
        return false;
    }

    /**
     * Determines if the {@link BankInterface} is open.
     *
     * @return {@code true} if the banking interface is open.
     */
    public boolean isOpen() {
        return bankInterface.isOpen();
    }

    public boolean isWithdrawAsNote() {
        return withdrawAsNote;
    }

    public void setWithdrawAsNote(boolean value) {
        if (withdrawAsNote != value) {
            withdrawAsNote = value;
            player.queue(new ConfigMessageWriter(115, value ? 1 : 0));
        }
    }
}