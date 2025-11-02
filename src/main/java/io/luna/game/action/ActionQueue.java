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
 * A model that handles registration and processing of actions.
 *
 * @author lare96
 */
public final class ActionQueue {

    /**
     * The mob this queue will be processed for.
     */
    private final Mob mob;

    /**
     * The actions being processed.
     */
    private final ListMultimap<ActionType, Action<?>> processing = ArrayListMultimap.create();

    /**
     * The queue of actions awaiting execution.
     */
    private final Queue<Action<?>> executing = new ArrayDeque<>();

    /**
     * Creates a new {@link ActionQueue}.
     */
    public ActionQueue(Mob mob) {
        this.mob = mob;
    }

    /**
     * Submits an {@link Action} to the processing list.
     */
    public void submit(Action<?> action) {
        processing.put(action.actionType, action);
        action.setState(ActionState.PROCESSING);
        action.onSubmit();
    }

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

    public <T extends Action<?>> List<T> getAll(Class<T> type) {
        return (List<T>) processing.values().stream().filter(type::isInstance).collect(Collectors.toList());
    }

    /**
     * Processes the mob's action queue for this cycle.
     */
    public void process() {
        // Interrupt weak actions if there's a strong one present.
        if (processing.containsKey(ActionType.STRONG)) {
            interruptWeak();
        }

        // Clean up actions that have finished processing.
        processing.values().removeIf(action -> action.getState() != ActionState.PROCESSING);

        for (Action<?> action : processing.values()) {
            action.onProcess();
            if (action.getState() != ActionState.PROCESSING) {
                continue;
            }
            executing.add(action); // Add to execution queue, for nesting.
        }

        // Close standard interface if strong or soft action present.
        if (processing.containsKey(ActionType.STRONG)) {
            if (mob.getType() == EntityType.PLAYER) {
                mob.asPlr().getOverlays().closeWindows();
            }
        }

        for (; ; ) {
            Action<?> action = executing.poll();
            if (action == null) {
                break;
            }

            // Normal actions are skipped during execution if interface is open.
            if ((action.actionType == ActionType.NORMAL &&
                    mob.getType() == EntityType.PLAYER &&
                    mob.asPlr().getOverlays().hasWindow()) ||
                    action.getState() != ActionState.PROCESSING) {
                continue;
            }

            if (action.isComplete()) {
                action.complete(); // Action completed normally.
            }
        }
    }

    /**
     * Forces this queue to interrupt all {@link ActionType#WEAK} actions right away.
     */
    public void interruptWeak() {
        processing.get(ActionType.WEAK).forEach(Action::interrupt);
    }

    public void interruptAll() {
        processing.values().forEach(Action::interrupt);
    }
}
