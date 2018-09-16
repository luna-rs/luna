package io.luna.util.parser.impl;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.GameMessageRepository;
import io.luna.util.parser.GsonParser;

import java.lang.reflect.Field;
import java.util.List;

/**
 * A {@link GsonParser} implementation that parses incoming packet data.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageRepositoryParser extends GsonParser<GameMessageReader> {

    private static final class DefaultMessageReader extends GameMessageReader {

        @Override
        public Event read(Player player, GameMessage msg) throws Exception {
            return null;
        }
    }

    private static final String DIR = "io.luna.net.msg.in.";

    /**
     * The message repository.
     */
    private final GameMessageRepository repository;

    /**
     * Creates a new {@link MessageRepositoryParser}.
     *
     * @param repository The message repository.
     */
    public MessageRepositoryParser(GameMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public GameMessageReader readObject(JsonObject reader) throws Exception {
        int opcode = reader.get("opcode").getAsInt();
        int size = reader.get("size").getAsInt();
        String className = reader.has("payload") ? reader.get("payload").getAsString() : null;
        return createReader(opcode, size, className);
    }

    @Override
    public void onReadComplete(List<GameMessageReader> readObjects) throws Exception {
        for (GameMessageReader messageReader : readObjects) {
            repository.put(messageReader);
        }
        repository.lock();
    }

    @Override
    public ImmutableList<String> forFiles() {
        return ImmutableList.of("./data/io/message_repo.json");
    }

    private GameMessageReader createReader(int opcode, int size, String className) throws ReflectiveOperationException {

        // Create class and instance from qualified name.
        Object readerInstance = className != null ?
                Class.forName(DIR + className).newInstance() : new DefaultMessageReader();

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