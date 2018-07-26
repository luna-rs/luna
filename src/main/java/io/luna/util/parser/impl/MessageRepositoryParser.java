package io.luna.util.parser.impl;

import com.google.gson.JsonObject;
import fj.P;
import fj.P3;
import io.luna.net.msg.MessageRepository;
import io.luna.util.parser.GsonParser;

import java.util.List;

/**
 * A {@link GsonParser} implementation that parses incoming packet data.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageRepositoryParser extends GsonParser<P3<Integer, Integer, String>> {

    /**
     * The message repository.
     */
    private final MessageRepository messageRepository;

    /**
     * Creates a new {@link MessageRepositoryParser}.
     *
     * @param messageRepository The message repository.
     */
    public MessageRepositoryParser(MessageRepository messageRepository) {
        super("./data/io/message_repo.json");
        this.messageRepository = messageRepository;
    }

    @Override
    public P3<Integer, Integer, String> readObject(JsonObject reader) throws Exception {
        int opcode = reader.get("opcode").getAsInt();
        int size = reader.get("size").getAsInt();
        String payload = reader.has("payload") ? reader.get("payload").getAsString() : null;
        return P.p(opcode, size, payload);
    }

    @Override
    public void onReadComplete(List<P3<Integer, Integer, String>> readObjects) throws Exception {
        for (P3<Integer, Integer, String> it : readObjects) {
            messageRepository.addHandler(it._1(), it._2(), it._3());
        }
    }
}