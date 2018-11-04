package io.luna.game.model.item.shop;

import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.task.Task;

/**
 * A {@link Task} implementation that will restock shop items.
 *
 * @author lare96 <http://github.com/lare96>
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
            setDelay(restockPolicy.getTickRate());
            return true;
        }
        return false;
    }

    @Override
    protected void execute() {
        // TODO Find a way to do this without looping through entire shop (Maybe use a bitset?)
        ItemContainer container = shop.getContainer();
        boolean cancelTask = true;
        for (int index = 0; index < container.capacity(); index++) {
            Item item = container.get(index);
            if (item != null && restock(index, item)) {
                // We had to restock an item, so don't cancel.
                cancelTask = false;
            }
        }

        if (cancelTask) {
            // No more items to restock.
            cancel();
        }
    }

    /**
     * Restocks the single {@code item} at {@code index}.
     *
     * @param index The index to restock.
     * @param item The item to restock.
     * @return {@code true} if a restock was performed.
     */
    private boolean restock(int index, Item item) {
        int initialAmount = shop.getAmountMap()[index].orElse(-1);
        if (item.getAmount() < initialAmount) {
            // Increase by restock amount, to a maximum of the initial amount.
            int newAmount = Math.min(restockPolicy.getStockAmount() + item.getAmount(), initialAmount);
            shop.getContainer().set(index, item.withAmount(newAmount));
            return true;
        }
        return false;
    }
}