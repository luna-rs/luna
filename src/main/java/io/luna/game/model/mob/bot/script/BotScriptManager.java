package io.luna.game.model.mob.bot.script;

import api.bot.BotScript;
import io.luna.game.model.mob.bot.Bot;
import kotlin.reflect.KClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A central registry and factory for constructing {@link BotScript} instances.
 * <p>
 * This manager maintains a mapping of registered script types and their corresponding constructor functions, allowing
 * scripts to be instantiated dynamically without reflection or hardcoded dependencies.
 * <p>
 * Scripts can be registered once during initialization, and later reconstructed from serialized state data during
 * bot persistence or login.
 *
 * @author lare96
 */
public final class BotScriptManager {

    /**
     * A functional interface for constructing {@link BotScript} instances.
     * <p>
     * Acts as a type-safe factory method taking a {@link Bot} and script-specific data as input, returning a
     * fully constructed script instance.
     *
     * @param <T> The script data type.
     */
    public interface ScriptSupplier<T> extends BiFunction<Bot, T, BotScript<T>> {

    }

    /**
     * The logger for this class.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The registry of script constructors, mapped by their fully qualified class names.
     */
    private final Map<String, ScriptSupplier<?>> scriptMap = new HashMap<>();

    /**
     * Registers a new {@link BotScript} type and its corresponding factory function.
     * <p>
     * This should be called once for each script type during server initialization.
     *
     * @param type The Kotlin class of the script.
     * @param newScript The factory function that constructs the script.
     * @param <T> The type of data used to initialize the script.
     */
    public <T> void addScript(KClass<? extends BotScript<T>> type, ScriptSupplier<T> newScript) {
        scriptMap.put(type.getQualifiedName(), newScript);
    }

    /**
     * Loads a {@link BotScript} from its registered constructor using its class name.
     * <p>
     * Typically invoked by {@link BotScriptStack#load(List)} when reconstructing scripts from saved
     * {@link BotScriptSnapshot}s.
     *
     * @param typeName The fully qualified class name of the script.
     * @param bot The bot instance to attach the script to.
     * @param data The serialized snapshot data for the script.
     * @param <T> The type of data used by the script.
     * @return The constructed {@link BotScript}, or {@code null} if the script was not registered.
     */
    public <T> BotScript<T> loadScript(String typeName, Bot bot, T data) {
        ScriptSupplier<T> supplier = (ScriptSupplier<T>) scriptMap.get(typeName);
        if (supplier == null) {
            logger.error("Unregistered script {}.", typeName);
            return null;
        }
        return supplier.apply(bot, data);
    }
}
