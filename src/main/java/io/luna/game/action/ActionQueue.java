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
 * Per-mob action scheduler. Execution follows {@link ActionPhase}; interruption and modal rules follow
 * {@link ActionType}. Strong actions interrupt weak actions, and normal actions wait while a modal is open.
 * <p>
 * Each phase snapshots its candidates before execution. Submissions to a later phase can execute in the same turn;
 * submissions to the current phase wait. Insertion order is retained within each action type. Combat therefore has
 * a defined position after incoming effects and ordinary actions rather than relying on multimap key order.
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
     * Processes effects, ordinary actions, interactions, then combat. Each phase takes its own snapshot so an
     * interaction can establish combat in the same turn. Nested submissions to the current phase wait until
     * the next turn. Strong actions are checked at every phase boundary.
     */
    public void process() {
        if (processing.isEmpty() && executing.isEmpty()) {
            return;
        }
        processing.values().removeIf(Action::isFinished);
        for (ActionPhase phase : ActionPhase.values()) {
            processPhase(phase, false);
        }
    }

    /**
     * Retries interactions and combat after walking. Existing actions do not advance their counters or processing
     * hooks twice. Combat created by a just-reached interaction may execute its first cycle here.
     */
    public void processAfterMovement() {
        if (processing.isEmpty()) {
            return;
        }
        processPhase(ActionPhase.INTERACTION, true);
        processPhase(ActionPhase.COMBAT, true);
    }

    private void processPhase(ActionPhase phase, boolean afterMovement) {
        boolean strong = processing.get(ActionType.STRONG).stream().anyMatch(action -> !action.isFinished());
        if (strong) {
            interruptWeak();
            if (mob.getType() == EntityType.PLAYER) {
                mob.asPlr().getOverlays().closeWindows();
            }
        }

        // Hooks may submit or cancel actions; iterate a snapshot rather than the live multimap.
        for (Action<?> action : new ArrayList<>(processing.values())) {
            if (action.isFinished() || action.getPhase() != phase) {
                continue;
            }
            executing.add(action);
        }
        for (Action<?> action; (action = executing.poll()) != null; ) {
            boolean skipForInterface = action.actionType == ActionType.NORMAL &&
                    mob.getType() == EntityType.PLAYER && mob.asPlr().getOverlays().hasWindow();
            if (action.isFinished()) {
                continue;
            }
            if (afterMovement && action.processed) {
                if (skipForInterface) {
                    continue;
                }
                try {
                    if (action.runAfterMovement()) {
                        action.complete();
                    }
                } catch (Exception e) {
                    Action.logger.error("An error occurred while retrying an interaction after movement.", e);
                    action.interrupt();
                }
            } else {
                action.processed = true;
                action.onProcess();
                if (!skipForInterface && !action.isFinished() && action.isComplete()) {
                    action.complete();
                }
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
        new ArrayList<>(processing.values()).forEach(Action::interrupt);
    }

    /**
     * Interrupts all unfinished actions and immediately empties the processing and execution queues.
     * <p>
     * Each interrupted action receives its {@link Action#onFinished()} callback. Actions that have already completed
     * or been interrupted are removed without invoking their callbacks again.
     * <p>
     * This method may be called from {@link Action#run()}. Remaining actions will not execute during that cycle,
     * and new actions may be submitted without waiting for the next {@link #process()} call.
     */
    public void clear() {
        interruptAll();
        processing.clear();
        executing.clear();
    }

    /**
     * Returns the number of actions in {@link ActionState#PROCESSING} with the supplied {@link ActionType}.
     * <p>
     * Completed and interrupted actions are excluded even if they have not yet been removed from the queue.
     * Actions awaiting execution during the current cycle are counted once.
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
     * Returns the number of actions in {@link ActionState#PROCESSING}, regardless of {@link ActionType}.
     * <p>
     * Completed and interrupted actions are excluded even if they have not yet been removed from the queue.
     * Actions awaiting execution during the current cycle are counted once.
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
