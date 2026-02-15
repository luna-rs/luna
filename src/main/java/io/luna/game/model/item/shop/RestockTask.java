package io.luna.game.model.item.shop;

import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.task.Task;

import java.util.Iterator;

/**
 * A {@link Task} responsible for gradually restocking a shop's inventory according to its {@link RestockPolicy}.
 * <p>
 * The task processes a set of slot indexes that require restocking. For each slot:
 * <ul>
 *     <li>Restocking will only occur if the slot is restockable (has an original amount).</li>
 *     <li>The current amount is increased by {@link RestockPolicy#getAmount()} each cycle.</li>
 *     <li>The amount is clamped to the original amount so stock never exceeds its baseline.</li>
 * </ul>
 * <p>
 * When no more items require restocking, the task cancels itself.
 *
 * @author lare96
 */
public final class RestockTask extends Task {

    /**
     * The shop being restocked.
     */
    private final Shop shop;

    /**
     * The restock policy to apply during execution.
     */
    private final RestockPolicy restockPolicy;

    /**
     * Creates a new {@link RestockTask} for a given {@link Shop}.
     *
     * @param shop The shop to restock.
     */
    public RestockTask(Shop shop) {
        super(false, 1);
        this.shop = shop;
        restockPolicy = shop.getRestockPolicy();
    }

    @Override
    protected boolean onSchedule() {
        if (restockPolicy != null) {
            setDelay(restockPolicy.getRate());
            return true;
        }
        return false;
    }

    @Override
    protected void execute() {
        ItemContainer items = shop.getItems();
        Iterator<Integer> restockIterator = shop.getRestockItems().iterator();

        // Loop through indexes that need restocking.
        while (restockIterator.hasNext()) {
            int restockIndex = restockIterator.next();
            Item restockItem = items.get(restockIndex);
            int initialAmount = shop.getAmountMap()[restockIndex].orElse(-1);

            if (initialAmount == -1 || restockItem.getAmount() >= initialAmount) {
                // Item on slot is >= to its original amount, no longer requires restocking.
                restockIterator.remove();
                continue;
            }

            // Otherwise restock items according to the policy, up to a max of the original amount.
            int newAmount = Math.min(restockPolicy.getAmount() + restockItem.getAmount(), initialAmount);
            items.set(restockIndex, restockItem.withAmount(newAmount));
        }

        if (shop.getRestockItems().isEmpty()) {
            // No more items to restock, cancel the task.
            cancel();
        }
    }
}
