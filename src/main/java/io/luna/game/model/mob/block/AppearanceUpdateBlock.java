package io.luna.game.model.mob.block;

import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link UpdateBlock} implementation that encodes the {@link UpdateFlag#APPEARANCE} block for a player.
 * <p>
 * The appearance block describes how the client should render a player model. It includes:
 * <ul>
 *     <li>Gender and head icons (skull, prayer)</li>
 *     <li>Either an NPC transform ID, or the full equipment and body part model IDs.</li>
 *     <li>Body color selections (hair, torso, legs, feet, skin).</li>
 *     <li>Model animation identifiers (stand, walk, run, etc).</li>
 *     <li>Display name, combat level, and total skill level.</li>
 * </ul>
 * The block is first serialized into a temporary {@link ByteMessage} so that the final size can be prefixed and
 * written into the outer update buffer in reverse order, following the RuneScape update protocol specification.
 *
 * @author lare96
 */
public final class AppearanceUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link AppearanceUpdateBlock} bound to the {@link UpdateFlag#APPEARANCE}
     * update flag.
     */
    public AppearanceUpdateBlock() {
        super(UpdateFlag.APPEARANCE);
    }

    @Override
    public void encodeForPlayer(ByteMessage msg, UpdateBlockData data) {
        ByteMessage buf = ByteMessage.raw();
        try {
            // Gender and head icons.
            int gender = data.appearance[PlayerAppearance.GENDER];
            buf.put(gender);
            buf.put(data.skull.getId());
            buf.put(data.prayer.getId());

            // Either encode an NPC transform or full equipment/body models.
            if (data.transform > -1) {
                buf.put(255);
                buf.put(255);
                buf.putShort(data.transform);
            } else {
                encodeEquipment(buf, data, gender);
            }

            // Body colors and animation definitions.
            encodeModelColors(buf, data.appearance);
            encodeAnimations(buf, data.model);

            // Identity and levels.
            buf.putLong(data.username);
            buf.put(data.combat);
            buf.putShort(data.skill);

            // Copy the serialized block into the main update buffer.
            // Note: writerIndex is equivalent to the encoded length when the buffer
            // has not been read from.
            int blockLength = buf.getBuffer().writerIndex();
            byte[] rawBytes = new byte[buf.getBuffer().readableBytes()];
            buf.getBuffer().getBytes(0, rawBytes);

            msg.put(blockLength);
            msg.putBytesReverse(rawBytes);
        } finally {
            buf.release();
        }
    }

    @Override
    public int getPlayerMask() {
        return 0x4;
    }

    /**
     * Encodes all equipment and base body-part model identifiers into the appearance buffer.
     * <p>
     * For each render slot (head, cape, amulet, weapon, chest, shield, arms, legs, head model, hands, boots,
     * beard) this method either:
     * <ul>
     *     <li>Writes a model derived from equipped items (offset by {@code 0x200}), or</li>
     *     <li>Falls back to a base appearance model (offset by {@code 0x100}), or</li>
     *     <li>Writes {@code 0} when the slot should be hidden (e.g. full helmet, full body).</li>
     * </ul>
     *
     * @param buf      The temporary appearance buffer to write to.
     * @param data     The snapshot of update data containing equipment and appearance arrays.
     * @param gender   The gender value from {@link PlayerAppearance} used for beard handling.
     */
    private void encodeEquipment(ByteMessage buf, UpdateBlockData data, int gender) {
        Item[] equipment = data.equipment;
        int[] appearance = data.appearance;

        // Helmet model.
        Item headItem = equipment[Equipment.HEAD];
        if (headItem != null) {
            buf.putShort(0x200 + headItem.getId());
        } else {
            buf.put(0);
        }

        // Cape model.
        Item capeItem = equipment[Equipment.CAPE];
        if (capeItem != null) {
            buf.putShort(0x200 + capeItem.getId());
        } else {
            buf.put(0);
        }

        // Amulet model.
        Item amuletItem = equipment[Equipment.AMULET];
        if (amuletItem != null) {
            buf.putShort(0x200 + amuletItem.getId());
        } else {
            buf.put(0);
        }

        // Weapon model.
        Item weaponItem = equipment[Equipment.WEAPON];
        if (weaponItem != null) {
            buf.putShort(0x200 + weaponItem.getId());
        } else {
            buf.put(0);
        }

        // Chest model (falls back to base torso if no item).
        Item chestItem = equipment[Equipment.CHEST];
        if (chestItem != null) {
            buf.putShort(0x200 + chestItem.getId());
        } else {
            buf.putShort(0x100 + appearance[PlayerAppearance.CHEST]);
        }

        // Shield model.
        Item shieldItem = equipment[Equipment.SHIELD];
        if (shieldItem != null) {
            buf.putShort(0x200 + shieldItem.getId());
        } else {
            buf.put(0);
        }

        // Arms model (hidden if the chestpiece is full-body).
        if (chestItem != null && chestItem.getEquipDef().isFullBody()) {
            buf.put(0);
        } else {
            buf.putShort(0x100 + appearance[PlayerAppearance.ARMS]);
        }

        // Legs model.
        Item legsItem = equipment[Equipment.LEGS];
        if (legsItem != null) {
            buf.putShort(0x200 + legsItem.getId());
        } else {
            buf.putShort(0x100 + appearance[PlayerAppearance.LEGS]);
        }

        // Head model (hidden if a full helmet is equipped).
        boolean isFullHelmet = headItem != null && headItem.getEquipDef().isFullHelmet();
        if (isFullHelmet) {
            buf.put(0);
        } else {
            buf.putShort(0x100 + appearance[PlayerAppearance.HEAD]);
        }

        // Hands model.
        Item handsItem = equipment[Equipment.HANDS];
        if (handsItem != null) {
            buf.putShort(0x200 + handsItem.getId());
        } else {
            buf.putShort(0x100 + appearance[PlayerAppearance.HANDS]);
        }

        // Boots model.
        Item bootsModel = equipment[Equipment.BOOTS];
        if (bootsModel != null) {
            buf.putShort(0x200 + bootsModel.getId());
        } else {
            buf.putShort(0x100 + appearance[PlayerAppearance.BOOTS]);
        }

        // Beard model (hidden for females and when wearing a full helmet).
        if (gender == PlayerAppearance.GENDER_FEMALE || isFullHelmet) {
            buf.put(0);
        } else {
            buf.putShort(0x100 + appearance[PlayerAppearance.BEARD]);
        }
    }

    /**
     * Encodes all appearance-related body color selections into the appearance buffer.
     * <p>
     * Each value is a palette index that the client interprets as a specific color for the associated body part.
     *
     * @param buf        The temporary appearance buffer to write to.
     * @param appearance The appearance array from {@link PlayerAppearance} containing
     *                   hair, torso, leg, feet, and skin color indices.
     */
    private void encodeModelColors(ByteMessage buf, int[] appearance) {
        buf.put(appearance[PlayerAppearance.HAIR_COLOR]);
        buf.put(appearance[PlayerAppearance.TORSO_COLOR]);
        buf.put(appearance[PlayerAppearance.LEG_COLOR]);
        buf.put(appearance[PlayerAppearance.BOOTS_COLOR]);
        buf.put(appearance[PlayerAppearance.SKIN_COLOR]);
    }

    /**
     * Encodes the base animation identifiers that drive how the player model moves.
     * <p>
     * This includes idle, walk, run, and turning animations. The client combines these definitions with the current
     * movement state to pick the correct animation to play.
     *
     * @param buf   The temporary appearance buffer to write to.
     * @param model The model animation definition used for this player.
     */
    private void encodeAnimations(ByteMessage buf, PlayerModelAnimation model) {
        buf.putShort(model.getStandingId());
        buf.putShort(model.getStandingTurnId());
        buf.putShort(model.getWalkingId());
        buf.putShort(model.getTurning180DegreesId());
        buf.putShort(model.getTurningRightId());
        buf.putShort(model.getTurningLeftId());
        buf.putShort(model.getRunningId());
    }
}
