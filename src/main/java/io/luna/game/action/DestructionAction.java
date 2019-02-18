package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;

/**
 * A {@link FixedAction} implementation where items are removed from the inventory.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class DestructionAction extends FixedAction<Player> {

    /**
     * The items being removed.
     */
    protected Item[] currentRemove;

    /**
     * Creates a new {@link DestructionAction}.
     *
     * @param player The {@link Player} assigned to this action.
     * @param instant If this action executes instantly.
     * @param delay The delay of this action.
     * @param times The amount of times to destruct.
     */
    public DestructionAction(Player player, boolean instant, int delay, int times) {
        super(player, instant, delay, times);
    }

    @Override
    protected final boolean canExecute() {
        return canDestruct();
    }

    @Override
    public final void execute() {
        Inventory inventory = mob.getInventory();
        currentRemove = remove();

        if (inventory.containsAll(currentRemove)) {
            inventory.removeAll(currentRemove);
            onDestruct();
        } else {
            interrupt();
        }
    }

    /**
     * An attempt will be made to remove these {@link Item}s every {@code delay}.
     *
     * @return The items that will be removed from the inventory.
     */
    protected abstract Item[] remove();

    /**
     * Function invoked at the beginning of every action loop.
     *
     * @return {@code false} to interrupt the action.
     */
    protected boolean canDestruct() {
        return true;
    }

    /**
     * Function invoked when all items are removed from the inventory.
     */
    protected void onDestruct() {

    }
}