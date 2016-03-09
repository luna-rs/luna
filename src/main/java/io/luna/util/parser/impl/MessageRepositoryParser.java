package io.luna.util.parser.impl;

import com.google.gson.JsonObject;
import io.luna.net.msg.MessageRepository;
import io.luna.util.parser.TomlParser;
import io.luna.util.parser.impl.MessageRepositoryParser.MessageRepositoryElement;

import java.util.List;

/**
 * A {@link TomlParser} implementation that parses data that will later be contained within a {@link MessageRepository}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageRepositoryParser extends TomlParser<MessageRepositoryElement> {

    /**
     * The {@link MessageRepository} that the data will be added to.
     */
    private final MessageRepository messageRepository;

    /**
     * Creates a new {@link MessageRepositoryParser}.
     *
     * @param messageRepository The {@link MessageRepository} that the data will be added to.
     */
    public MessageRepositoryParser(MessageRepository messageRepository) {
        super("./data/io/message_repository.toml");
        this.messageRepository = messageRepository;
    }

    @Override
    public MessageRepositoryElement readObject(JsonObject reader) throws Exception {
        return new MessageRepositoryElement(reader.get("opcode").getAsInt(), reader.get("size").getAsInt(),
            reader.get("payload").getAsString());
    }

    @Override
    public void onReadComplete(List<MessageRepositoryElement> readObjects) throws Exception {
        for (MessageRepositoryElement it : readObjects) {
            messageRepository.addHandler(it.opcode, it.size, it.payload);
        }
    }

    @Override
    public String table() {
        return "message";
    }

    /**
     * A POJO representing a single read object.
     */
    protected final class MessageRepositoryElement {

        /**
         * The opcode.
         */
        private final int opcode;

        /**
         * The size.
         */
        private final int size;

        /**
         * The payload.
         */
        private final String payload;

        /**
         * Creates a new {@link MessageRepositoryElement}.
         *
         * @param opcode The opcode.
         * @param size The size.
         * @param payload The payload.
         */
        private MessageRepositoryElement(int opcode, int size, String payload) {
            this.opcode = opcode;
            this.size = size;
            this.payload = payload;
        }
    }
}
