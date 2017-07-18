package io.luna.game.model.mobile.update;

import io.luna.game.model.item.Equipment;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.Player.PrayerIcon;
import io.luna.game.model.mobile.Player.SkullIcon;
import io.luna.game.model.mobile.PlayerAppearance;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;

import java.util.OptionalInt;

/**
 * A {@link PlayerUpdateBlock} implementation that handles the {@code APPEARANCE} update block.
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

        int gender = appearance.get(PlayerAppearance.GENDER);
        PrayerIcon prayer = mob.getPrayerIcon();
        SkullIcon skull = mob.getSkullIcon();

        buf.put(gender);
        buf.put(prayer.getId());
        buf.put(skull.getId());

        OptionalInt transformId = mob.getTransformId();
        if (transformId.isPresent()) {
            buf.putShort(-1);
            buf.putShort(transformId.getAsInt());
        } else {
            encodeModels(buf, mob);
        }
        encodeModelColors(buf, mob);
        encodeAnimations(buf, mob);

        buf.putLong(mob.getUsernameHash());
        buf.put(mob.getCombatLevel());
        buf.putShort(0); /* Skill level, used for Burthrope games' room. */

        int currentIndex = buf.getBuffer().writerIndex();
        msg.put(currentIndex, ByteTransform.C);
        msg.putBytes(buf);
        buf.release();
    }

    /**
     * Encodes values related to the model.
     */
    @SuppressWarnings("ConstantConditions")
    private void encodeModels(ByteMessage buf, Player mob) {
        Equipment equipment = mob.getEquipment();
        PlayerAppearance appearance = mob.getAppearance();

        buf.putShort(0x200 + equipment.computeIdForIndex(Equipment.HEAD).orElse(0)); // Helmet model.
        buf.putShort(0x200 + equipment.computeIdForIndex(Equipment.CAPE).orElse(0)); // Cape model.
        buf.putShort(0x200 + equipment.computeIdForIndex(Equipment.AMULET).orElse(0)); // Amulet model.
        buf.putShort(0x200 + equipment.computeIdForIndex(Equipment.WEAPON).orElse(0)); // Weapon model.

        if (equipment.occupied(Equipment.CHEST)) { // Chest model.
            buf.putShort(0x200 + equipment.get(Equipment.CHEST).getId());
        } else {
            buf.putShort(0x100 + appearance.get(PlayerAppearance.CHEST));
        }

        buf.putShort(0x200 + equipment.computeIdForIndex(Equipment.SHIELD).orElse(0)); // Shield model.

        boolean isFullBody = equipment.retrieve(Equipment.CHEST).map(it -> it.getEquipDef().isFullBody())
            .orElse(false);
        if (isFullBody) { // Arms model.
            buf.put(0);
        } else {
            buf.putShort(0x100 + appearance.get(PlayerAppearance.ARMS));
        }

        if (equipment.occupied(Equipment.LEGS)) { // Legs model.
            buf.putShort(0x200 + equipment.computeIdForIndex(Equipment.LEGS).get());
        } else {
            buf.putShort(0x100 + appearance.get(PlayerAppearance.LEGS));
        }

        boolean isFullHelmet = equipment.retrieve(Equipment.HEAD).map(it -> it.getEquipDef().isFullHelmet()).
            orElse(false);
        if (isFullHelmet) { // Head model.
            buf.put(0);
        } else {
            buf.putShort(0x100 + appearance.get(PlayerAppearance.HEAD));
        }

        if (equipment.occupied(Equipment.HANDS)) { // Hands model.
            buf.putShort(0x200 + equipment.computeIdForIndex(Equipment.HANDS).get());
        } else {
            buf.putShort(0x100 + appearance.get(PlayerAppearance.HANDS));
        }

        if (equipment.occupied(Equipment.FEET)) { // Feet model.
            buf.putShort(0x200 + equipment.computeIdForIndex(Equipment.FEET).get());
        } else {
            buf.putShort(0x100 + appearance.get(PlayerAppearance.FEET));
        }

        if (appearance.isFemale() || isFullHelmet) { // Beard model.
            buf.put(0);
        } else {
            buf.putShort(0x100 + appearance.get(PlayerAppearance.BEARD));
        }
    }

    /**
     * Encodes values related to the model colors of the {@link Player}.
     */
    private void encodeModelColors(ByteMessage buf, Player mob) {
        PlayerAppearance appearance = mob.getAppearance();

        buf.put(appearance.get(PlayerAppearance.HAIR_COLOR));
        buf.put(appearance.get(PlayerAppearance.TORSO_COLOR));
        buf.put(appearance.get(PlayerAppearance.LEG_COLOR));
        buf.put(appearance.get(PlayerAppearance.FEET_COLOR));
        buf.put(appearance.get(PlayerAppearance.SKIN_COLOR));
    }

    /**
     * Encodes values related to the animations of the {@link Player}.
     */
    private void encodeAnimations(ByteMessage buf, Player mob) {
        /* TODO configurable animations for weapons and other effects */
        buf.putShort(0x328);
        buf.putShort(0x337);
        buf.putShort(0x333);
        buf.putShort(0x334);
        buf.putShort(0x335);
        buf.putShort(0x336);
        buf.putShort(0x338);
    }
}
