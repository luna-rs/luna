package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that forces a hint icon above a {@link Position}.
 *
 * @author lare96
 */
public final class PositionHintMessageWriter extends GameMessageWriter{

    /**
     * The target of the hint icon.
     */
    private final Position target;
    private final int height;

    /**
     * Creates a new {@link PositionHintMessageWriter}.
     *
     * @param target The target of the hint icon, or null to clear it.
     */
    public PositionHintMessageWriter(Position target) {
        this(target, 0);
    }

    /**
     * Creates a position hint at the requested height above the tile.
     *
     * @param target The target tile, or null to clear the current hint.
     * @param height The unsigned client height, in four-unit increments.
     */
    public PositionHintMessageWriter(Position target, int height) {
        if (height < 0 || height > 255) throw new IllegalArgumentException("Invalid hint height.");
        this.target = target;
        this.height = height;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(199, buffer);
        msg.put(target == null ? 0 : 2);
        msg.putShort(target == null ? 0 : target.getX());
        msg.putShort(target == null ? 0 : target.getY());
        msg.put(target == null ? 0 : height);
        return msg;
    }
}
