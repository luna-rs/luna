package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * Moves the 377 cutscene camera or changes its look-at target using the current scene's local tile coordinates.
 * The native client decodes move as opcode 3 and look-at as opcode 167, each with the same six-byte payload.
 *
 * @author lare96
 */
public final class CameraTargetMessageWriter extends GameMessageWriter {
    private final Position target;
    private final int height;
    private final int speed;
    private final int acceleration;
    private final boolean lookAt;

    /**
     * Creates a native camera target, with height above the target terrain and byte-sized movement parameters.
     *
     * @param target The world tile translated against the player's loaded scene when written.
     * @param height Height above the terrain, from 0 to 65535.
     * @param speed Fixed movement increment, from 0 to 255.
     * @param acceleration Proportional movement increment, from 0 to 255; at least 100 moves immediately.
     * @param lookAt Whether to aim at the target rather than move the camera there.
     */
    public CameraTargetMessageWriter(Position target, int height, int speed, int acceleration, boolean lookAt) {
        if (height < 0 || height > 65535 || speed < 0 || speed > 255 || acceleration < 0 || acceleration > 255) {
            throw new IllegalArgumentException("Invalid camera target parameters.");
        }
        this.target = target;
        this.height = height;
        this.speed = speed;
        this.acceleration = acceleration;
        this.lookAt = lookAt;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage message = ByteMessage.message(lookAt ? 167 : 3, buffer);
        message.put(target.getLocalX(player.getLastRegion()));
        message.put(target.getLocalY(player.getLastRegion()));
        message.putShort(height);
        message.put(speed);
        message.put(acceleration);
        return message;
    }
}
