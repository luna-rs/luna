package io.luna.game.action;

import io.luna.game.model.mob.Mob;

import java.util.Optional;

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
    protected Optional<Runnable> queuedAction = Optional.empty();

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
        queuedAction.ifPresent(Runnable::run);
        interrupt();
    }

    @Override
    protected final boolean isEqual(Action<?> other) {
        if (other instanceof QueuedAction<?>) {
            boolean is = isQueued((QueuedAction<?>) other);
            System.out.println("here2: " + is);

            return is;
        }
        return false;
    }

    @Override
    protected final void onEquals(Action<?> other) {
        System.out.println("here3");

        if (!queuedAction.isPresent()) {
            System.out.println("here4");
            QueuedAction<?> queued = (QueuedAction<?>) other;
            queuedAction = Optional.of(queued::execute);
        }
    }

    // TODO documentation
    protected abstract void execute();

    protected abstract boolean isQueued(QueuedAction<?> other);

    protected void onDuration() {
    }
}