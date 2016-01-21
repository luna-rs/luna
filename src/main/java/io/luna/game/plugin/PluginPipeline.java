package io.luna.game.plugin;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.model.mobile.Player;
import plugin.Plugin;
import scala.Function0;
import scala.runtime.BoxedUnit;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * A pipeline-like model that allows for an event to be passed through the pipeline to each individual {@code Plugin}. The
 * traversal of the event through the pipeline can be terminated at any time be invoking {@code cancel()}.
 * <p>
 * <p>
 * Please note that {@code Plugin}s can always be added to this pipeline provided the operation isn't done during traversal,
 * but {@code Plugin}s can <strong>never</strong> be removed.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginPipeline<E> implements Iterable<Plugin<E>> {

    /**
     * A {@link Queue} of {@link Plugin}s contained within this pipeline.
     */
    private final Queue<Plugin<E>> plugins = new ArrayDeque<>();

    /**
     * A flag that determines if a traversal has been cancelled by a plugin.
     */
    private boolean cancelled;

    /**
     * Traverse the pipeline passing the {@code evt} instance to each {@link Plugin}. A full traversal over all {@code
     * Plugin}s is not always made.
     *
     * @param evt The event to pass to each {@code Plugin}.
     * @param player The {@link Player} to pass to each {@code Plugin}.
     */
    public void traverse(E evt, Player player) {
        cancelled = false;

        for (Plugin<E> it : this) {
            if (cancelled) {
                break;
            }
            it.plr_$eq(player);
            it.evt_$eq(evt);

            Function0<BoxedUnit> execute = it.execute();
            if (execute != null) {
                execute.apply();
            }
        }
    }

    /**
     * Cancels an active traversal of this pipeline, if this pipeline is not currently being traversed then this method does
     * nothing.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Adds {@code plugin} to the underlying pipeline. Throws a {@link ClassCastException} if the plugin event context is not
     * the same as the other {@code Plugin}s in this pipeline.
     *
     * @param plugin The {@link Plugin} to add.
     */
    @SuppressWarnings("unchecked")
    protected void add(Plugin<?> plugin) {
        Plugin<E> addPlugin = (Plugin<E>) plugin;

        addPlugin.pipeline_$eq(this);

        plugins.add(addPlugin);
    }

    @Override
    public UnmodifiableIterator<Plugin<E>> iterator() {
        return Iterators.unmodifiableIterator(plugins.iterator());
    }
}
