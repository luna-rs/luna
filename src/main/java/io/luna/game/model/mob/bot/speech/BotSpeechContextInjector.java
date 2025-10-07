package io.luna.game.model.mob.bot.speech;

import io.luna.game.event.Event;
import io.luna.game.event.EventListenerPipeline;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatColor;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler.ChatEffect;

import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base type for all bot speech injectors.
 * <p>
 * A {@link BotSpeechContextInjector} listens for global {@link Event}s and dynamically pushes
 * context-aware speech to associated {@link Bot} instances. Each injector manages its own
 * {@link BotSpeechPool} of phrases, strictly scoped to a single gameplay context
 * (e.g. combat reactions, level-ups, trades, NPC interactions).
 * <p>
 * By compartmentalizing phrases and reacting to live world events, injectors allow bots to
 * communicate intelligently and believably about their surroundings.
 *
 * @param <T> The enum type used to compartmentalize phrases in the backing pool.
 * @author lare96
 */
public class BotSpeechContextInjector<T extends Enum<T>> {

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
     * Initializes this injector if it has not already been started.
     * <p>
     * Loads the backing {@link #speechPool} and then invokes {@link #load()} for any subclass-specific
     * initialization or resource binding.
     */
    public final void start() {
        if (started.compareAndSet(false, true)) {
            speechPool.load();
            load();
        }
    }

    /**
     * Called during {@link #start()} to load any additional resources associated with this injector.
     * <p>
     * Subclasses may override this to perform setup tasks such as event subscriptions or precomputations.
     */
    public void load() {

    }

    /**
     * Handles an incoming {@link Event} routed from the global {@link EventListenerPipeline}.
     * <p>
     * Subclasses implement this method to detect relevant events and queue appropriate speech
     * into the corresponding bot's {@link BotSpeechStack}.
     *
     * @param event The event received from the world.
     */
    public void onEvent(Event event) {

    }

    /**
     * Handles a speech event triggered by a {@link Player}. This method allows injectors to know when nearby
     * players speak, enabling conversational reactions and social chatter.
     *
     * @param player The player who sent the message.
     * @param message The raw message text.
     * @param color The color used in the chat message.
     * @param effect The visual chat effect applied to the message.
     */
    public void onSpeech(Player player, String message, ChatColor color, ChatEffect effect) {

    }
}
