package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;

/**
 * A {@link PlayerAction} implementation where items are removed from the inventory.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class DestructionAction extends PlayerAction {

    /**
     * The items being removed.
     */
    protected Item[] currentRemove;

    /**
     * Creates a new {@link DestructionAction}.
     */
    public DestructionAction(Player player, boolean instant, int delay) {
        super(player, instant, delay);
    }

    @Override
    public final void call() {
        if (!canDestruct()) {
            interrupt();
            return;
        }

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
     * Function invoked at the beginning of every action loop. Return {@code false} to interrupt the action.
     */
    protected boolean canDestruct() {
        return true;
    }

    /**
     * Function invoked when all items are removed from the inventory.
     */
    protected void onDestruct() {

    }

    /**
     * Returns the items that will be removed from the inventory.
     */
    protected abstract Item[] remove();
}