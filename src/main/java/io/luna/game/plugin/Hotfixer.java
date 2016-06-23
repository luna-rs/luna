package io.luna.game.plugin;

import io.luna.LunaContext;
import io.luna.game.GameService;
import io.luna.game.event.EventListenerPipelineSet;
import io.luna.util.FutureUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * A class allowing for Scala plugins to be dynamically 'reloaded' without interfering with gameplay. Only one call to {@code
 * init()} can be done per {@code Hotfixer} instance or an {@link IllegalStateException} will be thrown. A simplified version
 * of the entire procedure is as follows
 * <p>
 * 1. Initialize a new {@link PluginBootstrap}, to create new pipelines.
 * <p>
 * 2. Initialize a new {@code HotfixerCallback}, to notify listeners and replace old pipelines with new ones from the {@code
 * PluginBootstrap}.
 * <p>
 * 3. Schedule the {@code PluginBootstrap} to run asynchronously, add {@code HotfixerCallback} as a callback to the bootstrap
 * to handle the result of the computation.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Hotfixer {

    /**
     * A callback that will be ran synchronously on the game thread once the {@link PluginBootstrap} finishes reinterpreting
     * plugins. Its primary jobs are to notify listeners of completion and dynamically replace the current pipeline set.
     */
    private final class HotfixerCallback implements Consumer<EventListenerPipelineSet> {

        @Override
        public void accept(EventListenerPipelineSet pipelines) {
            PluginManager plugins = context.getPlugins();

            for (; ; ) { // Notify listeners of completion, discard them.
                Consumer<EventListenerPipelineSet> listener = listeners.poll();
                if (listener == null) {
                    break;
                }
                listener.accept(pipelines);
            }
            plugins.getPipelines().replacePipelines(pipelines); // Dynamically replace pipelines.

            LOGGER.info("Hotfix successful! Plugins were reinterpreted.");
        }
    }

    /**
     * Returns a new {@link Hotfixer} instance.
     */
    public static Hotfixer newHotfixer(LunaContext context) {
        return new Hotfixer(context);
    }

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * An {@link ArrayList} of completion listeners.
     */
    private final Queue<Consumer<EventListenerPipelineSet>> listeners = new ConcurrentLinkedQueue<>();

    /**
     * An {@link AtomicBoolean} describing if this hotfixer has been initialized.
     */
    private final AtomicBoolean initialized = new AtomicBoolean();

    /**
     * The {@link LunaContext} instance.
     */
    private final LunaContext context;

    /**
     * Creates a new {@link Hotfixer}.
     *
     * @param context The {@link LunaContext} instance.
     */
    private Hotfixer(LunaContext context) {
        this.context = requireNonNull(context);
    }

    /**
     * Adds a completion listener to the backing queue. Although this method is thread safe, if listeners are added to the
     * queue after initialization no guarantee can be made on whether or not they will be ran. If the hotfix is unsuccessful
     * the listeners will <strong>never</strong> be ran.
     *
     * @param listener The listener that will be notified on completion of the hotfix.
     */
    public Hotfixer addListener(Consumer<EventListenerPipelineSet> listener) {
        listeners.add(requireNonNull(listener));
        return this;
    }

    /**
     * Initializes this hotfixer by asynchronously running a new {@link PluginBootstrap} instance and using the {@link
     * HotfixerCallback} to handle the result of the operation.
     * <p>
     * This method will throw an {@link IllegalStateException} if this hotfixer has already been initialized.
     */
    public void init() {
        checkState(initialized.compareAndSet(false, true), "Hotfixer has already been initialized");

        LOGGER.info("Initializing asynchronous hotfix...");

        HotfixerCallback callback = new HotfixerCallback();
        GameService service = context.getService();
        FutureUtils.addCallback(service.submit(new PluginBootstrap(context)), callback);
    }
}
