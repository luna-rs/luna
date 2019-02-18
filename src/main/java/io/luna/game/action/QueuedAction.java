package io.luna.game.action;

import io.luna.game.model.mob.Mob;

import java.util.Optional;

/**
 * An {@link Action} implementation which queues up to {@code 1} additional action. Any subsequent actions submitted
 * replace the old action. This behaviour is required for content such as alchemy and thieving.
 * <p>
 * <p>
 * These actions are executed instantly. The {@code duration} parameter simply refers to how long the player must wait
 * to execute a subsequent action. If this action hasn't been interrupted, once the duration completes the queued
 * action is executed.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class QueuedAction<T extends Mob> extends Action<T> {

    /**
     * The queued action.
     */
    protected Optional<Action<?>> queuedAction = Optional.empty();

    /**
     * Creates a new {@link QueuedAction}.
     *
     * @param mob The {@link Mob} assigned to this action.
     * @param duration The duration of this action. All subsequent actions submitted within this duration will
     * be queued.
     */
    public QueuedAction(T mob, int duration) {
        super(mob, false, duration);
    }

    @Override
    protected void onInit() {
        execute();
    }

    @Override
    protected final void call() {
        onDuration();

        // Interrupt this action and execute the queued action.
        interrupt();
        queuedAction.ifPresent(Action::call);
    }

    @Override
    protected final boolean isEqual(Action<?> other) {
        return true;
    }

    @Override
    protected final void onEquals(Action<?> other) {
        // Queue all incoming actions. The player is essentially "locked" from doing another action until this one
        // completes (or this action is interrupted).
        queuedAction = Optional.of(other);
    }

    /**
     * Executes this action. Invoked when this action is submitted.
     */
    protected abstract void execute();

    /**
     * Executes when this action's duration elapses.
     */
    protected void onDuration() {
    }
}