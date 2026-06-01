package io.luna.game.persistence;

import io.luna.Luna;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.ReflectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * Resolves and exposes the active {@link GameSerializer}.
 * <p>
 * The serializer is selected from the game serializer setting in {@code luna.jsonc}. The configured value is treated as
 * a class name inside the {@code io.luna.game.persistence} package and is instantiated reflectively.
 * <p>
 * If the configured class is not a valid {@link GameSerializer}, this manager falls back to
 * {@link PassiveGameSerializer}. The resolved serializer is then reused for player and bot persistence operations.
 *
 * @author lare96
 */
public final class GameSerializerManager {

    /**
     * The logger for serializer startup and fallback messages.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Supplies the fallback serializer used when the configured serializer cannot be used.
     */
    private static final Supplier<GameSerializer> DEFAULT = PassiveGameSerializer::new;

    /**
     * The serializer selected for this server runtime.
     */
    private final GameSerializer serializer;

    /**
     * Creates a new {@link GameSerializerManager} and resolves the configured serializer.
     */
    public GameSerializerManager() {
        serializer = computeSerializer();
    }

    /**
     * Resolves the serializer configured in {@code luna.json}.
     * <p>
     * The configured serializer name is prefixed with {@code io.luna.game.persistence.} and instantiated through
     * reflection. If the resolved type is not a {@link GameSerializer}, a {@link PassiveGameSerializer} is used instead.
     *
     * @return The resolved serializer.
     *
     * @throws ClassCastException If serializer construction fails with a cast error before fallback is applied.
     */
    private GameSerializer computeSerializer() throws ClassCastException {
        String name = Luna.settings().game().serializer();
        try {
            String fullName = "io.luna.game.persistence." + name;
            return ReflectionUtils.newInstanceOf(fullName, type -> {
                try {
                    return type.getDeclaredConstructor();
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (ClassCastException e) {
            logger.fatal("{} not an instance of GameSerializer, using PassiveGameSerializer instead.", name, e);
            return DEFAULT.get();
        }
    }

    /**
     * Returns whether the active serializer saves data as JSON.
     *
     * @return {@code true} if the active serializer is a {@link JsonGameSerializer}.
     */
    public boolean isJson() {
        return serializer instanceof JsonGameSerializer;
    }

    /**
     * Returns whether the active serializer saves data through SQL.
     *
     * @return {@code true} if the active serializer is a {@link SqlGameSerializer}.
     */
    public boolean isSql() {
        return serializer instanceof SqlGameSerializer;
    }

    /**
     * Returns whether the active serializer is passive.
     *
     * @return {@code true} if the active serializer is a {@link PassiveGameSerializer}.
     */
    public boolean isPassive() {
        return serializer instanceof PassiveGameSerializer;
    }

    /**
     * Returns the active serializer.
     *
     * @return The serializer selected for this server runtime.
     * @throws NullPointerException If the serializer was not resolved during construction.
     */
    public GameSerializer getSerializer() {
        if (serializer == null) {
            throw new NullPointerException("Serializer was not properly computed!");
        }
        return serializer;
    }
}