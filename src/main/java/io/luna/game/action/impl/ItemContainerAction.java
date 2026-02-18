package io.luna.game.action.impl;

import io.luna.game.action.Action;
import io.luna.game.action.ActionState;
import io.luna.game.action.ActionType;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;

import java.util.List;

/**
 * An {@link Action} that performs atomic modifications on an {@link ItemContainer} (add/remove lists).
 * <p>
 * This is a convenience base for “consume X, produce Y” style interactions (crafting, fletching, cooking, etc.).
 * Implementations provide the items to remove via {@link #remove()} and items to add via {@link #add()}, and can run
 * custom logic via {@link #execute()} after the container has been updated.
 * <h3>Action semantics</h3>
 * <ul>
 *   <li>Always uses {@link ActionType#WEAK} (does not hard-lock the player).</li>
 *   <li>On submit, clears walking if {@link #executeIf(boolean)} allows starting.</li>
 *   <li>Each execution validates requirements, checks space, then applies container changes.</li>
 *   <li>Stops early when requirements are not met or the action is interrupted.</li>
 * </ul>
 *
 * @author lare96
 */
public abstract class ItemContainerAction extends Action<Player> {

    /**
     * Specialization of {@link ItemContainerAction} targeting the player's {@link Inventory}.
     */
    public static abstract class InventoryAction extends ItemContainerAction {

        /**
         * Creates a new {@link InventoryAction}.
         *
         * @param player The player.
         * @param instant Whether the action should execute instantly.
         * @param delay The action delay (ticks) between executions.
         * @param repeatTimes Number of executions before completing.
         */
        public InventoryAction(Player player, boolean instant, int delay, int repeatTimes) {
            super(player, player.getInventory(), instant, delay, repeatTimes);
        }
    }

    /**
     * Inventory action that also plays an {@link Animation} at a fixed tick cadence.
     * <p>
     * This is meant for content where the server-side action loop must stay synchronized with an animation cycle
     * (e.g., repeated skilling animations).
     */
    public static abstract class AnimatedInventoryAction extends InventoryAction {

        /**
         * Ticks between animation replays.
         */
        private final int animationDelay;

        /**
         * Countdown timer for the next animation replay.
         */
        private int animationTimer;

        /**
         * Creates a new {@link AnimatedInventoryAction}.
         *
         * @param player The player.
         * @param actionDelay The delay (ticks) between action executions.
         * @param animationDelay The delay (ticks) between animation replays.
         * @param repeatTimes Number of executions before completing.
         */
        public AnimatedInventoryAction(Player player, int actionDelay, int animationDelay, int repeatTimes) {
            super(player, false, actionDelay, repeatTimes);
            this.animationDelay = animationDelay;
        }

        /**
         * Supplies the animation played every {@link #animationDelay} ticks while the action is processing.
         *
         * @return The animation to play.
         */
        public abstract Animation animation();

        /**
         * Per-tick hook called before animation timing logic.
         * <p>
         * Override for any additional per-tick behavior needed while processing.
         */
        public void onProcessAnimation() {
            /* optional */
        }

        @Override
        public final void onProcess() {
            onProcessAnimation();
            if (--animationTimer < 1) {
                mob.animation(animation());
                animationTimer = animationDelay;
            }
        }
    }

    /**
     * The container being modified (inventory, bank, etc.).
     */
    private final ItemContainer container;

    /**
     * Remaining executions before completion.
     */
    private int repeat;

    /**
     * Items computed for the current cycle to add to the container.
     */
    protected List<Item> currentAdd;

    /**
     * Items computed for the current cycle to remove from the container.
     */
    protected List<Item> currentRemove;

    /**
     * Creates a new {@link ItemContainerAction}.
     *
     * @param player The player.
     * @param container The container to modify.
     * @param instant Whether the action executes instantly.
     * @param delay Delay (ticks) between executions.
     * @param repeat Number of executions before completing.
     */
    public ItemContainerAction(Player player, ItemContainer container, boolean instant, int delay, int repeat) {
        super(player, ActionType.WEAK, instant, delay);
        this.container = container;
        this.repeat = repeat;
    }

    @Override
    public final void onSubmit() {
        if (executeIf(true)) {
            mob.getWalking().clear();
        } else {
            complete();
        }
    }

    @Override
    public boolean run() {
        return handleItems();
    }

    /**
     * Executes one “cycle” of the action.
     * <p>Flow:
     * <ol>
     *   <li>Validate {@link #executeIf(boolean)} for this tick.</li>
     *   <li>Compute {@link #remove()} and ensure items exist.</li>
     *   <li>Compute {@link #add()}.</li>
     *   <li>Verify container space after remove/add.</li>
     *   <li>Apply remove/add, then call {@link #execute()}.</li>
     * </ol>
     *
     * @return {@code true} to complete the action and remove it from the queue.
     */
    private boolean handleItems() {
        if (!executeIf(false)) {
            return true;
        }

        currentRemove = remove();
        if (getState() != ActionState.PROCESSING || !container.containsAll(currentRemove)) {
            if (getExecutions() == 0) {
                mob.newDialogue().text(onNoMaterials());
            }
            return true;
        }

        currentAdd = add();
        if (getState() != ActionState.PROCESSING) {
            return true;
        }

        int addSpaces = container.computeSpaceForAll(currentRemove);
        int removeSpaces = container.computeSpaceForAll(currentAdd);
        int requiredSpaces = removeSpaces - addSpaces;

        if (requiredSpaces > container.computeRemainingSize()) {
            mob.sendMessage(onInventoryFull());
            return true;
        }

        if (getExecutions() == 0) {
            mob.getOverlays().closeWindows(false);
        }

        container.removeAll(currentRemove);
        container.addAll(currentAdd);
        execute();

        return --repeat == 0;
    }

    /**
     * @return Message sent when the container lacks space for the operation.
     */
    public String onInventoryFull() {
        return Inventory.INVENTORY_FULL_MESSAGE;
    }

    /**
     * @return Message sent when required inputs are missing on the first execution.
     */
    public String onNoMaterials() {
        return "You do not have enough materials to do this.";
    }

    /**
     * Called after the container has been modified for this cycle.
     * <p>
     * Use this for XP, sounds, graphics, interfaces, etc.
     */
    public abstract void execute();

    /**
     * Supplies the items to remove for this execution.
     *
     * @return Items to remove (may be empty).
     */
    public List<Item> remove() {
        return List.of();
    }

    /**
     * Supplies the items to add for this execution.
     *
     * @return Items to add (may be empty).
     */
    public List<Item> add() {
        return List.of();
    }

    /**
     * Predicate invoked on submit and every cycle.
     * <p>
     * If this returns {@code false}, the action completes immediately and performs no processing.
     *
     * @param start {@code true} if invoked during registration/submit; {@code false} if invoked during a cycle.
     * @return {@code false} to prevent starting or to stop the action.
     */
    public boolean executeIf(boolean start) {
        return true;
    }
}
