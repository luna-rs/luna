package io.luna.game.model.item.shop;

import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.task.Task;

import java.util.Iterator;

/**
 * A {@link Task} implementation that will restock shop items.
 *
 * @author lare96
 */
public final class RestockTask extends Task {

    /**
     * The shop to restock items for.
     */
    private final Shop shop;

    /**
     * The restock policy.
     */
    private final RestockPolicy restockPolicy;

    /**
     * Creates a new {@link RestockTask}.
     *
     * @param shop The shop to restock items for.
     */
    public RestockTask(Shop shop) {
        super(false, 1);
        this.shop = shop;
        restockPolicy = shop.getRestockPolicy();
    }

    @Override
    protected boolean onSchedule() {
        if (restockPolicy != RestockPolicy.DISABLED) {
            setDelay(restockPolicy.getRate());
            return true;
        }
        return false;
    }

    @Override
    protected void execute() {
        ItemContainer items = shop.getContainer();
        Iterator<Integer> restockIterator = shop.getNeedsRestock().iterator();

        // Loop through all indexes that need restocking.
        while (restockIterator.hasNext()) {
            int restockIndex = restockIterator.next();
            Item restockItem = items.get(restockIndex);
            int initialAmount = shop.getAmountMap()[restockIndex].orElse(-1);

            // The item is not restockable, or has been restocked. Remove it.
            if(initialAmount == -1 || restockItem.getAmount() >= initialAmount) {
                restockIterator.remove();
                continue;
            }

            // Increase by restock amount, to a maximum of the initial amount.
            int newAmount = Math.min(restockPolicy.getAmount() + restockItem.getAmount(), initialAmount);
            items.set(restockIndex, restockItem.withAmount(newAmount));
        }

        // No more items to restock.
        if (shop.getNeedsRestock().isEmpty()) {
            cancel();
        }
    }
}