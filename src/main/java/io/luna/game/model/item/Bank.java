package io.luna.game.model.item;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.attr.AttributeValue;
import io.luna.net.msg.out.InfoMessageWriter;
import io.luna.net.msg.out.InventoryOverlayMessageWriter;
import io.luna.net.msg.out.StateMessageWriter;

/**
 * An {@link ItemContainer} implementation that manages the bank for a {@link Player}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Bank extends ItemContainer {

    /**
     * An {@link ItemContainerAdapter} implementation that listens for changes to the bank.
     */
    private static final class BankListener extends ItemContainerAdapter {

        /**
         * Creates a new {@link BankListener}.
         *
         * @param player The {@link Player} this instance is dedicated to.
         */
        public BankListener(Player player) {
            super(player);
        }

        @Override
        public int getWidgetId() {
            return BANK_DISPLAY_ID;
        }

        @Override
        public String getCapacityExceededMsg() {
            return "You do not have enough bank space to deposit that.";
        }
    }

    /**
     * The size of all bank instances.
     */
    public static final int SIZE = 352;

    /**
     * The main interface identifier for banks.
     */
    private static final int INTERFACE_ID = 5292;

    /**
     * The inventory overlay identifier for banks.
     */
    private static final int INVENTORY_OVERLAY_ID = 5063;

    /**
     * The bank item display widget identifier.
     */
    private static final int BANK_DISPLAY_ID = 5382;

    /**
     * The inventory item display widget identifier.
     */
    private static final int INVENTORY_DISPLAY_ID = 5064;

    /**
     * The withdraw mode state identifier.
     */
    private static final int WITHDRAW_MODE_STATE_ID = 115;

    /**
     * The {@link Player} this instance is dedicated to.
     */
    private final Player player;

    /**
     * Creates a new {@link Bank}.
     *
     * @param player The {@link Player} this instance is dedicated to.
     */
    public Bank(Player player) {
        super(SIZE, StackPolicy.ALWAYS);
        this.player = player;

        addListener(new BankListener(player));
    }

    /**
     * Opens the banking interface for the underlying player.
     */
    public void open() {
        shift();

        AttributeValue<Boolean> value = player.attr().get("bank_withdraw_note");
        value.set(false);

        player.queue(new InventoryOverlayMessageWriter(INTERFACE_ID, INVENTORY_OVERLAY_ID));
        player.queue(new StateMessageWriter(WITHDRAW_MODE_STATE_ID, 0));

        forceRefresh();
    }

    /**
     * Deposits an {@link Item} from the underlying player's {@link Inventory}.
     *
     * @param inventoryIndex The {@code Inventory} index that the {@code Item} will be deposited from.
     * @param amount The amount of the {@code Item} to deposit.
     * @return {@code true} if the {@code Item} was successfully deposited, {@code false} otherwise.
     */
    public boolean deposit(int inventoryIndex, int amount) {
        Inventory inventory = player.getInventory();
        Item depositItem = inventory.get(inventoryIndex);

        if (depositItem == null) { // Item doesn't exist in inventory.
            return false;
        }

        int existingAmount = inventory.computeAmountForId(depositItem.getId());
        if (amount > existingAmount) {
            amount = existingAmount;
        }

        int remaining = computeRemainingSize();
        if (remaining < 1) {
            fireCapacityExceededEvent();
            return false;
        }

        if (amount > remaining) {
            amount = remaining;
        }

        depositItem = depositItem.setAmount(amount);

        ItemDefinition def = depositItem.getDefinition();
        int newId = (def.getUnnotedId() != -1) ? def.getUnnotedId() : depositItem.getId();

        if (add(depositItem.setId(newId))) {
            inventory.remove(depositItem);
            forceRefresh();
            return true;
        }
        return false;
    }

    /**
     * Withdraws an {@link Item} from the underlying player's {@code Bank}.
     *
     * @param bankIndex The {@code Bank} index that the {@code Item} will be deposited from.
     * @param amount The amount of the {@code Item} to withdraw.
     * @return {@code true} if the {@code Item} was successfully deposited, {@code false} otherwise.
     */
    public boolean withdraw(int bankIndex, int amount) {
        Inventory inventory = player.getInventory();
        Item withdrawItem = get(bankIndex);

        if (withdrawItem == null) { // Item doesn't exist in bank.
            return false;
        }

        int existingAmount = computeAmountForId(withdrawItem.getId());
        if (withdrawItem.getAmount() > existingAmount) {
            amount = existingAmount;
        }

        int remaining = inventory.computeRemainingSize();
        if (remaining < 1) {
            inventory.fireCapacityExceededEvent();
            return false;
        }

        if (amount > remaining) {
            amount = remaining;
        }
        withdrawItem = withdrawItem.setAmount(amount);

        AttributeValue<Boolean> value = player.attr().get("bank_withdraw_note");
        int newId = -1;
        if (value.get()) {
            ItemDefinition def = withdrawItem.getDefinition();
            newId = def.getNotedId() != -1 ? def.getNotedId() : withdrawItem.getId();

            if (newId == withdrawItem.getId()) {
                player.queue(new InfoMessageWriter("This item cannot be withdrawn as a note."));
            }
        }

        if (remove(withdrawItem)) {
            inventory.add(withdrawItem.setId(newId));
            forceRefresh();
            return true;
        }
        return false;
    }

    /**
     * Forces a refresh of {@code Bank} items to the {@code BANK_DISPLAY_ID} widget and {@link Inventory} items to the {@code
     * INVENTORY_DISPLAY_ID} widget.
     */
    private void forceRefresh() {
        Inventory inventory = player.getInventory();
        player.queue(constructRefresh(BANK_DISPLAY_ID));
        player.queue(inventory.constructRefresh(INVENTORY_DISPLAY_ID));
    }
}
