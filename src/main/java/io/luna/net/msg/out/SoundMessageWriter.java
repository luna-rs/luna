package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that plays a synthesized sound.
 * The client interprets the byte after the sound ID as its repeat count, not volume. Volume is controlled by varp 169.
 *
 * @author lare96
 */
public final class SoundMessageWriter extends GameMessageWriter {

    /**
     * The sound identifier.
     */
    private final int id;

    /**
     * The sound repeat count; one plays the complete sound once.
     */
    private final int loops;

    /**
     * The additional delay in twenty-millisecond client cycles.
     */
    private final int delay;

    /**
     * Creates a new {@link SoundMessageWriter}.
     *
     * @param id The sound identifier.
     * @param loops The sound repeat count.
     * @param delay The sound delay in client cycles.
     */
    public SoundMessageWriter(int id, int loops, int delay) {
        this.id = id;
        this.loops = loops;
        this.delay = delay;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(26, buffer);
        msg.putShort(id);
        msg.put(loops);
        msg.putShort(delay);
        return msg;
    }
}
