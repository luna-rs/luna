package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;

import java.util.List;

/**
 * A {@link RepeatingAction} implementation that supports inventory modifications. Users should override the {@link #add()}
 * and {@link #remove()} functions to add and remove items.
 *
 * @author lare96 
 */
public abstract class ItemContainerAction extends RepeatingAction<Player> {

    /**
     * An {@link ItemContainerAction} implementation that works with the inventory.
     */
    public static abstract class InventoryAction extends ItemContainerAction {

        /**
         * Creates a new {@link InventoryAction}.
         *
         * @param player The player.
         * @param instant If this action executes instantly.
         * @param delay The delay of this action.
         * @param repeatTimes The amount of times to repeat.
         */
        public InventoryAction(Player player, boolean instant, int delay, int repeatTimes) {
            super(player, player.getInventory(), instant, delay, repeatTimes);
        }
    }

    // todo rename to animatedinventoryaction, see if this is really needed. apply to other plugins
    public static abstract class UnsynchronizedInventoryAction extends InventoryAction{
        private final int animationDelay;
        private int animationTimer;
        public UnsynchronizedInventoryAction(Player player, boolean instant, int actionDelay, int animationDelay, int repeatTimes) {
            super(player, instant, actionDelay, repeatTimes);
            this.animationDelay = animationDelay;
        }

        public abstract Animation animation();
        public void onProcess() {

        }
        @Override
        public final void process() {
            onProcess();
            if (--animationTimer < 1) {
                mob.animation(animation());
                animationTimer = animationDelay;
            }
        }
    }

    /**
     * The item container.
     */
    private final ItemContainer container;

    /**
     * The items being added.
     */
    protected List<Item> currentAdd;

    /**
     * The items being removed.
     */
    protected List<Item> currentRemove;

    /**
     * Creates a new {@link ItemContainerAction}.
     *
     * @param player The player.
     * @param container The container.
     * @param instant If this action executes instantly.
     * @param delay The delay of this action.
     * @param repeatTimes The amount of times to repeat.
     */
    public ItemContainerAction(Player player, ItemContainer container, boolean instant, int delay, int repeatTimes) {
        super(player, instant, delay);
        this.container = container;
        setRepeat(repeatTimes);
    }

    /**
     * Creates a new {@link ItemContainerAction} with a delay of {@code 1}.
     *
     * @param player The player.
     * @param container The container.
     * @param instant If this action executes instantly.
     * @param times The amount of times to repeat.
     */
    public ItemContainerAction(Player player, ItemContainer container, boolean instant, int times) {
        this(player,container, instant, 1, times);
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

        currentRemove = remove();
        if (isInterrupted() || !container.containsAll(currentRemove)) {
            if(getExecutions() == 0) {
                mob.sendMessage("You do not have enough materials to do this.");
            }
            interrupt();
            return;
        }

        currentAdd = add();
        int addSpaces = container.computeSpaceForAll(currentRemove);
        int removeSpaces = container.computeSpaceForAll(currentAdd);
        int requiredSpaces = removeSpaces - addSpaces;
        if(isInterrupted()) {
            return;
        }
        if (requiredSpaces > container.computeRemainingSize()) {
            mob.sendMessage(Inventory.INVENTORY_FULL_MESSAGE);
            interrupt();
            return;
        }
        container.removeAll(currentRemove);
        container.addAll(currentAdd);
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