package io.luna.game.action;

import io.luna.game.model.mob.Mob;

/**
 * An {@link Action} implementation which queues up to {@code 1} additional queued action upon equality. Any subsequent
 * equal actions submitted are ignored. This behaviour is required for content such as alchemy and thieving.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class QueuedAction<T extends Mob> extends Action<T> {

    /**
     * The queued action.
     */
    protected Runnable queuedAction;

    /**
     * Creates a new {@link QueuedAction}.
     *
     * @param mob The {@link Mob} assigned to this action.
     * @param duration
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
        
        if (queuedAction != null) {
            queuedAction.run();
        }
        
        interrupt();
    }

    @Override
    protected final boolean isEqual(Action<?> other) {
        if (!(other instanceof QueuedAction<?>)) {
            return false;
        }
        
        return isQueued((QueuedAction<?>) other);
    }

    @Override
    protected final void onEquals(Action<?> other) {
        if (queuedAction == null) {
            queuedAction = ((QueuedAction<?>) other)::execute;
        }
    }

    // TODO documentation
    protected abstract void execute();

    protected abstract boolean isQueued(QueuedAction<?> other);

    protected void onDuration() {
    }
}