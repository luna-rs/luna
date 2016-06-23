package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.MessageWriter;

/**
 * An {@link MessageWriter} implementation that plays a sound.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SoundMessageWriter extends MessageWriter {

    /**
     * The identifier for the sound.
     */
    private final int id;

    /**
     * How many times the sound should loop (?).
     */
    private final int loops;

    /**
     * The amount of client ticks before the sound should play.
     */
    private final int delay;

    /**
     * Creates a new {@link SoundMessageWriter}.
     *
     * @param id The identifier for the sound.
     * @param loops How many times the sound should loop (?).
     * @param delay The amount of client ticks before the sound should play.
     */
    public SoundMessageWriter(int id, int loops, int delay) {
        this.id = id;
        this.loops = loops;
        this.delay = delay;
    }

    /**
     * Creates a new {@link SoundMessageWriter} with no loops or delay.
     *
     * @param id The identifier for the sound.
     */
    public SoundMessageWriter(int id) {
        this(id, 0, 0);
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(174);
        msg.putShort(id);
        msg.put(loops);
        msg.putShort(delay);
        return msg;
    }
}
