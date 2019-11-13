package io.luna.game.action;

import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;

import java.util.List;

/**
 * A {@link RepeatingAction} implementation that supports inventory modifications. Users should override the {@link #add()}
 * and {@link #remove()} functions to add and remove items.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class InventoryAction extends RepeatingAction<Player> {

    /**
     * The items being added.
     */
    protected List<Item> currentAdd;

    /**
     * The items being removed.
     */
    protected List<Item> currentRemove;

    /**
     * Creates a new {@link InventoryAction}.
     *
     * @param player The player.
     * @param instant If this action executes instantly.
     * @param delay The delay of this action.
     * @param times The amount of times to repeat.
     */
    public InventoryAction(Player player, boolean instant, int delay, int times) {
        super(player, instant, delay);
        setRepeat(times);
    }

    /**
     * Creates a new {@link InventoryAction} with a delay of {@code 1}.
     *
     * @param player The player.
     * @param instant If this action executes instantly.
     * @param times The amount of times to repeat.
     */
    public InventoryAction(Player player, boolean instant, int times) {
        this(player, instant, 1, times);
    }

    @Override
    public final boolean start() {
        mob.getWalking().clear();
        return executeIf(true);
    }

    @Override
    public final void repeat() {
        if (!executeIf(false)) {
            interrupt();
            return;
        }

        var inventory = mob.getInventory();
        currentRemove = remove();
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
        execute();
    }

    /**
     * Invoked when the inventory is changed.
     */
    public abstract void execute();

    /**
     * @return The items that will be removed.
     */
    public List<Item> remove() {
        return List.of();
    }

    /**
     * @return The items that will be added.
     */
    public List<Item> add() {
        return List.of();
    }

    /**
     * A function invoked every {@code delay} and on registration. Will stop the action and perform no processing if
     * {@code false} is returned.
     *
     * @param start If this action is currently being registered.
     * @return {@code false} to interrupt the action.
     */
    public boolean executeIf(boolean start) {
        return true;
    }
}