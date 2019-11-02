package io.luna.game.model.mob.block;

import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.ModelAnimation;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerAppearance;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;

import java.util.OptionalInt;
import java.util.function.Function;

/**
 * An {@link UpdateBlock} implementation that handles the {@code APPEARANCE} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AppearanceUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link AppearanceUpdateBlock}.
     */
    public AppearanceUpdateBlock() {
        super(UpdateFlag.APPEARANCE);
    }

    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        ByteMessage buf = ByteMessage.raw();
        try {
            buf.put(player.getAppearance().get(PlayerAppearance.GENDER)); // Gender.
            buf.put(player.getPrayerIcon().getId()); // Prayer icon. // TODO fix with new client
            buf.put(player.getSkullIcon().getId()); // Skull icon. // TODO fix with new client

            // Transform the player if needed.
            OptionalInt transformId = player.getTransformId();
            if (transformId.isPresent()) {
                buf.putShort(-1);
                buf.putShort(transformId.getAsInt());
            } else {
                // Otherwise encode equipment.
                encodeEquipment(buf, player);
            }

            encodeModelColors(buf, player); // Encode model colors.
            encodeAnimations(buf, player); // Encode model animations.

            buf.putLong(player.getUsernameHash()); // Username.
            buf.put(player.getCombatLevel()); // Combat level.
            buf.putShort(0); // Skill level for Burthrope games' room.

            // Append appearance block to block set buffer.
            int currentIndex = buf.getBuffer().writerIndex();
            msg.put(currentIndex, ValueType.NEGATE);
            msg.putBytes(buf);
        } finally {
            buf.release();
        }
    }

    @Override
    public int getPlayerMask() {
        return 16;
    }

    /**
     * Encodes values related to equipment.
     *
     * @param buf The update block buffer.
     * @param player The player.
     */
    @SuppressWarnings("ConstantConditions")
    private void encodeEquipment(ByteMessage buf, Player player) {
        Equipment equipment = player.getEquipment();
        PlayerAppearance appearance = player.getAppearance();

        buf.putShort(512 + getId(equipment, Equipment.HEAD)); // Helmet model.
        buf.putShort(512 + getId(equipment, Equipment.CAPE)); // Cape model.
        buf.putShort(512 + getId(equipment, Equipment.AMULET)); // Amulet model.
        buf.putShort(512 + getId(equipment, Equipment.WEAPON)); // Weapon model.

        // Chest model.
        if (equipment.occupied(Equipment.CHEST)) {
            buf.putShort(512 + equipment.get(Equipment.CHEST).getId());
        } else {
            buf.putShort(256 + appearance.get(PlayerAppearance.CHEST));
        }

        buf.putShort(512 + getId(equipment, Equipment.SHIELD)); // Shield model.

        // Arms model.
        boolean isFullBody = getDef(equipment, Equipment.CHEST, EquipmentDefinition::isFullBody);
        if (isFullBody) {
            buf.put(0);
        } else {
            buf.putShort(256 + appearance.get(PlayerAppearance.ARMS));
        }

        // Legs model.
        if (equipment.occupied(Equipment.LEGS)) {
            buf.putShort(512 + equipment.computeIdForIndex(Equipment.LEGS).getAsInt());
        } else {
            buf.putShort(256 + appearance.get(PlayerAppearance.LEGS));
        }

        // Head model.
        boolean isFullHelmet = getDef(equipment, Equipment.HEAD, EquipmentDefinition::isFullHelmet);
        if (isFullHelmet) {
            buf.put(0);
        } else {
            buf.putShort(256 + appearance.get(PlayerAppearance.HEAD));
        }

        // Hands model.
        if (equipment.occupied(Equipment.HANDS)) {
            buf.putShort(512 + equipment.computeIdForIndex(Equipment.HANDS).getAsInt());
        } else {
            buf.putShort(256 + appearance.get(PlayerAppearance.HANDS));
        }

        // Feet model.
        if (equipment.occupied(Equipment.FEET)) {
            buf.putShort(512 + equipment.computeIdForIndex(Equipment.FEET).getAsInt());
        } else {
            buf.putShort(256 + appearance.get(PlayerAppearance.FEET));
        }

        // Beard model.
        if (appearance.isFemale() || isFullHelmet) {
            buf.put(0);
        } else {
            buf.putShort(256 + appearance.get(PlayerAppearance.BEARD));
        }
    }

    /**
     * Encodes values related to model colors.
     *
     * @param buf The update block buffer.
     * @param player The player.
     */
    private void encodeModelColors(ByteMessage buf, Player player) {
        PlayerAppearance appearance = player.getAppearance();
        buf.put(appearance.get(PlayerAppearance.HAIR_COLOR));
        buf.put(appearance.get(PlayerAppearance.TORSO_COLOR));
        buf.put(appearance.get(PlayerAppearance.LEG_COLOR));
        buf.put(appearance.get(PlayerAppearance.FEET_COLOR));
        buf.put(appearance.get(PlayerAppearance.SKIN_COLOR));
    }

    /**
     * Encodes values related to model animations.
     *
     * @param buf The update block buffer.
     * @param player The player.
     */
    private void encodeAnimations(ByteMessage buf, Player player) {
        ModelAnimation model = player.getModelAnimation();
        buf.putShort(model.getStandingId());
        buf.putShort(model.getStandingTurnId());
        buf.putShort(model.getWalkingId());
        buf.putShort(model.getTurning180DegreesId());
        buf.putShort(model.getTurning90DegreesCwId());
        buf.putShort(model.getTurning90DegreesCcwId());
        buf.putShort(model.getRunningId());
    }

    /**
     * Returns either the item identifier on {@code index} or {@code 0}.
     *
     * @param equipment The equipment.
     * @param index The index.
     * @return The item identifier or {@code 0}.
     */
    private int getId(Equipment equipment, int index) {
        return equipment.computeIdForIndex(index).orElse(0);
    }

    /**
     * Returns either the result of {@code defFunc} or {@code false}.
     *
     * @param equipment The equipment.
     * @param index The index.
     * @param defFunc The equipment definition function.
     * @return The result of the function or {@code false}.
     */
    private boolean getDef(Equipment equipment, int index, Function<EquipmentDefinition, Boolean> defFunc) {
        return equipment.nonNullGet(index).map(Item::getEquipDef).map(defFunc).orElse(false);
    }
}
