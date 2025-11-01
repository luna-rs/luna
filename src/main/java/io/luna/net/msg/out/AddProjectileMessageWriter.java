package io.luna.net.msg.out;

import io.luna.game.model.LocalProjectile;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that displays a {@link LocalProjectile}.
 *
 * @author lare96
 */
public final class AddProjectileMessageWriter extends GameMessageWriter implements ChunkUpdatableMessage {

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The offset.
     */
    private final int offset;

    /**
     * The {@code x} delta.
     */
    private final int deltaX;

    /**
     * The {@code y} delta.
     */
    private final int deltaY;

    /**
     * The target index of a {@link Mob}.
     */
    private final int targetIndex;

    /**
     * The start height.
     */
    private final int startHeight;

    /**
     * The end height.
     */
    private final int endHeight;

    /**
     * The delay.
     */
    private final int delay;

    /**
     * The speed.
     */
    private final int speed;

    /**
     * The initial slope.
     */
    private final int startSlope;

    /**
     * The distance from the source.
     */
    private final int startDistance;

    /**
     * Creates a new {@link LocalProjectile}.
     *
     * @param id The identifier.
     * @param offset The offset.
     * @param deltaX The {@code x} delta.
     * @param deltaY The {@code y} delta.
     * @param targetIndex The target index of a {@link Mob}.
     * @param startHeight The start height.
     * @param endHeight The end height.
     * @param delay The delay.
     * @param speed The speed.
     * @param startSlope The initial slope.
     * @param startDistance The distance from the source.
     */
    public AddProjectileMessageWriter(int id, int offset, int deltaX, int deltaY, int targetIndex, int startHeight,
                                      int endHeight, int delay, int speed, int startSlope, int startDistance) {
        this.id = id;
        this.offset = offset;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.targetIndex = targetIndex;
        this.startHeight = startHeight;
        this.endHeight = endHeight;
        this.delay = delay;
        this.speed = speed;
        this.startSlope = startSlope;
        this.startDistance = startDistance;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        var msg = ByteMessage.message(181, buffer);
        msg.put(offset);
        msg.put(deltaX);
        msg.put(deltaY);
        msg.putShort(targetIndex);
        msg.putShort(id);
        msg.put(startHeight);
        msg.put(endHeight);
        msg.putShort(delay);
        msg.putShort(speed);
        msg.put(startSlope);
        msg.put(startDistance);
        return msg;
    }
}
