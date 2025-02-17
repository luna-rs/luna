package io.luna.game.persistence;

import io.luna.Luna;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;
import io.luna.util.ReflectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.function.Supplier;

/**
 * A model that creates and manages a {@link GameSerializer}, used to load and save important data related to
 * {@link Player} and {@link Bot} types.
 *
 * @author lare96
 */
public class GameSerializerManager {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The default game serializer that will be used if one cannot be computed.
     */
    private static final Supplier<GameSerializer> DEFAULT = PassiveGameSerializer::new;

    /**
     * The world instance.
     */
    private final World world;

    /**
     * The computed serializer.
     */
    private final GameSerializer serializer;

    /**
     * Creates a new {@link GameSerializerManager}.
     *
     * @param world The world instance.
     */
    public GameSerializerManager(World world) {
        this.world = world;
        serializer = computeSerializer();
    }

    /**
     * Initializes a new serializer based on data within {@code luna.json}.
     *
     * @return The serializer.
     * @throws ClassCastException If the serializer could not be created.
     */
    private GameSerializer computeSerializer() throws ClassCastException {
        String name = Luna.settings().game().serializer();
        try {
            String fullName = "io.luna.game.model.mob.persistence." + name;
            return ReflectionUtils.newInstanceOf(fullName, type -> {
                try {
                    return type.getDeclaredConstructor();
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (ClassCastException e) {
            logger.fatal(new ParameterizedMessage("{} not an instance of GameSerializer, using PassiveGameSerializer instead.", name), e);
            return DEFAULT.get();
        }
    }

    public GameSerializer getSerializer() {
        if(serializer == null) {
            throw new NullPointerException("Serializer was not properly computed!");
        }
        return serializer;
    }
}
