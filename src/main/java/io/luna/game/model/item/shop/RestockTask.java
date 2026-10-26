package io.luna.game.model.item.shop;

import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.task.Task;

import java.util.Iterator;
import java.util.OptionalInt;

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
 * Under a {@link RestockPolicy#isBalanced() balanced} policy the task instead runs every tick, and each slot moves one
 * unit back toward its original amount whenever the world tick lines up with that slot's restock rate. Slots the shop
 * doesn't normally stock lose one unit every {@link RestockPolicy#UNSTOCKED_DECAY_RATE} ticks until they're cleared.
 * Lining the rates up with the world tick keeps every shop on the same cycle, like the 377 era.
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
            setDelay(restockPolicy.isBalanced() ? 1 : restockPolicy.getRate());
            return true;
        }
        return false;
    }

    @Override
    protected void execute() {
        if (restockPolicy.isBalanced()) {
            balance();
        } else {
            restock();
        }

        if (shop.getRestockItems().isEmpty()) {
            // No more items to restock, cancel the task.
            cancel();
        }
    }

    /**
     * Restocks items below their original amount, by {@link RestockPolicy#getAmount()} each cycle.
     */
    private void restock() {
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
    }

    /**
     * Moves every out of balance slot one unit toward its original amount, if this tick lines up with the slot's rate.
     */
    private void balance() {
        ItemContainer items = shop.getItems();
        long tick = shop.getWorld().getCurrentTick();
        Iterator<Integer> balanceIterator = shop.getRestockItems().iterator();

        while (balanceIterator.hasNext()) {
            int index = balanceIterator.next();
            Item item = items.get(index);
            OptionalInt originalAmount = shop.getAmountMap()[index];

            if (originalAmount.isEmpty()) {
                // Not normally stocked here, so it's cleared out over time.
                if (item == null) {
                    balanceIterator.remove();
                } else if (tick % RestockPolicy.UNSTOCKED_DECAY_RATE == 0) {
                    items.set(index, item.getAmount() > 1 ? item.withAmount(item.getAmount() - 1) : null);
                    if (items.get(index) == null) {
                        balanceIterator.remove();
                    }
                }
                continue;
            }

            if (item == null) {
                // Stocked slots keep their item at zero, so there's nothing to balance here.
                balanceIterator.remove();
                continue;
            }

            int amount = item.getAmount();
            int target = originalAmount.getAsInt();
            if (amount != target && tick % shop.computeRestockRate(index) == 0) {
                amount += amount < target ? 1 : -1;
                items.set(index, item.withAmount(amount));
            }
            if (amount == target) {
                balanceIterator.remove();
            }
        }
    }
}
