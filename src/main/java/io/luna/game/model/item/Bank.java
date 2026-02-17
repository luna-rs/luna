package io.luna.game.model.item;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.RefreshListener.PlayerRefreshListener;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.InventoryOverlayInterface;
import io.luna.game.model.mob.overlay.StandardInterface;
import io.luna.game.model.mob.varp.PersistentVarp;
import io.luna.net.msg.out.WidgetItemsMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.OptionalInt;

/**
 * An {@link ItemContainer} implementation representing a {@link Player}'s bank.
 * <p>
 * The bank is a large, stack-always container (size 352) backed by the bank interface widget {@code 5382}. It supports:
 * <ul>
 *     <li>opening/closing the banking overlay UI</li>
 *     <li>depositing items from inventory into the bank</li>
 *     <li>withdrawing items from the bank into inventory, optionally as notes</li>
 * </ul>
 * <p>
 * <b>Note handling:</b>
 * <ul>
 *     <li>Deposits normalize noted items into their unnoted id (so banks store the “real” item).</li>
 *     <li>Withdrawals may convert to noted form when {@link PersistentVarp#WITHDRAW_AS_NOTE} is enabled and the item has
 *     a valid noted id.</li>
 * </ul>
 * <p>
 * <b>UI updates:</b> The bank and inventory are refreshed when opening and when the underlying container changes via
 * {@link PlayerRefreshListener}.
 *
 * @author lare96
 */
public final class Bank extends ItemContainer {

    /**
     * The overlay interface shown when the player opens the bank.
     * <p>
     * This is an {@link InventoryOverlayInterface} which shows the bank interface (primary) and an inventory overlay.
     * When the overlay is closed, the inventory secondary widget is cleared.
     */
    public final class BankInterface extends InventoryOverlayInterface {

        /**
         * Creates a new {@link BankInterface}.
         * <p>
         * {@code 5292} is the bank interface id and {@code 5063} is the inventory overlay component.
         */
        public BankInterface() {
            super(5292, 5063);
        }

        @Override
        public void onClose(Player player) {
            inventory.clearSecondaryWidget();
        }
    }

    /**
     * A “bank-like” interface that reuses the bank widget layout to display an arbitrary list of items.
     * <p>
     * This is useful for any UI that wants the familiar bank view (scrollable item grid), without binding to the
     * player's actual bank contents. Implementations provide their own item list via {@link #buildDisplayItems(Player)}.
     * <p>
     * <b>Item reduction:</b> The list is reduced into stacked entries by id prior to display (all identical ids are
     * combined by summing amounts). This is done using a {@link Multiset}.
     * <p>
     * <b>UI text:</b> Several header widgets are cleared on open, a custom title is written, and the default bank texts
     * are restored on close.
     */
    public static abstract class DynamicBankInterface extends StandardInterface {

        /**
         * The widgets whose text should be cleared when this interface opens.
         */
        private static final ImmutableList<WidgetTextMessageWriter> CLEAR_WIDGETS = ImmutableList.of(
                new WidgetTextMessageWriter("", 5388),
                new WidgetTextMessageWriter("", 5389),
                new WidgetTextMessageWriter("", 5390),
                new WidgetTextMessageWriter("", 5391),
                new WidgetTextMessageWriter("", 8132),
                new WidgetTextMessageWriter("", 8133));

        /**
         * The title displayed in the bank header widget.
         */
        private final String title;

        /**
         * Creates a new {@link DynamicBankInterface} using the standard bank interface id.
         *
         * @param title The header title to display (widget {@code 5383}).
         */
        public DynamicBankInterface(String title) {
            super(5292);
            this.title = title;
        }

        /**
         * Builds the list of items that will be displayed in the bank item grid.
         * <p>
         * The returned list will be mutated by {@link #onOpen(Player)} to reduce/stack identical ids.
         *
         * @param player The player opening the interface.
         * @return A mutable list of items to display.
         */
        public abstract ArrayList<Item> buildDisplayItems(Player player);

        @Override
        public void onOpen(Player player) {
            ArrayList<Item> displayItems = buildDisplayItems(player);

            /*
             * Reduce the list into stacked id to totalAmount entries.
             */
            Multiset<Integer> reduceItems = HashMultiset.create();
            Iterator<Item> displayItemsIterator = displayItems.iterator();
            while (displayItemsIterator.hasNext()) {
                Item item = displayItemsIterator.next();
                if (item == null || item.getAmount() == 0) {
                    continue;
                }
                reduceItems.add(item.getId(), item.getAmount());
                displayItemsIterator.remove();
            }

            for (Entry<Integer> entry : reduceItems.entrySet()) {
                int count = entry.getCount();
                displayItems.add(new Item(entry.getElement(), count));
            }

            player.queue(new WidgetItemsMessageWriter(5382, displayItems));
            CLEAR_WIDGETS.forEach(player::queue);
            player.sendText(title, 5383);
        }

        @Override
        public void onClose(Player player) {
            /*
             * Restore default bank header labels.
             */
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
     * The owning player.
     */
    private final Player player;

    /**
     * The player's inventory.
     */
    private final Inventory inventory;

    /**
     * The overlay interface instance used for banking.
     */
    private final BankInterface bankInterface = new BankInterface();

    /**
     * Creates a new {@link Bank}.
     *
     * @param player The owning player.
     */
    public Bank(Player player) {
        super(352, StackPolicy.ALWAYS, 5382);
        this.player = player;
        inventory = player.getInventory();

        /*
         * Keeps the bank interface and inventory overlay in sync when the container changes.
         */
        setListeners(new PlayerRefreshListener(
                player,
                bankInterface,
                "You do not have enough bank space to deposit that."
        ));
    }

    /**
     * Opens the bank overlay interface if it is not already open.
     * <p>
     * This method temporarily stops container event dispatch while it prepares the UI state, then restarts events.
     * The open sequence:
     * <ul>
     *     <li>resets withdraw-as-note varp to "item"</li>
     *     <li>clears placeholder spaces</li>
     *     <li>renders inventory as a secondary widget on the bank interface</li>
     *     <li>refreshes both inventory overlay and bank item grid</li>
     *     <li>opens the overlay</li>
     * </ul>
     */
    public void open() {
        if (!isOpen()) {
            stopEvents();
            try {
                player.getVarpManager().setAndSendValue(PersistentVarp.WITHDRAW_AS_NOTE, 0);

                clearSpaces();
                inventory.setSecondaryWidget(5064);
                inventory.updateSecondaryWidget(player);
                updatePrimaryWidget(player);

                player.getOverlays().open(bankInterface);
            } finally {
                startEvents();
            }
        }
    }

    /**
     * Deposits an item from inventory into the bank.
     *
     * @param inventoryIndex The inventory slot index.
     * @param amount The requested deposit amount.
     * @return {@code true} if the deposit succeeded.
     */
    public boolean deposit(int inventoryIndex, int amount) {
        Item item = inventory.get(inventoryIndex);
        if (item == null || amount < 1) {
            return false;
        }

        /*
         * Clamp to the actual amount present in inventory (by the inventory item id).
         */
        int existingAmount = inventory.computeAmountForId(item.getId());
        amount = Math.min(amount, existingAmount);

        /*
         * Remove what the player actually has (original id).
         */
        item = item.withAmount(amount);

        /*
         * Convert to unnoted id for bank storage.
         */
        int unnotedId = item.getItemDef().getUnnotedId().orElse(item.getId());
        Item unnotedItem = item.withId(unnotedId);

        if (!hasSpaceFor(unnotedItem)) {
            onCapacityExceeded();
            return false;
        }

        if (inventory.remove(item)) {
            return add(unnotedItem);
        }
        return false;
    }

    /**
     * Withdraws an item from the bank into the inventory.
     *
     * @param bankIndex The bank slot index.
     * @param amount The requested withdraw amount.
     * @return {@code true} if the withdrawal succeeded.
     */
    public boolean withdraw(int bankIndex, int amount) {
        Item item = get(bankIndex);
        if (item == null || amount < 1) {
            return false;
        }

        int id = item.getId();
        int existingAmount = item.getAmount();
        int remaining = inventory.computeRemainingSize();
        amount = Math.min(amount, existingAmount);

        if (player.getVarpManager().getValue(PersistentVarp.WITHDRAW_AS_NOTE) == 1) {
            OptionalInt noted = item.getItemDef().getNotedId();
            if (noted.isPresent() && !item.isDynamic()) {
                id = noted.getAsInt();
            } else {
                player.sendMessage("This item cannot be withdrawn as a note.");
            }
        }

        /*
         * For non-stackables, limit the withdraw amount by remaining slots and ensure enough inventory space.
         */
        ItemDefinition withdrawItemDef = ItemDefinition.ALL.retrieve(id);
        if (!withdrawItemDef.isStackable()) {
            if (remaining == 0) {
                inventory.onCapacityExceeded();
                return false;
            }
            amount = Math.min(amount, remaining);
        }

        /*
         * For stackables, ensure there's enough space to withdraw at 0 inventory capacity.
         */
        Item withdrawItem = item.withId(id).withAmount(amount);
        if (remaining == 0 && !inventory.hasSpaceFor(withdrawItem)) {
            inventory.onCapacityExceeded();
            return false;
        }

        if (remove(item)) {
            return inventory.add(withdrawItem);
        }
        return false;
    }

    /**
     * Returns {@code true} if the banking overlay interface is currently open.
     *
     * @return {@code true} if open.
     */
    public boolean isOpen() {
        return bankInterface.isOpen();
    }
}
