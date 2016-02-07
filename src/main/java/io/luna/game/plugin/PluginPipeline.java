package io.luna.game.plugin;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.model.mobile.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import plugin.PluginEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A pipeline-like model that allows for an event to be passed through the pipeline to each individual {@link
 * PluginFunction}. The traversal of the event through the pipeline can be terminated at any time by invoking {@code
 * terminate()}.
 * <p>
 * Please note that {@code PluginFunction}s can always be added to this pipeline, but they can <strong>never</strong> be
 * removed.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginPipeline<E extends PluginEvent> implements Iterable<PluginFunction<E>> {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(PluginPipeline.class);

    /**
     * A {@link List} of {@link PluginFunction} contained within this pipeline.
     */
    private final List<PluginFunction<E>> pluginFunctions = new ArrayList<>();

    /**
     * A flag that determines if a traversal has been terminated by a {@link PluginFunction}.
     */
    private boolean terminated;

    /**
     * Traverse the pipeline passing the {@code evt} instance to each {@link PluginFunction}. A full traversal over all
     * {@code PluginFunction}s is not always made.
     *
     * @param evt The event to pass to each {@code PluginFunction}.
     * @param player The {@link Player} to pass to each {@code PluginFunction}, possibly {@code null}.
     */
    public void traverse(E evt, Player player) {
        terminated = false;

        for (PluginFunction<E> function : pluginFunctions) {
            if (terminated) {
                break;
            }
            evt.pipeline_$eq(this);

            try {
                function.getFunction().apply(evt, player);
            } catch (PluginFailureException failure) { // fail, recoverable
                LOGGER.catching(failure);
            } catch (Exception other) { // unknown, unrecoverable
                throw new PluginFailureException(other);
            }
        }
    }

    /**
     * Terminates an active traversal of this pipeline, if this pipeline is not currently being traversed then this method
     * does nothing.
     *
     * @return {@code true} if termination was successful, {@code false} if this pipeline traversal has already been
     * terminated.
     */
    public boolean terminate() {
        if (!terminated) {
            terminated = true;
            return true;
        }
        return false;
    }

    /**
     * Adds {@code function} to the underlying pipeline. May throw a {@link ClassCastException} if the event type doesn't
     * match the other {@link PluginFunction}s in this pipeline.
     *
     * @param function The {@code PluginFunction} to add.
     */
    @SuppressWarnings("unchecked")
    protected void add(PluginFunction<?> function) {
        pluginFunctions.add((PluginFunction<E>) function);
    }

    @Override
    public UnmodifiableIterator<PluginFunction<E>> iterator() {
        return Iterators.unmodifiableIterator(pluginFunctions.iterator());
    }
}
