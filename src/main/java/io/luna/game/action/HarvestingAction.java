package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.util.Rational;
import io.netty.util.internal.ThreadLocalRandom;

/**
 * A {@link StationaryAction} implementation that uses an algorithm to determine if items should be removed from
 * and/or added to the inventory every tick.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class HarvestingAction extends StationaryAction<Player> {

    /**
     * An empty array of items.
     */
    private static final Item[] NO_ITEMS = {};

    /**
     * The items being added.
     */
    protected Item[] currentAdd;

    /**
     * The items being removed.
     */
    protected Item[] currentRemove;

    /**
     * Creates a new {@link HarvestingAction}.
     *
     * @param player The {@link Player} assigned to this action.
     */
    public HarvestingAction(Player player) {
        super(player, false, 1);
    }

    @Override
    public final void call() {
        if (!canHarvest()) {
            interrupt();
            return;
        }

        double harvestChance = harvestChance().doubleValue();
        double currentRoll = ThreadLocalRandom.current().nextDouble();
        if (harvestChance >= currentRoll) {
            Inventory inventory = mob.getInventory();

            currentRemove = remove();
            currentAdd = add();

            if (!inventory.containsAll(currentRemove)) {
                interrupt();
                return;
            }

            int newSlots = inventory.computeSize(currentAdd);
            int oldSlots = inventory.computeSize(currentRemove);
            if ((newSlots - oldSlots) > inventory.computeRemainingSize()) {
                mob.sendMessage("You do not have enough space in your inventory.");
                interrupt();
                return;
            }

            inventory.removeAll(currentRemove);
            inventory.addAll(currentAdd);
            onHarvest();
        }
    }

    /**
     * Function invoked at the beginning of every action loop.
     *
     * @return {@code false} if the action should be interrupted.
     */
    protected boolean canHarvest() {
        return true;
    }

    /**
     * Function invoked when items are harvested.
     */
    protected void onHarvest() {

    }

    /**
     * @return The items that will be removed.
     */
    protected Item[] remove() {
        return NO_ITEMS;
    }

    /**
     * @return The chance of harvesting items.
     */
    protected abstract Rational harvestChance();

    /**
     * @return The items that will be added.
     */
    protected abstract Item[] add();
}
