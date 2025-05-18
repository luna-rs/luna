package io.luna.net.msg.out;

import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} that forces a hint icon above a {@link Mob}.
 *
 * @author lare96
 */
public final class MobHintMessageWriter extends GameMessageWriter {

    /**
     * The target of the hint icon.
     */
    private final Mob target;

    /**
     * Creates a new {@link MobHintMessageWriter}.
     *
     * @param target The target of the hint icon.
     */
    public MobHintMessageWriter(Mob target) {
        this.target = target;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(199);
        msg.put(target instanceof Player ? 10 : 1);
        msg.putShort(target.getIndex());

        msg.put(0); // Dummy data.
        msg.putShort(0);
        return msg;
    }
}
