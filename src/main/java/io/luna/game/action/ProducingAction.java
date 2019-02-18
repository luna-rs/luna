package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;

/**
 * A {@link FixedAction} implementation that will remove items from and add items to the inventory.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class ProducingAction extends FixedAction<Player> {

    /**
     * The items being added.
     */
    protected Item[] currentAdd;

    /**
     * The items being removed.
     */
    protected Item[] currentRemove;

    /**
     * Creates a new {@link ProducingAction}.
     *
     * @param player The player.
     * @param instant If this action executes instantly.
     * @param delay The delay of this action.
     * @param times The amount of times to produce.
     */
    public ProducingAction(Player player, boolean instant, int delay, int times) {
        super(player, instant, delay, times);
    }

    @Override
    protected final boolean canExecute() {
        return canProduce();
    }

    @Override
    protected final void execute() {
        currentRemove = remove();
        Inventory inventory = mob.getInventory();
        if (currentRemove.length > 0) {
            if (!inventory.containsAll(currentRemove)) {
                interrupt();
                return;
            }
        }

        currentAdd = add();
        int addSpaces = currentRemove.length > 0 ? inventory.computeSpaceForAll(currentRemove) : 0;
        int removeSpaces = currentAdd.length > 0 ? inventory.computeSpaceForAll(currentAdd) : 0;
        int requiredSpaces = removeSpaces - addSpaces;
        if (requiredSpaces > inventory.computeRemainingSize()) {
            mob.sendMessage("You do not have enough space in your inventory.");
            interrupt();
            return;
        }
        if (currentRemove.length > 0) {
            inventory.removeAll(currentRemove);
        }
        if (currentAdd.length > 0) {
            inventory.addAll(currentAdd);
        }
        onProduce();
    }

    /**
     * @return The items that will be removed.
     */
    protected abstract Item[] remove();

    /**
     * @return The items that will be added.
     */
    protected abstract Item[] add();

    /**
     * Function invoked at the beginning of every action loop.
     *
     * @return {@code false} to interrupt the action.
     */
    protected boolean canProduce() {
        return true;
    }

    /**
     * Function executed when items are removed from and added to the inventory.
     */
    protected void onProduce() {

    }
}