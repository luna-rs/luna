package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.PlayerAppearance;
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
        PlayerAppearance appearance = mob.getAppearance();

        buf.put(appearance.get(PlayerAppearance.GENDER));
        buf.put(-1); // TODO: head icon
        buf.put(-1); // TODO: skull icon

        if (mob.getTransformId() != -1) {
            buf.putShort(-1);
            buf.putShort(mob.getTransformId());
        } else {
            encodeEquipmentValues(buf, mob); // TODO: encode equipment values

            buf.put(appearance.get(PlayerAppearance.HAIR_COLOR));
            buf.put(appearance.get(PlayerAppearance.TORSO_COLOR));
            buf.put(appearance.get(PlayerAppearance.LEG_COLOR));
            buf.put(appearance.get(PlayerAppearance.FEET_COLOR));
            buf.put(appearance.get(PlayerAppearance.SKIN_COLOR));

            // TODO: Walking, standing, running, turning animations
            buf.putShort(0x328);
            buf.putShort(0x337);
            buf.putShort(0x333);
            buf.putShort(0x334);
            buf.putShort(0x335);
            buf.putShort(0x336);
            buf.putShort(0x338);

            buf.putLong(mob.getUsernameHash());
            buf.put(mob.getCombatLevel());
            buf.putShort(0); // Skill level, used for Burthrope Games' Room iirc

            msg.put(buf.getBuffer().writerIndex(), ByteTransform.C);
            msg.putBytes(buf);

            buf.release();
        }
    }

    /**
     * Encodes values related to the equipment of the {@link Player}.
     */
    private void encodeEquipmentValues(ByteMessage buf, Player mob) {
        PlayerAppearance appearance = mob.getAppearance();

        buf.put(0);
        buf.put(0);
        buf.put(0);
        buf.put(0);

        buf.putShort(0x100 + appearance.get(PlayerAppearance.CHEST));
        buf.put(0);
        buf.putShort(0x100 + appearance.get(PlayerAppearance.ARMS));
        buf.putShort(0x100 + appearance.get(PlayerAppearance.LEGS));
        buf.putShort(0x100 + appearance.get(PlayerAppearance.HEAD));
        buf.putShort(0x100 + appearance.get(PlayerAppearance.HANDS));
        buf.putShort(0x100 + appearance.get(PlayerAppearance.FEET));

        if (appearance.isMale()) {
            buf.putShort(0x100 + appearance.get(PlayerAppearance.BEARD));
        } else {
            buf.putShort(0);
        }
    }
}
