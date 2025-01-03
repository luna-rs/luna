package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that plays a sound.
 *
 * @author lare96
 */
public final class SoundMessageWriter extends GameMessageWriter {

    /**
     * The sound identifier.
     */
    private final int id;

    /**
     * The sound volume.
     */
    private final int volume;

    /**
     * The sound delay.
     */
    private final int delay;

    /**
     * Creates a new {@link SoundMessageWriter}.
     *
     * @param id The sound identifier.
     * @param volume The sound volume.
     * @param delay The sound delay.
     */
    public SoundMessageWriter(int id, int volume, int delay) {
        this.id = id;
        this.volume = volume;
        this.delay = delay;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(26);
        msg.putShort(id);
        msg.put(volume);
        msg.putShort(delay);
        return msg;
    }
}
