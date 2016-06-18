package io.luna.game.model.item;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mobile.Player;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.InventoryOverlayMessageWriter;

import java.util.OptionalInt;

/**
 * An {@link ItemContainer} implementation that manages the bank for a {@link Player}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class Bank extends ItemContainer {

    /**
     * An {@link ItemContainerAdapter} implementation that listens for changes to the bank.
     */
    private final class BankListener extends ItemContainerAdapter {

        /**
         * Creates a new {@link BankListener}.
         */
        public BankListener() {
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
    public static final int BANK_DISPLAY_ID = 5382;

    /**
     * The inventory item display widget identifier.
     */
    private static final int INVENTORY_DISPLAY_ID = 5064;

    /**
     * The withdraw mode state identifier.
     */
    public static final int WITHDRAW_MODE_STATE_ID = 115;

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

        addListener(new BankListener());
    }

    /**
     * Opens the banking interface for the underlying player.
     */
    public void open() {
        shift();

        player.queue(new InventoryOverlayMessageWriter(INTERFACE_ID, INVENTORY_OVERLAY_ID));
        player.setWithdrawAsNote(false);

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

        if (depositItem == null || amount < 1) { // Item doesn't exist in inventory.
            return false;
        }

        int existingAmount = inventory.computeAmountForId(depositItem.getId());
        if (amount > existingAmount) { // Deposit amount is more than we actually have, size it down.
            amount = existingAmount;
        }
        depositItem = depositItem.createWithAmount(amount);

        ItemDefinition def = depositItem.getItemDef();
        Item newDepositItem = depositItem.createWithId(def.isNoted() ? def.getUnnotedId() : depositItem.getId());

        int remaining = computeRemainingSize(); // Do we have enough space in the bank?
        if (remaining < 1 && computeIndexForId(newDepositItem.getId()) == -1) {
            fireCapacityExceededEvent();
            return false;
        }

        if (inventory.remove(depositItem)) {
            add(newDepositItem);
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

        if (withdrawItem == null || amount < 1) { // Item doesn't exist in bank.
            return false;
        }

        int existingAmount = withdrawItem.getAmount();
        if (amount > existingAmount) { // Withdraw amount is more than we actually have, size it down.
            amount = existingAmount;
        }

        OptionalInt newId = OptionalInt.empty();
        if (player.isWithdrawAsNote()) { // Configure the noted id of the item we're withdrawing, if applicable.
            ItemDefinition def = withdrawItem.getItemDef();
            if (def.canBeNoted()) {
                newId = OptionalInt.of(def.getNotedId());
            } else {
                player.queue(new GameChatboxMessageWriter("This item cannot be withdrawn as a note."));
            }
        }

        Item newWithdrawItem = withdrawItem.createWithId(newId.orElse(withdrawItem.getId()));
        ItemDefinition newDef = newWithdrawItem.getItemDef();

        int remaining = inventory.computeRemainingSize();
        if (remaining < 1) { // Do we have enough space in the inventory?
            inventory.fireCapacityExceededEvent();
            return false;
        }

        if (amount > remaining && !newDef.isStackable()) { // Size down withdraw amount to inventory space.
            amount = remaining;
        }
        withdrawItem = withdrawItem.createWithAmount(amount);
        newWithdrawItem = newWithdrawItem.createWithAmount(amount);

        if (remove(withdrawItem)) {
            inventory.add(newWithdrawItem);
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
