package io.luna.game.model.mob.bot;

import api.bot.action.BotActionHandler;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerAppearance;
import io.luna.game.model.object.GameObject;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.in.AmountInputMessageReader;
import io.luna.net.msg.in.ButtonClickMessageReader;
import io.luna.net.msg.in.ChatMessageReader;
import io.luna.net.msg.in.CloseInterfaceMessageReader;
import io.luna.net.msg.in.CommandMessageReader;
import io.luna.net.msg.in.ContinueDialogueMessageReader;
import io.luna.net.msg.in.DesignPlayerMessageReader;
import io.luna.net.msg.in.DropItemMessageReader;
import io.luna.net.msg.in.EquipItemMessageReader;
import io.luna.net.msg.in.GroundItemClickMessageReader;
import io.luna.net.msg.in.ItemClickMessageReader;
import io.luna.net.msg.in.ItemOnItemMessageReader;
import io.luna.net.msg.in.ItemOnNpcMessageReader;
import io.luna.net.msg.in.ItemOnObjectMessageReader;
import io.luna.net.msg.in.ItemOnPlayerMessageReader;
import io.luna.net.msg.in.NpcClickMessageReader;
import io.luna.net.msg.in.ObjectClickMessageReader;
import io.luna.net.msg.in.PlayerClickMessageReader;
import io.luna.net.msg.in.WidgetItemClickMessageReader;
import io.luna.util.StringUtils;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A collection of functions that build send game packets to this server. This class should ideally be the main
 * way a {@link Bot} performs actions.
 * <p>
 * For suspendable actions that utilize these packets, see {@link BotActionHandler}.
 *
 * @author lare96
 */
public class BotOutputMessageHandler {

    /**
     * An enum representing chat color.
     */
    public enum ChatColor {
        YELLOW(0),

        RED(1),

        GREEN(2),

        CYAN(3),

        PURPLE(4),

        WHITE(5),

        FLASH_1(6),

        FLASH_2(7),

        FLASH_3(8),

        GLOW_1(9),

        GLOW_2(10),

        GLOW_3(11);

        /**
         * The client identifier.
         */
        private final int id;

        /**
         * Creates a new {@link ChatColor}.
         *
         * @param id The client identifier.
         */
        ChatColor(int id) {
            this.id = id;
        }

        /**
         * @return The client identifier.
         */
        public int getId() {
            return id;
        }
    }

    /**
     * An enum representing chat effects.
     */
    public enum ChatEffect {
        NONE(0),
        WAVE(1),
        WAVE_2(2),
        SHAKE(3),
        SCROLL(4),
        SLIDE(5);

        /**
         * The client identifier.
         */
        private final int id;

        /**
         * Creates a new {@link ChatEffect}.
         *
         * @param id The client identifier.
         */
        ChatEffect(int id) {
            this.id = id;
        }

        /**
         * @return The client identifier.
         */
        public int getId() {
            return id;
        }
    }

    /**
     * The bot.
     */
    private final Bot bot;

    /**
     * The bot client.
     */
    private final BotClient client;

    /**
     * Creates a new {@link BotOutputMessageHandler}.
     *
     * @param client The bot client.
     */
    public BotOutputMessageHandler(BotClient client) {
        this.client = client;
        bot = client.getBot();
    }

    /**
     * Sends the {@link CloseInterfaceMessageReader} packet.
     */
    public void sendCloseInterface() {
        ByteMessage msg = ByteMessage.raw();
        client.queueSimulated(new GameMessage(110, MessageType.FIXED, msg));
    }

    /**
     * Sends the {@link DesignPlayerMessageReader} packet. Will send a random appearance.
     */
    public void sendCharacterDesignSelection() {
        sendCharacterDesignSelection(PlayerAppearance.randomValues());
    }

    /**
     * Sends the {@link DesignPlayerMessageReader} packet.
     *
     * @param appearance The appearance values to send.
     */
    public void sendCharacterDesignSelection(int[] appearance) {
        ByteMessage msg = ByteMessage.raw();
        int index = 0;
        msg.put(appearance[index++]);
        for (int loops = 0; loops < 7; loops++) {
            msg.put(appearance[index++]);
        }
        for (int loops = 0; loops < 5; loops++) {
            msg.put(appearance[index++]);
        }
        client.queueSimulated(new GameMessage(163, MessageType.FIXED, msg));
    }

    /**
     * Sends one of the {@link PlayerClickMessageReader} packets.
     *
     * @param option The interaction option, between 1-5.
     * @param targetPlayer The target player.
     * @return {@code true} if successfully sent.
     */
    public void sendPlayerInteraction(int option, Player targetPlayer) {
        checkArgument(option >= 1 && option <= 5, "[option] must be between 1 and 5.");
        int index = targetPlayer.getIndex();
        ByteMessage msg = ByteMessage.raw();
        int opcode = -1;
        switch (option) {
            case 1:
                msg.putShort(index, ByteOrder.LITTLE, ValueType.ADD);
                opcode = 245;
                break;
            case 2:
                msg.putShort(index, ValueType.ADD);
                opcode = 233;
                break;
            case 3:
                msg.putShort(index, ByteOrder.LITTLE);
                opcode = 194;
                break;
            case 4:
                msg.putShort(index, ByteOrder.LITTLE);
                opcode = 116;
                break;
            case 5:
                msg.putShort(index, ValueType.ADD);
                opcode = 45;
                break;
        }
        client.queueSimulated(new GameMessage(opcode, MessageType.FIXED, msg));
    }

    /**
     * Sends one of the {@link GroundItemClickMessageReader} packets.
     *
     * @param localItem The local ground item.
     */
    public void sendPickupItem(GroundItem localItem) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(localItem.getId(), ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(localItem.getPosition().getX(), ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(localItem.getPosition().getY(), ValueType.ADD);
        client.queueSimulated(new GameMessage(71, MessageType.FIXED, msg));
    }

    /**
     * Sends the {@link ContinueDialogueMessageReader} packet.
     */
    public void sendContinueDialogue() {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(0);
        client.queueSimulated(new GameMessage(226, MessageType.FIXED, msg));
    }

    /**
     * Sends one of the {@link ObjectClickMessageReader} packets.
     *
     * @param option The interaction option, between 1-3
     * @param localObject The target object.
     */
    public void sendObjectInteraction(int option, GameObject localObject) {
        checkArgument(option >= 1 && option <= 3, "[option] must be between 1 and 3.");
        int x = localObject.getPosition().getX();
        int y = localObject.getPosition().getY();
        int id = localObject.getId();

        ByteMessage msg = ByteMessage.raw();
        int opcode = -1;
        switch (option) {
            case 1:
                msg.putShort(x, ValueType.ADD);
                msg.putShort(y, ByteOrder.LITTLE);
                msg.putShort(id, ByteOrder.LITTLE);
                opcode = 181;
                break;
            case 2:
                msg.putShort(id);
                msg.putShort(x);
                msg.putShort(y, ValueType.ADD);
                opcode = 241;
                break;
            case 3:
                msg.putShort(x, ByteOrder.LITTLE);
                msg.putShort(y);
                msg.putShort(id, ByteOrder.LITTLE, ValueType.ADD);
                opcode = 50;
                break;
        }
        client.queueSimulated(new GameMessage(opcode, MessageType.FIXED, msg));
    }

    /**
     * Sends one of the {@link GroundItemClickMessageReader} packets.
     *
     * @param option The interaction option, between 1-2.
     * @param targetItem The target item.
     */
    public void sendGroundItemInteraction(int option, GroundItem targetItem) {
        checkArgument(option >= 1 && option <= 2, "[option] must be between 1 and 2.");
        int x = targetItem.getPosition().getX();
        int y = targetItem.getPosition().getY();
        int id = targetItem.getId();

        ByteMessage msg = ByteMessage.raw();
        int opcode = -1;
        switch (option) {
            case 1: // Pickup ground item option.
                msg.putShort(id, ByteOrder.LITTLE, ValueType.ADD);
                msg.putShort(x, ByteOrder.LITTLE, ValueType.ADD);
                msg.putShort(y, ValueType.ADD);
                opcode = 71;
                break;
            case 2:
                msg.putShort(id, ValueType.ADD);
                msg.putShort(y, ByteOrder.LITTLE);
                msg.putShort(x);
                opcode = 54;
                break;
        }
        client.queueSimulated(new GameMessage(opcode, MessageType.FIXED, msg));
    }

    /**
     * Sends one of the {@link ItemClickMessageReader} packets.
     *
     * @param option The option (1-5).
     * @param index The index on the widget.
     * @param itemId The item id.
     */
    public void sendInventoryItemClick(int option, int index, int itemId) {
        checkArgument(option >= 1 && option <= 5, "[option] must be between 1 and 5.");

        ByteMessage msg = ByteMessage.raw();
        int opcode = -1;
        switch (option) {
            case 1:
                msg.putShort(3214, ValueType.ADD);
                msg.putShort(index, ByteOrder.LITTLE);
                msg.putShort(itemId, ByteOrder.LITTLE);
                opcode = 203;
                break;
            case 2:
                msg.putShort(itemId);
                msg.putShort(index, ValueType.ADD);
                msg.putShort(3214, ValueType.ADD);
                opcode = 24;
                break;
            case 3:
                msg.putShort(itemId, ValueType.ADD);
                msg.putShort(index, ByteOrder.LITTLE, ValueType.ADD);
                msg.putShort(3214, ByteOrder.LITTLE, ValueType.ADD);
                opcode = 161;
                break;
            case 4:
                msg.putShort(index, ByteOrder.LITTLE);
                msg.putShort(itemId, ValueType.ADD);
                msg.putShort(3214);
                opcode = 228;
                break;
            case 5:
                msg.putShort(itemId,ValueType.ADD);
                msg.putShort(3214);
                msg.putShort(index, ValueType.ADD);
                opcode = 4;
                break;
        }
        client.queueSimulated(new GameMessage(opcode, MessageType.FIXED, msg));
    }

    /**
     * Sends one of the {@link WidgetItemClickMessageReader} packets.
     *
     * @param option The option (1-5).
     * @param widgetIndex The index on the widget.
     * @param widgetId The widget id.
     * @param itemId The item id.
     */
    public void sendItemWidgetClick(int option, int widgetIndex, int widgetId, int itemId) {
        checkArgument(option >= 1 && option <= 5, "[option] must be between 1 and 5.");

        ByteMessage msg = ByteMessage.raw();
        int opcode = -1;
        switch (option) {
            case 1:
                msg.putShort(itemId, ValueType.ADD);
                msg.putShort(widgetId);
                msg.putShort(widgetIndex);
                opcode = 3;
                break;
            case 2:
                msg.putShort(widgetIndex, ValueType.ADD);
                msg.putShort(itemId, ByteOrder.LITTLE);
                msg.putShort(widgetId, ByteOrder.LITTLE);
                opcode = 177;
                break;
            case 3:
                msg.putShort(itemId, ByteOrder.LITTLE);
                msg.putShort(widgetIndex, ByteOrder.LITTLE, ValueType.ADD);
                msg.putShort(widgetId);
                opcode = 91;
                break;
            case 4:
                msg.putShort(widgetId, ByteOrder.LITTLE, ValueType.ADD);
                msg.putShort(widgetIndex, ByteOrder.LITTLE);
                msg.putShort(itemId);
                opcode = 231;
                break;
            case 5:
                msg.putShort(widgetIndex, ByteOrder.LITTLE, ValueType.ADD);
                msg.putShort(itemId, ByteOrder.LITTLE, ValueType.ADD);
                msg.putShort(widgetId, ByteOrder.LITTLE);
                opcode = 158;
                break;
        }
        client.queueSimulated(new GameMessage(opcode, MessageType.FIXED, msg));
    }

    /**
     * Sends a {@link CommandMessageReader} packet. No colons are required.
     *
     * @param commandWithArgs The command to send, as you would type it.
     */
    public void sendCommand(String commandWithArgs) {
        ByteMessage msg = ByteMessage.raw();
        msg.putString(commandWithArgs.replace("::", ""));
        client.queueSimulated(new GameMessage(56, MessageType.VAR, msg));
    }

    /**
     * Sends one of the {@link NpcClickMessageReader} packets.
     *
     * @param option The interaction option, between 1-5.
     * @param localNpc The target npc.
     * \     * @return {@code true} if successfully sent.
     */
    public void sendNpcInteraction(int option, Npc localNpc) {
        checkArgument(option >= 1 && option <= 5, "[option] must be between 1 and 5.");
        int index = localNpc.getIndex();

        ByteMessage msg = ByteMessage.raw();
        int opcode = -1;
        switch (option) {
            case 1:
                msg.putShort(index, ByteOrder.LITTLE);
                opcode = 112;
                break;
            case 2:
                msg.putShort(index, ByteOrder.LITTLE, ValueType.ADD);
                opcode = 13;
                break;
            case 3:
                msg.putShort(index, ValueType.ADD);
                opcode = 67;
                break;
            case 4:
                msg.putShort(index);
                opcode = 42;
                break;
            case 5:
                msg.putShort(index, ByteOrder.LITTLE);
                opcode = 8;
                break;
        }
        client.queueSimulated(new GameMessage(opcode, MessageType.FIXED, msg));
    }

    /**
     * Sends the {@link ChatMessageReader} packet with the default color and effect.
     *
     * @param text The text to chat.
     */
    public void chat(String text) {
        chat(text, ChatColor.YELLOW, ChatEffect.NONE);
    }

    /**
     * Sends the {@link ChatMessageReader} packet.
     *
     * @param text The text to chat.
     * @param color The text color.
     * @param effect The text effect.
     */
    public void chat(String text, ChatColor color, ChatEffect effect) {
        ByteMessage textMsg = ByteMessage.raw();
        try {
            StringUtils.packText(text, textMsg);

            ByteMessage msg = ByteMessage.raw();
            msg.put(color.id, ValueType.NEGATE);
            msg.put(effect.id, ValueType.ADD);
            msg.putBytes(textMsg);
            client.queueSimulated(new GameMessage(49, MessageType.VAR, msg));
        } finally {
            textMsg.releaseAll();
        }
    }

    /**
     * Sends a {@link ButtonClickMessageReader} packet.
     *
     * @param buttonId The button id to send.
     */
    public void clickButton(int buttonId) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(buttonId);
        client.queueSimulated(new GameMessage(79, MessageType.FIXED, msg));
    }

    /**
     * Sends a {@link ButtonClickMessageReader} packet that clicks the logout button.
     */
    public void clickLogout() {
        clickButton(2458);
    }

    /**
     * Sends the {@link DropItemMessageReader} packet.
     *
     * @param inventoryIndex The inventory index to drop.
     * @param itemId The item id.
     * @return {@code true} if successfully sent.
     */
    public void sendDropItem(int inventoryIndex, int itemId) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(inventoryIndex, ByteOrder.LITTLE);
        msg.putShort(itemId, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(3214, ByteOrder.LITTLE, ValueType.ADD);
        client.queueSimulated(new GameMessage(4, MessageType.FIXED, msg));
    }

    /**
     * Sends the {@link EquipItemMessageReader} packet.
     *
     * @param inventoryIndex The inventory slot to equip.
     * @return {@code true} if successfully sent.
     */
    public void sendEquipItem(int inventoryIndex, int itemId) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(1688, ByteOrder.LITTLE);
        msg.putShort(itemId, ByteOrder.LITTLE);
        msg.putShort(inventoryIndex, ValueType.ADD);
        client.queueSimulated(new GameMessage(24, MessageType.FIXED, msg));
    }

    /**
     * Sends the {@link AmountInputMessageReader} packet.
     *
     * @param amount The amount.
     */
    public void enterAmount(int amount) {
        ByteMessage msg = ByteMessage.raw();
        msg.putInt(amount);
        client.queueSimulated(new GameMessage(75, MessageType.FIXED, msg));
    }

    /**
     * Sends the {@link ItemOnObjectMessageReader} packet.
     *
     * @param targetObject The object to use the item with.
     * @return {@code true} if successfully sent.
     */
    public void useItemOnObject(int inventoryIndex, int itemId, GameObject targetObject) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(targetObject.getId(), ByteOrder.LITTLE);
        msg.putShort(3214, ByteOrder.LITTLE);
        msg.putShort(itemId, ByteOrder.LITTLE);
        msg.putShort(targetObject.getPosition().getY(), ByteOrder.LITTLE);
        msg.putShort(inventoryIndex);
        msg.putShort(targetObject.getPosition().getX(), ByteOrder.LITTLE, ValueType.ADD);
        client.queueSimulated(new GameMessage(152, MessageType.FIXED, msg));
    }

    /**
     * Sends the {@link ItemOnItemMessageReader} packet.
     *
     * @param targetInventoryIndex The inventory index to use the item with.
     * @param usedInventoryIndex The inventory index to use the item with.
     * @param targetId The target item id.
     * @param usedId The used item id.
     * @return {@code true} if successfully sent.
     */
    public void useItemOnItem(int targetInventoryIndex, int usedInventoryIndex, int targetId, int usedId) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(targetId);
        msg.putShort(usedInventoryIndex, ByteOrder.LITTLE);
        msg.putShort(usedId, ByteOrder.LITTLE);
        msg.putShort(3214, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(targetInventoryIndex, ValueType.ADD);
        msg.putShort(3214, ValueType.ADD);
        client.queueSimulated(new GameMessage(1, MessageType.FIXED, msg));
    }

    /**
     * Sends the {@link ItemOnNpcMessageReader} packet.
     *
     * @param usedInventoryIndex The used inventory index.
     * @param usedId The used item id.
     * @param targetNpc The npc to use the item with.
     * @return {@code true} if successfully sent.
     */
    public void useItemOnNpc(int usedInventoryIndex, int usedId, Npc targetNpc) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(targetNpc.getIndex());
        msg.putShort(usedId, ByteOrder.LITTLE);
        msg.putShort(3214, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(usedInventoryIndex);
        client.queueSimulated(new GameMessage(57, MessageType.FIXED, msg));
    }

    /**
     * Sends the {@link ItemOnPlayerMessageReader} packet.
     *
     * @param targetPlayer The player to use the item with.
     */
    public void useItemOnPlayer(int usedInventoryIndex, int usedId, Player targetPlayer) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(usedId, ByteOrder.LITTLE);
        msg.putShort(usedInventoryIndex, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(3214);
        msg.putShort(targetPlayer.getIndex(), ValueType.ADD);
        client.queueSimulated(new GameMessage(143, MessageType.FIXED, msg));
    }
}
