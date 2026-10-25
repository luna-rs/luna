package io.luna.game.action;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static com.google.common.base.Preconditions.checkState;

/**
 * Per-mob action scheduler responsible for registering and processing {@link Action}s each game cycle.
 * <p>
 * The queue is processed once per tick (or "cycle") for a single {@link Mob}. Actions are submitted into a processing
 * set and evaluated in two stages:
 * <ol>
 *   <li><b>Processing stage</b> ({@link Action#onProcess()}): gives actions a chance to update state each cycle and
 *       enqueue themselves for execution.</li>
 *   <li><b>Execution stage</b> (nested queue): actions are polled and executed in a stable order, allowing "nested"
 *       behavior where one action can trigger/queue another during the same cycle.</li>
 * </ol>
 *
 * <h3>Action type rules</h3>
 * <ul>
 *   <li>If any {@link ActionType#STRONG} action exists, all {@link ActionType#WEAK} actions are interrupted.</li>
 *   <li>If a strong action exists and the mob is a player, modal interfaces are closed before execution.</li>
 *   <li>{@link ActionType#NORMAL} actions are skipped during execution if a modal interface is open.</li>
 * </ul>
 * <strong>Implementation note:</strong> This class stores actions in a {@link ListMultimap} keyed by {@link ActionType}.
 * Processing order follows the multimap’s iteration order (which is insertion order for each key in {@link ArrayListMultimap}).
 *
 * @author lare96
 */
public final class ActionQueue {

    /**
     * The mob whose actions are processed by this queue.
     */
    private final Mob mob;

    /**
     * Registered actions currently in the queue, keyed by {@link ActionType}.
     * <p>
     * All actions in this multimap are considered "active" candidates for processing until they leave
     * {@link ActionState#PROCESSING}.
     */
    private final ListMultimap<ActionType, Action<?>> processing = ArrayListMultimap.create();

    /**
     * Per-cycle execution queue used to support nesting.
     * <p>
     * Actions that remain in {@link ActionState#PROCESSING} after {@link Action#onProcess()} are added here for the
     * execution stage.
     */
    private final Queue<Action<?>> executing = new ArrayDeque<>();

    /**
     * If actions are currently being run. Used to normalize processing delays when actions are scheduled within
     * actions.
     */
    private boolean running;

    /**
     * Creates a new {@link ActionQueue} for {@code mob}.
     *
     * @param mob The mob that owns this action queue.
     */
    public ActionQueue(Mob mob) {
        this.mob = mob;
    }

    /**
     * Submits an {@link Action} to this queue.
     * <p>
     * The action is immediately placed into the processing set, transitioned to {@link ActionState#PROCESSING},
     * and has {@link Action#onSubmit()} invoked.
     *
     * @param action The action to submit.
     */
    public void submit(Action<?> action) {
        action.setState(ActionState.PROCESSING);
        action.onSubmit();
        if (!action.isFinished()) {
            processing.put(action.actionType, action);
        }
    }

    /**
     * Submits {@code action} only if no other action of the same concrete type is currently being processed.
     * <p>
     * Only unfinished actions of the same exact runtime class prevent submission. Completed or interrupted
     * actions may remain queued until the next cycle, but do not prevent a replacement from being submitted.
     * <p>
     * If an action of the same class is already present, this method does nothing.
     *
     * @param action The action to submit if its concrete type is not already active.
     * @return {@code true} if the action was not already active, and therefore submitted.
     */
    public boolean submitIfAbsent(Action<?> action) {
        checkState(!action.getClass().isAnonymousClass(), "Anonymous classes cannot be used with submitIfAbsent.");
        for (Action<?> active : processing.values()) {
            if (active.getState() == ActionState.PROCESSING && active.getClass() == action.getClass()) {
                return false;
            }
        }
        submit(action);
        return true;
    }

    /**
     * Returns whether this queue currently contains an action assignable to {@code type}.
     * <p>
     * This checks both the processing set and the per-cycle execution queue. Finished actions are ignored.
     *
     * @param type The action class to check for.
     * @return {@code true} if an unfinished instance of {@code type} exists in the queue.
     */
    @Deprecated
    public <T extends Action<?>> boolean contains(Class<T> type) {
        return first(type) != null;
    }

    /**
     * Returns the first processing or executing action assignable to {@code type}.
     * <p>
     * Finished actions stay queued until the next cycle, so they are skipped here.
     *
     * @param type The action base type.
     * @return The first matching action, or {@code null} if there is none.
     */
    public <T extends Action<?>> T first(Class<T> type) {
        for (Action<?> action : processing.values()) {
            if (action.getState() == ActionState.PROCESSING && type.isAssignableFrom(action.getClass())) {
                return (T) action;
            }
        }
        for (Action<?> action : executing) {
            if (action.getState() == ActionState.PROCESSING && type.isAssignableFrom(action.getClass())) {
                return (T) action;
            }
        }
        return null;
    }

    /**
     * Returns all processing and executing actions assignable to {@code type}.
     * <p>
     * Finished actions stay queued until the next cycle, so they are skipped here.
     *
     * @param type The action base type.
     * @return All matching actions.
     */
    public <T extends Action<?>> List<T> getAll(Class<T> type) {
        List<T> filtered = new ArrayList<>();
        for (Action<?> action : processing.values()) {
            if (action.getState() == ActionState.PROCESSING && type.isAssignableFrom(action.getClass())) {
                filtered.add((T) action);
            }
        }
        for (Action<?> action : executing) {
            if (action.getState() == ActionState.PROCESSING && type.isAssignableFrom(action.getClass())) {
                filtered.add((T) action);
            }
        }
        return filtered;
    }

    /**
     * Returns all processing and executing actions with the {@link ActionType} {@code type}.
     * <p>
     * Finished actions stay queued until the next cycle, so they are skipped here.
     *
     * @param type The action type.
     * @return All matching actions.
     */
    public <T extends Action<?>> List<T> getAll(ActionType type) {
        List<T> filtered = new ArrayList<>();
        for (Action<?> action : processing.get(type)) {
            if (action.getState() == ActionState.PROCESSING) {
                filtered.add((T) action);
            }
        }
        for (Action<?> action : executing) {
            if (action.getState() == ActionState.PROCESSING && action.getActionType() == type) {
                filtered.add((T) action);
            }
        }
        return filtered;
    }

    /**
     * Processes this mob’s action queue for a single game cycle.
     * <p>
     * High-level flow:
     * <ol>
     *   <li>Interrupt {@link ActionType#WEAK} actions if any {@link ActionType#STRONG} exists.</li>
     *   <li>Remove actions that are no longer {@link ActionState#PROCESSING}.</li>
     *   <li>Call {@link Action#onProcess()} on remaining actions and enqueue them for execution.</li>
     *   <li>If a strong action exists, close player modal interfaces.</li>
     *   <li>Execute queued actions, skipping {@link ActionType#NORMAL} while a modal interface is open.</li>
     * </ol>
     */
    public void process() {
        // Most world NPCs have no actions. Avoid allocating multimap iterators for an idle queue.
        if (processing.isEmpty() && executing.isEmpty()) {
            return;
        }
        // 1) Remove actions that are no longer actively processing.
        processing.values().removeIf(action -> action.getState() != ActionState.PROCESSING);

        // 2) Strong actions suppress weak actions.
        if (processing.containsKey(ActionType.STRONG)) {
            interruptWeak();
        }

        // 3) Processing stage: per-tick hooks + enqueue for execution stage.
        for (Action<?> action : processing.values()) {
            action.onProcess();
            if (action.getState() != ActionState.PROCESSING) {
                continue;
            }
            action.processed = true;
            executing.add(action);
        }

        // 4) Close modal interfaces when a strong action is present (player only).
        if (processing.containsKey(ActionType.STRONG) && mob.getType() == EntityType.PLAYER) {
            mob.asPlr().getOverlays().closeWindows();
        }

        // 5) Execution stage: poll until all queued actions are handled.
        for (; ; ) {
            Action<?> action = executing.poll();
            if (action == null) {
                break;
            }

            // NORMAL actions are skipped if a modal interface is open.
            boolean skipForInterface =
                    action.actionType == ActionType.NORMAL &&
                            mob.getType() == EntityType.PLAYER &&
                            mob.asPlr().getOverlays().hasWindow();

            if (skipForInterface || action.getState() != ActionState.PROCESSING) {
                continue;
            }

            // Action completed normally this cycle.
            if (action.isComplete()) {
                action.complete();
            }
        }
    }

    /**
     * Normalizes all delays when actions are scheduled within actions.
     */
    public void normalize() {
        if (processing.isEmpty()) {
            return;
        }
        for (Action<?> action : processing.values()) {
            if (action.processed) {
                action.processed = false;
            } else {
                action.counter = 0;
            }
        }
    }

    /**
     * Interrupts all {@link ActionType#WEAK} actions immediately.
     * <p>
     * This is also invoked automatically during {@link #process()} when any strong action is present.
     */
    public void interruptWeak() {
        List<Action<?>> weakActions = new ArrayList<>(processing.get(ActionType.WEAK));
        for (Action<?> action : weakActions) {
            if (action.getState() == ActionState.PROCESSING) {
                action.interrupt();
            }
        }
    }

    /**
     * Interrupts all actions currently registered in this queue, regardless of {@link ActionType}.
     */
    public void interruptAll() {
        processing.values().forEach(Action::interrupt);
    }

    /**
     * Interrupts every action and empties this queue at once, instead of on the next {@link #process()}, so the same
     * actions can be submitted again straight away. Safe to call from a running action.
     */
    public void clear() {
        interruptAll();
        processing.clear();
        executing.clear();
    }
    //todo docs, testing

    /**
     * Returns the number of unfinished actions with the {@link ActionType} {@code type}.
     *
     * @param type The action type.
     * @return The active action count for {@code type}.
     */
    public int size(ActionType type) {
        int count = 0;
        for (Action<?> action : processing.get(type)) {
            if (action.getState() == ActionState.PROCESSING) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the number of unfinished actions. Finished actions stay queued until the next cycle, so they are not
     * counted.
     *
     * @return The active action count.
     */
    public int size() {
        int count = 0;
        for (Action<?> action : processing.values()) {
            if (action.getState() == ActionState.PROCESSING) {
                count++;
            }
        }
        return count;
    }
}
