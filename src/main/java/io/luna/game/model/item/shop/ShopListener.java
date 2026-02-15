package io.luna.game.model.item.shop;

import io.luna.game.model.item.IndexedItem;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.item.RefreshListener;
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter;

import java.util.List;
import java.util.OptionalInt;

/**
 * A {@link RefreshListener} implementation responsible for synchronizing shop state with its visual representation
 * and managing restock behavior.
 *
 * @author lare96
 */
public final class ShopListener extends RefreshListener {

    /**
     * The shop instance this listener is bound to.
     */
    private final Shop shop;

    /**
     * Creates a new {@link ShopListener} bound to the specified {@link Shop}.
     *
     * @param shop The shop associated with this listener.
     */
    public ShopListener(Shop shop) {
        this.shop = shop;
    }

    @Override
    public void onInit(ItemContainer items) {
        // Initialize original shop item amounts.
        OptionalInt[] amountMap = shop.getAmountMap();
        for (int index = 0; index < items.capacity(); index++) {
            int amount = items.computeAmountForIndex(index);
            if (amount == 0) {
                amountMap[index] = OptionalInt.empty();
            } else {
                amountMap[index] = OptionalInt.of(amount);
            }
        }
    }

    @Override
    public void displayUpdate(ItemContainer items, List<IndexedItem> updateItems,
                              WidgetIndexedItemsMessageWriter msg) {

        // Determine if restocking is needed based on updated items.
        for (IndexedItem item : updateItems) {
            if (item == null) {
                continue;
            }

            int itemIndex = item.getIndex();
            double originalAmount = shop.getAmountMap()[itemIndex].orElse(-1);

            // Item is not restockable.
            if (originalAmount <= 0) {
                continue;
            }

            int currentAmount = item.getAmount();
            boolean aggressive = shop.getRestockPolicy().isAggressive();
            if ((currentAmount != originalAmount && aggressive) || (currentAmount == 0 && !aggressive)) {
                shop.getRestockItems().add(itemIndex);
            }
        }

        // Trigger restocking if at least one item meets the threshold.
        if (!shop.getRestockItems().isEmpty()) {
            shop.restockItems();
        }

        // Queue update messages for all players currently viewing the shop.
        shop.getViewing().forEach(player -> player.queue(msg));
    }

    @Override
    public void onCapacityExceeded(ItemContainer items) {
        throw new IllegalStateException("Shop '" + shop.getName() + "' item capacity exceeded.");
    }
}
