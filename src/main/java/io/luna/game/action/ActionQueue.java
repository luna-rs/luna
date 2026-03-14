package io.luna.game.action;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multiset;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;

/**
 * Per-mob action scheduler responsible for registering and processing {@link Action}s each game cycle.
 * <p>
 * The queue is processed once per tick (or “cycle”) for a single {@link Mob}. Actions are submitted into a processing
 * set and evaluated in two stages:
 * <ol>
 *   <li><b>Processing stage</b> ({@link Action#onProcess()}): gives actions a chance to update state each cycle and
 *       enqueue themselves for execution.</li>
 *   <li><b>Execution stage</b> (nested queue): actions are polled and executed in a stable order, allowing “nested”
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
     * All actions in this multimap are considered “active” candidates for processing until they leave
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
     * Fast-membership set of action classes currently present in this queue.
     * <p>
     * This set exists purely as an O(1) {@link #contains(Class)} shortcut for “is an action of type X currently
     * queued?”, avoiding a linear scan across {@link #processing} and {@link #executing}.
     */
    private final Multiset<Class<?>> types = HashMultiset.create();

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
        processing.put(action.actionType, action);
        action.setState(ActionState.PROCESSING);
        action.onSubmit();
        types.add(action.getClass());
    }

    /**
     * Submits {@code action} only if no other action of the same concrete type is currently being processed.
     * <p>
     * This method uses the action's runtime class as a uniqueness key. If that type has not already been registered,
     * the action is submitted as normal.
     * <p>
     * If an action of the same class is already present, this method does nothing.
     *
     * @param action The action to submit if its concrete type is not already active.
     */
    public void submitIfAbsent(Action<?> action) {
        Class<?> type = action.getClass();
        if (types.add(type)) {
            processing.put(action.actionType, action);
            action.setState(ActionState.PROCESSING);
            action.onSubmit();
        }
    }

    /**
     * Returns whether this queue currently contains an action whose concrete class is exactly {@code type}.
     * <p>
     * This checks both the processing set and the per-cycle execution queue.
     *
     * @param type The exact action class to check for.
     * @return {@code true} if an instance of {@code type} exists in the queue.
     */
    public boolean contains(Class<? extends Action<?>> type) {
        return types.contains(type);
    }

    /**
     * Returns all processing and executing actions assignable to {@code type}.
     *
     * @param type The action base type.
     * @return All matching actions.
     */
    public <T extends Action<?>> T first(Class<T> type) {
        for (Action<?> action : processing.values()) {
            if (type.isAssignableFrom(action.getClass())) {
                return (T) action;
            }
        }
        for (Action<?> action : executing) {
            if (type.isAssignableFrom(action.getClass())) {
                return (T) action;
            }
        }
        return null;
    }

    /**
     * Returns all processing and executing actions assignable to {@code type}.
     *
     * @param type The action base type.
     * @return All matching actions.
     */
    public <T extends Action<?>> List<T> getAll(Class<T> type) {
        List<T> filtered = new ArrayList<>();
        for (Action<?> action : processing.values()) {
            if (type.isAssignableFrom(action.getClass())) {
                filtered.add((T) action);
            }
        }
        for (Action<?> action : executing) {
            if (type.isAssignableFrom(action.getClass())) {
                filtered.add((T) action);
            }
        }
        return filtered;
    }

    /**
     * Returns all processing and executing actions assignable to {@code type}.
     *
     * @param type The action base type.
     * @return All matching actions.
     */
    public <T extends Action<?>> List<T> getAll(ActionType type) {
        List<T> filtered = new ArrayList<>((Collection<? extends T>) processing.get(type));
        for (Action<?> action : executing) {
            if (action.getActionType() == type) {
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

        // 1) Strong actions suppress weak actions.
        if (processing.containsKey(ActionType.STRONG)) {
            interruptWeak();
        }

        // 2) Remove actions that are no longer actively processing.
        processing.values().removeIf(action -> action.getState() != ActionState.PROCESSING);

        // 3) Processing stage: per-tick hooks + enqueue for execution stage.
        for (Action<?> action : processing.values()) {
            action.onProcess();
            if (action.getState() != ActionState.PROCESSING) {
                continue;
            }
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
     * Intended for internal use, removes the specified action type from the cache.
     */
    void removeType(Action<?> action) {
        types.remove(action.getClass());
    }

    /**
     * Interrupts all {@link ActionType#WEAK} actions immediately.
     * <p>
     * This is also invoked automatically during {@link #process()} when any strong action is present.
     */
    public void interruptWeak() {
        processing.get(ActionType.WEAK).forEach(Action::interrupt);
    }

    /**
     * Interrupts all actions currently registered in this queue, regardless of {@link ActionType}.
     */
    public void interruptAll() {
        processing.values().forEach(Action::interrupt);
    }

    /**
     * @return The active action count.
     */
    public int size() {
        return executing.size() + processing.size();
    }
}
