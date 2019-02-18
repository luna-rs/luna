package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.util.Rational;
import io.netty.util.internal.ThreadLocalRandom;

/**
 * A {@link FixedAction} implementation that uses an algorithm to determine if items should be removed from and/or added to
 * the inventory every tick.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class HarvestingAction extends FixedAction<Player> {

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
     * @param player The player.
     * @param times The amount of times to harvest.
     */
    public HarvestingAction(Player player, int times) {
        super(player, false, 1, times);
    }

    @Override
    protected final boolean canExecute() {
        switch (getState()) {
            case IDLE:
                // The action hasn't been started.
                return canHarvest();
            case RUNNING:
                // The action was started, incorporate the harvest chance algorithm.
                if (canHarvest()) {
                    double harvestChance = harvestChance().doubleValue();
                    double currentRoll = ThreadLocalRandom.current().nextDouble();
                    return harvestChance >= currentRoll;
                }
                return false;
            default:
                throw new IllegalStateException("Task cannot be CANCELLED at this point.");
        }
    }

    @Override
    public final void execute() {
        Inventory inventory = mob.getInventory();

        currentRemove = remove();
        if (!inventory.containsAll(currentRemove)) {
            interrupt();
            return;
        }

        currentAdd = add();
        if (!inventory.hasSpaceForAll(currentAdd)) {
            mob.sendMessage("You do not have enough space in your inventory.");
            interrupt();
            return;
        }

        inventory.removeAll(currentRemove);
        inventory.addAll(currentAdd);
        onHarvest();
    }

    /**
     * @return The chance of harvesting items.
     */
    protected abstract Rational harvestChance();

    /**
     * @return The items that will be added.
     */
    protected abstract Item[] add();

    /**
     * Function invoked at the beginning of every action loop.
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
}
