package io.luna.game.model.mob.inter;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.item.RefreshListener;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.attr.AttributeValue;
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter;
import io.luna.net.msg.out.WidgetItemsMessageWriter;
import io.luna.util.LazyVal;

import java.util.List;

/**
 * An {@link InventoryOverlayInterface} implementation representing the offer trading screen.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class OfferTradeInterface extends InventoryOverlayInterface {

    /**
     * A {@link RefreshListener} that listens for items on the offer screen.
     */
    private final class OfferListener extends RefreshListener {

        /**
         * The player.
         */
        private final Player player;

        /**
         * Creates a new {@link Player}.
         *
         * @param player The player.
         */
        public OfferListener(Player player) {
            this.player = player;
        }

        @Override
        public void displayUpdate(ItemContainer items, List<IndexedItem> updateItems,
                                  WidgetIndexedItemsMessageWriter msg) {
            player.queue(msg); // Send to left panel.
            tradingWith.queue(new WidgetIndexedItemsMessageWriter(3416, updateItems)); // Send to right panel.
        }
    }

    /**
     * The player being traded with.
     */
    private final Player tradingWith;

    /**
     * The items being offered.
     */
    private final ItemContainer tradeItems = new ItemContainer(28, ItemContainer.StackPolicy.STANDARD, 3415);

    /**
     * The "trading_with" attribute.
     */
    private AttributeValue<Integer> tradingAttr;

    /**
     * The trading player's offer instance.
     */
    private LazyVal<OfferTradeInterface> otherOffer;

    /**
     * If the "Accept" button has been clicked.
     */
    private boolean accepted;

    /**
     * Creates a new {@link OfferTradeInterface}.
     *
     * @param tradingWith The player being traded with.
     */
    public OfferTradeInterface(Player tradingWith) {
        super(3323, 3321);
        this.tradingWith = tradingWith;
        otherOffer = new LazyVal<>(() -> tradingWith.getInterfaces().
                standardTo(OfferTradeInterface.class).
                orElseThrow(IllegalStateException::new));
    }

    @Override
    public void onOpen(Player player) {
        // Initialize variables.
        tradingAttr = player.getAttributes().get("trading_with");

        // Send text to trading interface.
        sendTradingWith(player);
        player.sendText("", 3431);
        player.sendText("Are you sure you want to make this trade?", 3535);

        // Refresh inventory to trade inventory.
        Inventory inventory = player.getInventory();
        inventory.setSecondaryRefresh(3322);
        inventory.refreshSecondary(player);

        // Clear left and right trade panels.
        WidgetItemsMessageWriter clearLeftMsg = new WidgetItemsMessageWriter(3415, ImmutableList.of());
        WidgetItemsMessageWriter clearRightMsg = new WidgetItemsMessageWriter(3416, ImmutableList.of());
        player.queue(clearLeftMsg);
        player.queue(clearRightMsg);

        // Set listeners.
        tradeItems.setListeners(new OfferListener(player));
    }

    @Override
    public void onClose(Player player) {
        // Trade was declined.
        tradingAttr.set(-1);
        player.getInventory().resetSecondaryRefresh();
        player.getInventory().addAll(tradeItems);

        tradingWith.getInterfaces().close();
    }

    @Override
    public void onReplace(Player player, AbstractInterface replace) {
        // If replacing interface isn't the confirm screen, decline.
        if (replace.getClass() != ConfirmTradeInterface.class) {
            onClose(player);
        }
    }

    /**
     * Adds an item to the offer screen.
     *
     * @param player The player.
     * @param index The inventory index of the item.
     * @param amount The amount to add.
     */
    public void add(Player player, int index, int amount) {
        Inventory inventory = player.getInventory();
        Item inventoryItem = inventory.get(index);

        // Item doesn't exist.
        if (inventoryItem == null) {
            return;
        }

        // Item can't be traded.
        if (!inventoryItem.getItemDef().isTradeable()) {
            player.sendMessage("This item cannot be traded.");
            return;
        }

        // Modify amount if there's less than requested.
        int hasAmount = inventoryItem.getItemDef().isStackable() ?
                inventoryItem.getAmount() :
                inventory.computeAmountForId(inventoryItem.getId());
        if (amount == -1 || amount > hasAmount) {
            amount = hasAmount;
        }

        // Add to offer screen.
        Item tradeItem = inventoryItem.withAmount(amount);
        if (inventory.remove(index, tradeItem)) {
            tradeItems.add(tradeItem);
            otherOffer.get().sendTradingWith(tradingWith);
            resetAccept(player);
        }
    }

    /**
     * Removes an item from the offer screen.
     *
     * @param player The player.
     * @param index The offer screen index of the item.
     * @param amount The amount to remove.
     */
    public void remove(Player player, int index, int amount) {
        Item tradeItem = tradeItems.get(index);

        // Item doesn't exist.
        if (tradeItem == null) {
            return;
        }

        // Modify amount if there's less than requested.
        int hasAmount = tradeItem.getItemDef().isStackable() ?
                tradeItem.getAmount() :
                tradeItems.computeAmountForId(tradeItem.getId());
        if (amount == -1 || amount > hasAmount) {
            amount = hasAmount;
        }

        // Remove from offer screen.
        Item removeItem = tradeItem.withAmount(amount);
        if (tradeItems.remove(removeItem)) {
            player.getInventory().add(removeItem);
            otherOffer.get().sendTradingWith(tradingWith);
            resetAccept(player);
        }
    }

    /**
     * Invoked when {@code player} clicks the "Accept" button on the offer screen.
     *
     * @param player The player.
     */
    public void accept(Player player) {
        if (accepted) {
            return;
        }

        // Trading player doesn't have enough inventory space.
        if (tradingWith.getInventory().computeRemainingSize() < tradeItems.size()) {
            player.sendMessage(tradingWith.getUsername() + " does not have enough inventory space for your items.");
            return;
        }

        if (otherOffer.get().accepted) {
            // Both players have accepted, open confirmation screen.
            ConfirmTradeInterface confirm = new ConfirmTradeInterface(this);
            ConfirmTradeInterface otherConfirm = new ConfirmTradeInterface(otherOffer.get());
            player.getInterfaces().open(confirm);
            tradingWith.getInterfaces().open(otherConfirm);
        } else {
            // Accept trade, wait for other player to accept.
            accepted = true;
            player.sendText("Waiting for other player...", 3431);
            tradingWith.sendText("Other player has accepted", 3431);
        }
    }

    /**
     * Displays the trading Player's name and remaining free space.
     *
     * @param player The player.
     */
    private void sendTradingWith(Player player) {
        String username = tradingWith.getUsername();
        StringBuilder sb = new StringBuilder(username.length() + 5);

        // Append username and crown.
        sb.append(username);
        switch (tradingWith.getRights()) {
            case MODERATOR:
                sb.append("@cr1@");
            case DEVELOPER:
            case ADMINISTRATOR:
                sb.append("@cr2@");
        }

        // Send widget text to player.
        int remaining = tradingWith.getInventory().computeRemainingSize();
        player.sendText("Trading with: " + sb + " who has @gre@" + remaining + " free slots", 3417);
    }

    /**
     * Resets the accept button click for {@code player} and the trading Player.
     *
     * @param player The player.
     */
    private void resetAccept(Player player) {
        accepted = false;
        otherOffer.get().accepted = false;
        player.sendText("", 3431);
        tradingWith.sendText("", 3431);
    }

    /**
     * @return The items being offered.
     */
    ItemContainer getTradeItems() {
        return tradeItems;
    }

    /**
     * @return The player being traded with.
     */
    Player getTradingWith() {
        return tradingWith;
    }
}