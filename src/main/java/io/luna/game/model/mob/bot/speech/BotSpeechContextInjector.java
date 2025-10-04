package io.luna.game.model.mob.bot.speech;

import io.luna.game.event.Event;
import io.luna.game.event.EventListenerPipeline;
import io.luna.game.model.mob.bot.Bot;

import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dynamically injects context into {@link Bot} speech where needed. Every injector has its own {@link BotSpeechPool}
 * of unique phrases to pick from, strictly compartmentalized by context. This provides smarter communication capabilities
 * for bots, allowing them to react to the world around them through speech.
 *
 * @param <T> The enum that will compartmentalize phrases in the backing pool.
 * @author lare96
 */
public abstract class BotSpeechContextInjector<T extends Enum<T>> {

    /**
     * The backing speech pool.
     */
    protected final BotSpeechPool<T> speechPool;

    /**
     * If this injector was started already.
     */
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * Creates a new {@link BotSpeechContextInjector}.
     *
     * @param fileName The file name of the speech pool.
     * @param type The speech pool context type.
     */
    public BotSpeechContextInjector(String fileName, Class<T> type) {
        speechPool = new BotSpeechPool<>(Paths.get(fileName), type);
    }

    /**
     * Loads the backing {@link #speechPool} and any additional resources associated with this injector
     * ({@link #load()}).
     */
    public final void start() {
        if(started.compareAndSet(false, true)) {
            speechPool.load();
            load();
        }
    }

    /**
     * Loads any additional resources associated with this injector.
     */
    public void load() {

    }

    /**
     * Listen for all world events after they were sent through an {@link EventListenerPipeline}.
     *
     * @param msg The received event.
     */
    public abstract void onEvent(Event msg);
}
