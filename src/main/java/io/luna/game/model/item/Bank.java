package io.luna.game.model.item;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.RefreshListener.PlayerRefreshListener;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.InventoryOverlayInterface;
import io.luna.game.model.mob.inter.StandardInterface;
import io.luna.game.model.mob.varp.PersistentVarp;
import io.luna.net.msg.out.WidgetItemsMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;

/**
 * An item container model representing a player's bank.
 *
 * @author lare96 
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
     * An interface that uses the banking interface to display miscellaneous items.
     */
    public static abstract class DynamicBankInterface extends StandardInterface {

        /**
         * The widgets to clear when the interface opens.
         */
        private static final ImmutableList<WidgetTextMessageWriter> CLEAR_WIDGETS = ImmutableList.of(
                new WidgetTextMessageWriter("", 5388),
                new WidgetTextMessageWriter("", 5389),
                new WidgetTextMessageWriter("", 5390),
                new WidgetTextMessageWriter("", 5391),
                new WidgetTextMessageWriter("", 8132),
                new WidgetTextMessageWriter("", 8133));

        /**
         * The title.
         */
        private final String title;

        /**
         * Creates a new {@link DynamicBankInterface}.
         */
        public DynamicBankInterface(String title) {
            super(5292);
            this.title = title;
        }

        /**
         * Builds the list of items that will be displayed.
         */
        public abstract List<Item> buildDisplayItems(Player player);

        @Override
        public void onOpen(Player player) {
            List<Item> displayItems = buildDisplayItems(player);
            Multiset<Integer> reduceItems = HashMultiset.create();
            Iterator<Item> displayItemsIterator = displayItems.iterator();
            while (displayItemsIterator.hasNext()) {
                Item item = displayItemsIterator.next();
                reduceItems.add(item.getId(), item.getAmount());
                displayItemsIterator.remove();
            }
            for (Entry<Integer> entry : reduceItems.entrySet()) {
                int id = entry.getElement();
                int amount = entry.getCount();
                displayItems.add(new Item(id, amount));
            }
            player.queue(new WidgetItemsMessageWriter(5382, displayItems));
            CLEAR_WIDGETS.forEach(player::queue);
            player.sendText(title, 5383);
        }

        @Override
        public void onClose(Player player) {
            player.sendText("The Bank of Runescape", 5383);
            player.sendText("Withdraw as:", 5388);
            player.sendText("Item", 5389);
            player.sendText("Rearrange mode:", 5390);
            player.sendText("Note", 5391);
            player.sendText("Insert", 8132);
            player.sendText("Swap", 8133);
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
    private final BankInterface bankInterface = new BankInterface();

    /**
     * Creates a new {@link Bank}.
     *
     * @param player The player.
     */
    public Bank(Player player) {
        super(352, StackPolicy.ALWAYS, 5382);
        this.player = player;
        inventory = player.getInventory();

        setListeners(new PlayerRefreshListener(player, bankInterface, "You do not have enough bank space to deposit that."));
    }

    /**
     * Opens the banking interface.
     */
    public void open() {
        if (!isOpen()) {
            disableEvents();
            try {
                player.getVarpManager().setAndSendValue(PersistentVarp.WITHDRAW_AS_NOTE, 0);

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
        amount = Math.min(amount, existingAmount);
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
        amount = Math.min(amount, existingAmount);

        if (player.getVarpManager().getValue(PersistentVarp.WITHDRAW_AS_NOTE) == 1) {
            OptionalInt notedId = item.getItemDef().getUnnotedId();
            if (notedId.isPresent()) {
                id = notedId.getAsInt();
            } else {
                player.sendMessage("This item cannot be withdrawn as a note.");
            }
        }

        // For non-stackable items, make the amount equal to free slots left if necessary.
        ItemDefinition withdrawItemDef = ItemDefinition.ALL.retrieve(id);
        if (!withdrawItemDef.isStackable()) {
            amount = Math.min(amount, remaining);
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
}