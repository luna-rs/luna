package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that makes the user's screen shake. Primarily used for cutscenes, but
 * could also be used for boss fights, etc.
 *
 * @author lare96
 */
public final class CameraShakeMessageWriter extends GameMessageWriter {

    /**
     * The camera identifier.
     */
    private final int cameraId;

    /**
     * The jitter value.
     */
    private final int jitter;

    /**
     * The amplitude value.
     */
    private final int amplitude;

    /**
     * The frequency value.
     */
    private final int frequency;

    /**
     * Creates a new {@link CameraShakeMessageWriter}.
     *
     * @param cameraId The camera identifier.
     * @param jitter The jitter value.
     * @param amplitude The amplitude value.
     * @param frequency The frequency value.
     */
    public CameraShakeMessageWriter(int cameraId, int jitter, int amplitude, int frequency) {
        this.cameraId = cameraId;
        this.jitter = jitter;
        this.amplitude = amplitude;
        this.frequency = frequency;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(35);
        msg.put(cameraId);
        msg.put(jitter);
        msg.put(amplitude);
        msg.put(frequency);
        return msg;
    }
}
