package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.MessageWriter;

/**
 * An {@link MessageWriter} implementation that will close all open interfaces.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class CloseWindowsMessageWriter extends MessageWriter {

    @Override
    public ByteMessage write(Player player) {
        return ByteMessage.message(219);
    }
}
