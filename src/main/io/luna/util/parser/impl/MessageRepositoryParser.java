package io.luna.util.parser.impl;

import com.google.common.collect.Range;
import io.luna.net.msg.MessageRepository;
import io.luna.util.parser.NewLineSplitParser;

/**
 * A {@link NewLineSplitParser} implementation that parses data that will later be contained within a {@link
 * MessageRepository}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MessageRepositoryParser extends NewLineSplitParser {

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
        super("./data/io/message_repository.txt");
        this.messageRepository = messageRepository;
    }

    @Override
    public void readTokens(String[] tokens) throws Exception {
        tokens[0] = tokens[0].replace("message ", "");

        int opcode = Integer.parseInt(tokens[0]);
        int size = Integer.parseInt(tokens[1]);
        String inboundMessageName = "ReceiveGenericMessage";

        if (tokens.length == 3) {
            inboundMessageName = tokens[2];
        }

        messageRepository.addInboundMessage(opcode, size, inboundMessageName);
    }

    @Override
    public Range<Integer> tokenLength() {
        return Range.closed(2, 3);
    }

    @Override
    public String regex() {
        return ":";
    }
}
