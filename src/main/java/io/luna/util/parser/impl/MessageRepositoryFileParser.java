package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.GameMessageRepository;
import io.luna.util.parser.AbstractJsonFileParser;

import java.lang.reflect.Field;

/**
 * A {@link AbstractJsonFileParser} implementation that parses incoming message listener metadata.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageRepositoryFileParser extends AbstractJsonFileParser<GameMessageReader> {

    /**
     * A default implementation of a {@link GameMessageReader}. It does nothing.
     */
    private static final class DefaultMessageReader extends GameMessageReader {

        @Override
        public Event read(Player player, GameMessage msg) throws Exception {
            return null;
        }
    }

    /**
     * The directory of incoming message listeners.
     */
    private static final String DIR = "io.luna.net.msg.in.";

    /**
     * The message repository.
     */
    private final GameMessageRepository repository;

    /**
     * Creates a new {@link MessageRepositoryFileParser}.
     *
     * @param repository The message repository.
     */
    public MessageRepositoryFileParser(GameMessageRepository repository) {
        super("./data/io/message_repo.json");
        this.repository = repository;
    }

    @Override
    public GameMessageReader convert(JsonObject token) throws Exception {
        int opcode = token.get("opcode").getAsInt();
        int size = token.get("size").getAsInt();
        String className = token.has("payload") ? token.get("payload").getAsString() : null;
        return createReader(opcode, size, className);
    }

    @Override
    public void onCompleted(ImmutableList<GameMessageReader> tokenObjects) throws Exception {
        tokenObjects.forEach(repository::put);
        repository.lock();
    }

    /**
     * Creates a new {@link GameMessageReader} using reflection.
     *
     * @param opcode The opcode.
     * @param size The size.
     * @param className The simple class name.
     * @return The message listener instance.
     * @throws ReflectiveOperationException If any errors occur while creating the listener instance.
     */
    private GameMessageReader createReader(int opcode, int size, String className)
            throws ReflectiveOperationException {

        // Create class and instance from qualified name.
        Object readerInstance = className != null ?
                Class.forName(DIR + className).getDeclaredConstructor().newInstance() : new DefaultMessageReader();

        // Retrieve opcode and size fields.
        Class<?> readerClass = readerInstance.getClass().getSuperclass();
        Field opcodeField = readerClass.getDeclaredField("opcode");
        Field sizeField = readerClass.getDeclaredField("size");

        // Make them accessible.
        opcodeField.setAccessible(true);
        sizeField.setAccessible(true);

        // Reflectively set the values.
        opcodeField.setInt(readerInstance, opcode);
        sizeField.setInt(readerInstance, size);

        return (GameMessageReader) readerInstance;
    }
}
