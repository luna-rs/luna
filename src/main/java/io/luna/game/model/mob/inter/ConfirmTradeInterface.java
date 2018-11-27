package io.luna.game.model.mob.inter;

import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;
import io.luna.util.LazyVal;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * An {@link InventoryOverlayInterface} implementation representing the confirmation trading screen.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ConfirmTradeInterface extends InventoryOverlayInterface {

    /**
     * The number formatter.
     */
    private static final NumberFormat FORMATTER = NumberFormat.getInstance(Locale.UK);

    /**
     * The offer instance.
     */
    private final OfferTradeInterface offerInterface;

    /**
     * The player being traded with.
     */
    private final Player tradingWith;

    /**
     * The items being traded.
     */
    private final ItemContainer tradingItems;

    /**
     * The trading player's confirm instance.
     */
    private LazyVal<ConfirmTradeInterface> otherConfirm;

    /**
     * If the "Accept" button has been clicked.
     */
    private boolean accepted;

    /**
     * If the trade has been completed.
     */
    private boolean completed;

    /**
     * Creates a new {@link ConfirmTradeInterface}.
     *
     * @param offerInterface The offer instance.
     */
    public ConfirmTradeInterface(OfferTradeInterface offerInterface) {
        super(3443, 3213);
        this.offerInterface = offerInterface;
        tradingWith = offerInterface.getTradingWith();
        tradingItems = offerInterface.getTradeItems();
        otherConfirm = new LazyVal<>(() -> tradingWith.getInterfaces().
                standardTo(ConfirmTradeInterface.class).
                orElseThrow(IllegalStateException::new));
    }

    @Override
    public void onOpen(Player player) {
        // Display item names on confirmation screens.
        String myNames = computeItemsString();
        player.sendText(myNames, 3557);
        tradingWith.sendText(myNames, 3558);
    }

    @Override
    public void onClose(Player player) {
        if (!completed) {
            // Interface closed when trade wasn't finished yet, decline.
            offerInterface.onClose(player);
        } else {
            // Trade completed successfully!
            player.resetTradingWith();
        }
    }

    /**
     * Invoked when {@code player} clicks the "Accept" button on the confirmation screen.
     *
     * @param player The player.
     */
    public void accept(Player player) {
        if (accepted) {
            return;
        }

        if (otherConfirm.get().accepted) {
            // Both players have accepted, complete trade normally.
            completed = true;
            player.getInventory().addAll(otherConfirm.get().tradingItems);
            player.getInterfaces().close();

            otherConfirm.get().completed = true;
            tradingWith.getInventory().addAll(tradingItems);
            tradingWith.getInterfaces().close();
        } else {
            // Accept trade, and wait for other player to accept.
            accepted = true;
            player.sendText("Waiting for other player...", 3535);
            tradingWith.sendText("Other player has accepted.", 3535);
        }
    }

    /**
     * Returns the String of items that will be displayed on confirmation screens.
     *
     * @return The String of items.
     */
    private String computeItemsString() {
        int size = tradingItems.size();

        // Size is 0, lookup not needed.
        if (size == 0) {
            return "Absolutely nothing!";
        }

        // Otherwise, iterate through items.
        StringBuilder names = new StringBuilder(size * 16);
        for (int index = 0; index < tradingItems.capacity(); index++) {
            Item item = tradingItems.get(index);
            if (item == null) {
                continue;
            }


            if (index > 0) {
                // Newline if we're not on the first item.
                names.append("\\n");
            }

            // Append name and amount to the builder.
            names.append(item.getItemDef().getName());

            if (item.getItemDef().isStackable()) {
                String amount = computeAmountString(item.getAmount());
                names.append(' ').
                        append('x').
                        append(' ').
                        append(amount);
            }
        }
        return names.toString();
    }

    /**
     * Returns {@code amount} as a String that will be displayed alongside item names.
     *
     * @return The amount as a String.
     */
    private String computeAmountString(int amount) {
        StringBuilder sb = new StringBuilder(amount < 1_000 ? 6 : 36);
        if (amount >= 1_000 && amount < 1_000_000) {
            // Format above 1k and below 1m.
            return sb.append("@cya@").
                    append(amount / 1_000).
                    append("K @whi@").
                    append('(').
                    append(FORMATTER.format(amount)).
                    append(')').toString();
        } else if (amount >= 1_000_000) {
            // Format above 1m.
            return sb.append("@gre@").
                    append(amount / 1_000_000).
                    append(' ').
                    append("million @whi@").
                    append('(').
                    append(FORMATTER.format(amount)).
                    append(')').toString();
        }
        // Below 1k.
        return sb.append(amount).toString();
    }
}