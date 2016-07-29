package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;

/**
 * A {@link SkillAction} implementation where items are removed from the {@link Inventory} of a {@link Player}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class DestructionSkillAction extends SkillAction {

    /**
     * The array of items currently being removed.
     */
    protected Item[] currentRemove;

    /**
     * Creates a new {@link DestructionSkillAction}.
     */
    public DestructionSkillAction(Player player, boolean instant, int delay) {
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
     * Function invoked at the beginning of every Action loop. Return {@code false} to interrupt the Action.
     */
    protected boolean canDestruct() {
        return true;
    }

    /**
     * Function invoked when {@code remove()} is successfully removed from the {@link Inventory}.
     */
    protected void onDestruct() {

    }

    /**
     * Returns the items that will be removed from the {@link Inventory}.
     */
    protected abstract Item[] remove();
}