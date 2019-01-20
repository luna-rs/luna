package io.luna.game.action;

import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;

/**
 * A {@link StationaryAction} implementation that will remove items from and add items to the inventory.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class ProducingAction extends StationaryAction<Player> {

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
     */
    public ProducingAction(Player player, boolean instant, int delay) {
        super(player, instant, delay);
    }

    @Override
    protected final void call() {
        if (!canProduce()) {
            interrupt();
            return;
        }
        
        currentRemove = remove();
        var inventory = mob.getInventory();
        
        if (!inventory.containsAll(currentRemove)) {
            interrupt();
            return;
        }
        
        currentAdd = add();

        int addSpaces = inventory.computeSpaceForAll(currentRemove);
        int removeSpaces = inventory.computeSpaceForAll(currentAdd);
        int requiredSpaces = removeSpaces - addSpaces;
        
        if (requiredSpaces > inventory.computeRemainingSize()) {
            mob.sendMessage("You do not have enough space in your inventory.");
            interrupt();
            return;
        }
        
        inventory.removeAll(currentRemove);
        inventory.addAll(currentAdd);
        onProduce();
    }

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

    /**
     * @return The items that will be removed.
     */
    protected abstract Item[] remove();

    /**
     * @return The items that will be added.
     */
    protected abstract Item[] add();
}