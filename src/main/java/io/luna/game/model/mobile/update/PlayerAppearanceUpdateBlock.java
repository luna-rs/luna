package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link PlayerUpdateBlock} implementation that handles the updating of the appearance of {@link Player}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerAppearanceUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerAppearanceUpdateBlock}.
     */
    public PlayerAppearanceUpdateBlock() {
        super(0x10, UpdateFlag.APPEARANCE);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        ByteMessage buf = ByteMessage.message();

        // TODO: Complete this after NPC update blocks are done

        // Armor and player model
        buf.put(0);
        buf.put(-1);
        buf.put(-1);
        buf.put(0);
        buf.put(0);
        buf.put(0);
        buf.put(0);
        buf.putShort(0x100 + 18);
        buf.put(0);
        buf.putShort(0x100 + 26);
        buf.putShort(0x100 + 36);
        buf.putShort(0x100 + 0);
        buf.putShort(0x100 + 33);
        buf.putShort(0x100 + 42);
        buf.putShort(0x100 + 10);

        // Player model colors
        buf.put(7);
        buf.put(8);
        buf.put(9);
        buf.put(5);
        buf.put(0);

        // Walking, standing, running, turning animations
        buf.putShort(0x328);
        buf.putShort(0x337);
        buf.putShort(0x333);
        buf.putShort(0x334);
        buf.putShort(0x335);
        buf.putShort(0x336);
        buf.putShort(0x338);

        // Username, combat level
        buf.putLong(mob.getUsernameHash());
        buf.put(126);
        buf.putShort(0);

        msg.put(buf.getBuffer().writerIndex(), ByteTransform.C);
        msg.putBytes(buf);

        buf.release();
    }
}
