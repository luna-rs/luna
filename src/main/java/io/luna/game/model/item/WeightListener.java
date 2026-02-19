package io.luna.game.model.item;

import io.luna.game.model.mob.Player;

/**
 * An {@link ItemContainerListener} that maintains a {@link Player}'s carried weight as items are added, removed, or
 * replaced in an {@link ItemContainer}.
 * <p>
 * Weight updates are applied in two modes:
 * <ul>
 *     <li><b>Single update</b>: weight is updated immediately for the one changed slot.</li>
 *     <li><b>Bulk update</b>: weight deltas are accumulated and applied once when the bulk operation completes.</li>
 * </ul>
 * <p>
 * This approach avoids repeatedly calling {@link Player#setWeight(double, boolean)} during bulk inventory operations
 * while still keeping the final weight accurate.
 *
 * @author lare96
 */
public final class WeightListener implements ItemContainerListener {

    /**
     * The player whose weight is being tracked.
     */
    private final Player player;

    /**
     * Accumulated weight deltas recorded during a bulk update operation.
     */
    private double weightChange;

    /**
     * Creates a new {@link WeightListener}.
     *
     * @param player The player whose weight should be updated.
     */
    public WeightListener(Player player) {
        this.player = player;
    }

    @Override
    public void onSingleUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
        updateWeight(oldItem, newItem);
    }

    @Override
    public void onBulkUpdate(int index, ItemContainer items, Item oldItem, Item newItem) {
        weightChange += computeWeightDifference(oldItem, newItem);
    }

    @Override
    public void onBulkUpdateCompleted(ItemContainer items) {
        player.setWeight(player.getWeight() + weightChange, true);
        weightChange = 0.0;
    }

    /**
     * Applies the weight delta for a single change immediately.
     *
     * @param oldItem The previous item value.
     * @param newItem The new item value.
     */
    private void updateWeight(Item oldItem, Item newItem) {
        player.setWeight(player.getWeight() + computeWeightDifference(oldItem, newItem), true);
    }

    /**
     * Computes the weight delta caused by changing from {@code oldItem} to {@code newItem}.
     *
     * @param oldItem The previous item value.
     * @param newItem The new item value.
     * @return The net weight change (new weight minus old weight).
     */
    private double computeWeightDifference(Item oldItem, Item newItem) {
        double subtract = computeWeight(oldItem);
        double add = computeWeight(newItem);
        return add - subtract;
    }

    /**
     * Computes the weight contribution for the given optional item.
     * <p>
     * If the item is absent, {@code 0.0} is returned.
     *
     * @param item The item to compute for.
     * @return The item's weight, or {@code 0.0} if absent.
     */
    private double computeWeight(Item item) {
        return item != null ? item.getItemDef().getWeight() : 0.0;
    }
}
