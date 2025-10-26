package io.luna.game.model.mob.bot.io;

import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerAppearance;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.object.GameObject;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.in.NumberInputMessageReader;
import io.luna.net.msg.in.ButtonClickMessageReader;
import io.luna.net.msg.in.ChatMessageReader;
import io.luna.net.msg.in.CloseInterfaceMessageReader;
import io.luna.net.msg.in.CommandMessageReader;
import io.luna.net.msg.in.ContinueDialogueMessageReader;
import io.luna.net.msg.in.DesignPlayerMessageReader;
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

/**
 * Handles all outgoing packets that a {@link Bot} can send to the game server.
 * <p>
 * This handler acts as the primary bridge between bot scripts and the packet layer, simulating the behavior of a real
 * RuneScape client. All bot actions such as chatting, interacting with NPCs, clicking objects, and using items are
 * executed through this class.
 * <p>
 * Every method in this class builds a fully-formed {@link GameMessage} identical to what an authentic client would
 * send. These messages are then queued for transmission through {@link BotClient#queueSimulated(GameMessage)}.
 *
 * @author lare96
 */
public final class BotOutputMessageHandler {

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
     * Sends the {@link CloseInterfaceMessageReader} packet to close any active interface.
     *
     * @return {@code true} if successfully queued.
     */
    public boolean sendCloseInterface() {
        ByteMessage msg = ByteMessage.raw();
        client.queueSimulated(new GameMessage(110, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends the {@link DesignPlayerMessageReader} packet with randomized appearance data.
     *
     * @return {@code true} if successfully queued.
     */
    public boolean sendCharacterDesignSelection() {
        return sendCharacterDesignSelection(PlayerAppearance.randomValues());
    }

    /**
     * Sends the {@link DesignPlayerMessageReader} packet with the specified appearance values.
     *
     * @param appearance The 13-element appearance array.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean sendCharacterDesignSelection(int[] appearance) {
        if (appearance == null || appearance.length != 13) {
            bot.log("Invalid appearance data.");
            return false;
        }
        ByteMessage msg = ByteMessage.raw();
        int index = 0;
        msg.put(appearance[index++]);
        for (int i = 0; i < 7; i++) {
            msg.put(appearance[index++]);
        }
        for (int i = 0; i < 5; i++) {
            msg.put(appearance[index++]);
        }
        client.queueSimulated(new GameMessage(163, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends one of the {@link PlayerClickMessageReader} packets.
     *
     * @param option The interaction option (1–5).
     * @param targetPlayer The target {@link Player}.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean sendPlayerInteraction(int option, Player targetPlayer) {
        if (targetPlayer == null) {
            bot.log("Cannot send player interaction: null target.");
            return false;
        }
        if (option < 1 || option > 5) {
            bot.log("Invalid player interaction option: " + option);
            return false;
        }

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
            default:
                bot.log("Unhandled player interaction option: " + option);
                return false;
        }

        client.queueSimulated(new GameMessage(opcode, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends one of the {@link GroundItemClickMessageReader} packets.
     *
     * @param option The interaction option, between 1-2.
     * @param targetItem The target item.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean sendGroundItemInteraction(int option, GroundItem targetItem) {
        if (targetItem == null) {
            bot.log("Cannot interact with null ground item.");
            return false;
        }
        if (option < 1 || option > 2) {
            bot.log("Invalid ground item interaction option: " + option);
            return false;
        }

        int x = targetItem.getPosition().getX();
        int y = targetItem.getPosition().getY();
        int id = targetItem.getId();

        ByteMessage msg = ByteMessage.raw();
        int opcode = -1;

        switch (option) {
            case 1:
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
            default:
                bot.log("Unhandled ground item option: " + option);
                return false;
        }

        client.queueSimulated(new GameMessage(opcode, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends the {@link ContinueDialogueMessageReader} packet.
     *
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean sendContinueDialogue() {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(0);
        client.queueSimulated(new GameMessage(226, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends one of the {@link ItemClickMessageReader} packets.
     *
     * @param option The option (1-5).
     * @param index The index on the widget.
     * @param itemId The item id.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean sendInventoryItemClick(int option, int index, int itemId) {
        if (option < 1 || option > 5) {
            bot.log("Invalid inventory item click option: " + option);
            return false;
        }

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
                msg.putShort(itemId, ValueType.ADD);
                msg.putShort(3214);
                msg.putShort(index, ValueType.ADD);
                opcode = 4;
                break;
            default:
                bot.log("Unhandled inventory item option: " + option);
                return false;
        }

        client.queueSimulated(new GameMessage(opcode, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends one of the {@link WidgetItemClickMessageReader} packets.
     *
     * @param option The option (1-5).
     * @param widgetIndex The index on the widget.
     * @param widgetId The widget id.
     * @param itemId The item id.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean sendItemWidgetClick(int option, int widgetIndex, int widgetId, int itemId) {
        if (option < 1 || option > 5) {
            bot.log("Invalid widget item click option: " + option);
            return false;
        }
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
        return true;
    }

    /**
     * Sends the {@link CommandMessageReader} packet. No colons are required.
     *
     * @param commandWithArgs The command to send, as you would type it.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean sendCommand(String commandWithArgs) {
        if (commandWithArgs == null || commandWithArgs.isBlank()) {
            bot.log("Cannot send empty command.");
            return false;
        }
        ByteMessage msg = ByteMessage.raw();
        msg.putString(commandWithArgs.replace("::", ""));
        client.queueSimulated(new GameMessage(56, MessageType.VAR, msg));
        return true;
    }

    /**
     * Sends one of the {@link NpcClickMessageReader} packets.
     *
     * @param option The interaction option, between 1-5.
     * @param localNpc The target npc.
     * @return {@code true} if successfully sent.
     */
    public boolean sendNpcInteraction(int option, Npc localNpc) {
        if (localNpc == null) {
            bot.log("Cannot interact with null NPC.");
            return false;
        }
        if (option < 1 || option > 5) {
            bot.log("Invalid NPC interaction option: " + option);
            return false;
        }

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
        return true;
    }

    /**
     * Sends one of the {@link ObjectClickMessageReader} packets.
     *
     * @param option The interaction option, between 1-5.
     * @param localObject The target npc.
     * @return {@code true} if successfully sent.
     */
    public boolean sendObjectInteraction(int option, GameObject localObject) {
        if (localObject == null) {
            bot.log("Cannot interact with null object.");
            return false;
        }
        if (option < 1 || option > 3) {
            bot.log("Invalid object interaction option: " + option);
            return false;
        }
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
        return true;
    }

    /**
     * Sends the {@link ChatMessageReader} packet.
     *
     * @param text The message text.
     * @param color The chat {@link ChatColor}.
     * @param effect The chat {@link ChatEffect}.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean chat(String text, ChatColor color, ChatEffect effect) {
        if (text == null || text.isBlank()) {
            bot.log("Cannot send empty chat message.");
            return false;
        }

        ByteMessage textMsg = ByteMessage.raw();
        try {
            StringUtils.packText(text, textMsg);
            ByteMessage msg = ByteMessage.raw();
            msg.put(color.getId(), ValueType.NEGATE);
            msg.put(effect.getId(), ValueType.ADD);
            msg.putBytes(textMsg);
            client.queueSimulated(new GameMessage(49, MessageType.VAR, msg));
            return true;
        } catch (Exception e) {
            bot.log("Failed to send chat: " + e.getMessage());
            return false;
        } finally {
            textMsg.releaseAll();
        }
    }

    /**
     * Sends the {@link ChatMessageReader} packet with default color and no effect.
     *
     * @param text The message text.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean chat(String text) {
        return chat(text, ChatColor.YELLOW, ChatEffect.NONE);
    }

    /**
     * Sends the {@link ButtonClickMessageReader} packet.
     *
     * @param buttonId The button identifier.
     * @return {@code true} if successfully queued.
     */
    public boolean clickButton(int buttonId) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(buttonId);
        client.queueSimulated(new GameMessage(79, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends the {@link ButtonClickMessageReader} packet for the logout button.
     *
     * @return {@code true} if successfully queued.
     */
    public boolean clickLogout() {
        return clickButton(2458);
    }

    /**
     * Sends the {@link NumberInputMessageReader} packet.
     *
     * @param amount The amount.
     * @return {@code true} if successfully queued.
     */
    public boolean enterAmount(int amount) {
        ByteMessage msg = ByteMessage.raw();
        msg.putInt(amount);
        client.queueSimulated(new GameMessage(75, MessageType.FIXED, msg));
        return true;
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
    public boolean useItemOnItem(int targetInventoryIndex, int usedInventoryIndex, int targetId, int usedId) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(targetId);
        msg.putShort(usedInventoryIndex, ByteOrder.LITTLE);
        msg.putShort(usedId, ByteOrder.LITTLE);
        msg.putShort(3214, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(targetInventoryIndex, ValueType.ADD);
        msg.putShort(3214, ValueType.ADD);
        client.queueSimulated(new GameMessage(1, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends the {@link ItemOnNpcMessageReader} packet.
     *
     * @param usedInventoryIndex The used item slot.
     * @param usedId The used item identifier.
     * @param targetNpc The target {@link Npc}.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean useItemOnNpc(int usedInventoryIndex, int usedId, Npc targetNpc) {
        if (targetNpc == null) {
            bot.log("Cannot use item on null NPC.");
            return false;
        }

        ByteMessage msg = ByteMessage.raw();
        msg.putShort(targetNpc.getIndex());
        msg.putShort(usedId, ByteOrder.LITTLE);
        msg.putShort(3214, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(usedInventoryIndex);
        client.queueSimulated(new GameMessage(57, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends the {@link ItemOnPlayerMessageReader} packet.
     *
     * @param usedInventoryIndex The used item slot.
     * @param usedId The used item identifier.
     * @param targetPlayer The target {@link Player}.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean useItemOnPlayer(int usedInventoryIndex, int usedId, Player targetPlayer) {
        if (targetPlayer == null) {
            bot.log("Cannot use item on null player.");
            return false;
        }

        ByteMessage msg = ByteMessage.raw();
        msg.putShort(usedId, ByteOrder.LITTLE);
        msg.putShort(usedInventoryIndex, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(3214);
        msg.putShort(targetPlayer.getIndex(), ValueType.ADD);
        client.queueSimulated(new GameMessage(143, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends the {@link ItemOnObjectMessageReader} packet.
     *
     * @param usedInventoryIndex The used item slot.
     * @param usedId The used item identifier.
     * @param targetObject The object to use the item with.
     * @return {@code true} if successfully queued, otherwise {@code false}.
     */
    public boolean useItemOnObject(int usedInventoryIndex, int usedId, GameObject targetObject) {
        if (targetObject == null) {
            bot.log("Cannot use item on null object.");
            return false;
        }
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(targetObject.getId(), ByteOrder.LITTLE);
        msg.putShort(3214, ByteOrder.LITTLE);
        msg.putShort(usedId, ByteOrder.LITTLE);
        msg.putShort(targetObject.getPosition().getY(), ByteOrder.LITTLE);
        msg.putShort(usedInventoryIndex);
        msg.putShort(targetObject.getPosition().getX(), ByteOrder.LITTLE, ValueType.ADD);
        client.queueSimulated(new GameMessage(152, MessageType.FIXED, msg));
        return true;
    }
}
