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
 * Manages the {@link Action}s submitted to a single {@link Mob}.
 * <p>
 * {@link #process()} runs actions in {@link ActionPhase} order. Each phase snapshots its candidates before
 * execution, allowing an interaction to submit combat for a later phase of the same turn. Submissions to a phase
 * whose snapshot has already been taken wait for a later pass. Insertion order is retained within each action type.
 * <p>
 * {@link ActionType} determines interruption and modal behavior independently of execution order. An unfinished
 * strong action interrupts weak actions at each phase boundary. Normal actions continue receiving their processing
 * hooks while a modal is open, but their execution counters do not advance.
 * <p>
 * {@link #processAfterMovement()} revisits interaction and combat phases after walking. Existing actions use
 * {@link Action#runAfterMovement()} without a second counter update; newly submitted actions receive their first
 * normal processing cycle. {@link #normalize()} clears per-tick processing flags after world synchronization.
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
     * The candidate snapshot for the phase currently being processed.
     * <p>
     * Candidates are checked again before execution so an earlier action may finish, interrupt, or clear them.
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
     * The action first transitions to {@link ActionState#PROCESSING} and receives {@link Action#onSubmit()}.
     * It is added to the processing set only if that hook leaves it unfinished. Actions such as immediate
     * consumption may therefore complete during submission without being retained by the queue.
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
     * Processes this mob's actions once before walking, in {@link ActionPhase} declaration order.
     * <p>
     * Finished actions are removed before the phase passes begin. Incoming effects precede ordinary actions,
     * reach-based interactions, and outgoing combat. Each pass applies the interruption and modal rules before
     * executing its candidate snapshot.
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
     * Revisits interaction and combat phases after this mob's walking has been processed.
     * <p>
     * Existing actions receive {@link Action#runAfterMovement()} without advancing their counters or repeating
     * processing hooks. An action submitted since the initial pass receives its first normal processing cycle.
     * This allows an interaction reached by walking to start combat during the same turn.
     */
    public void processAfterMovement() {
        if (processing.isEmpty()) {
            return;
        }
        processPhase(ActionPhase.INTERACTION, true);
        processPhase(ActionPhase.COMBAT, true);
    }

    /**
     * Processes a candidate snapshot for one execution phase.
     * <p>
     * Strong-action suppression is checked before taking the snapshot. Candidates are rechecked for completion
     * before execution, and modal-blocked normal actions retain their processing hooks without advancing time.
     *
     * @param phase The phase whose actions should be considered.
     * @param afterMovement Whether existing candidates should receive their reach-retry hook.
     */
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
     * Resets processing flags and normalizes newly submitted action counters after the world tick's action passes.
     * <p>
     * An action that did not receive a processing cycle starts counting from zero on its next eligible pass.
     * This keeps delayed submissions consistent when their owner's turn has already finished.
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
