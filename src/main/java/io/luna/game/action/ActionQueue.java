package io.luna.game.action;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

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
        for (Action<?> action : processing.values()) {
            if (action.getClass() == type) {
                return true;
            }
        }
        for (Action<?> action : executing) {
            if (action.getClass() == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all processing actions assignable to {@code type}.
     * <p>
     * Only actions currently stored in the processing set are returned (not the temporary execution queue).
     *
     * @param type The action base type.
     * @return All matching actions currently processing.
     */
    @SuppressWarnings("unchecked")
    public <T extends Action<?>> List<T> getAll(Class<T> type) {
        return (List<T>) processing.values()
                .stream()
                .filter(type::isInstance)
                .collect(Collectors.toList());
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
     * Returns the total number of actions currently registered in the processing set.
     *
     * @return The processing action count.
     */
    public int size() {
        return processing.size();
    }
}
