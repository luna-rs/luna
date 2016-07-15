package io.luna.game.model.item;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mobile.Player;
import io.luna.net.msg.out.UpdateWeightMessageWriter;

import java.util.Objects;
import java.util.Optional;

/**
 * An {@link ItemContainerListener} implementation that will update the weight value of a {@link Player}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemWeightListener implements ItemContainerListener {

    /**
     * The {@link Player} to listen for.
     */
    private final Player player;

    /**
     * Creates a new {@link ItemWeightListener}.
     *
     * @param player The {@link Player} to listen for.
     */
    public ItemWeightListener(Player player) {
        this.player = player;
    }

    @Override
    public void itemUpdated(ItemContainer container, Optional<Item> oldItem, Optional<Item> newItem, int index) {
        updateWeight(oldItem, newItem);
        queueWeight();
    }

    @Override
    public void bulkItemsUpdated(ItemContainer container) {
        updateAllWeight();
        queueWeight();
    }

    /**
     * Updates the weight value for a single set of items.
     */
    private void updateWeight(Optional<Item> oldItem, Optional<Item> newItem) {
        double subtract = applyWeight(oldItem);
        double add = applyWeight(newItem);

        double currentWeight = player.getWeight();
        currentWeight -= subtract;
        currentWeight += add;

        player.setWeight(currentWeight, false);
    }

    /**
     * Updates the weight value for all items in {@code container}.
     */
    private void updateAllWeight() {
        player.setWeight(0.0, false);

        player.getInventory().stream().
            filter(Objects::nonNull).
            forEach(it -> updateWeight(Optional.empty(), Optional.of(it)));
        player.getEquipment().stream().
            filter(Objects::nonNull).
            forEach(it -> updateWeight(Optional.empty(), Optional.of(it)));
    }

    /**
     * Converts an {@link Optional} into a {@code double} describing its weight value.
     */
    private double applyWeight(Optional<Item> item) {
        return item.map(Item::getItemDef).
            map(ItemDefinition::getWeight).
            orElse(0.0);
    }

    /**
     * Queues an {@link UpdateWeightMessageWriter} message.
     */
    private void queueWeight() {
        player.queue(new UpdateWeightMessageWriter((int) player.getWeight()));
    }
}
