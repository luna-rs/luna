package io.luna.game.model.mob.bot;

import api.bot.SuspendableFuture;
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent;
import io.luna.game.model.Entity;
import io.luna.game.model.Position;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerAppearance;
import io.luna.game.model.mob.dialogue.OptionDialogueInterface;
import io.luna.game.model.mob.inter.StandardInterface;
import io.luna.game.model.object.GameObject;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.in.ButtonClickMessageReader;
import io.luna.net.msg.in.ChatMessageReader;
import io.luna.net.msg.in.CommandMessageReader;
import io.luna.net.msg.in.ContinueDialogueMessageReader;
import io.luna.net.msg.in.DesignPlayerMessageReader;
import io.luna.net.msg.in.DropItemMessageReader;
import io.luna.net.msg.in.EquipItemMessageReader;
import io.luna.net.msg.in.ItemOnItemMessageReader;
import io.luna.net.msg.in.ItemOnNpcMessageReader;
import io.luna.net.msg.in.ItemOnObjectMessageReader;
import io.luna.net.msg.in.ItemOnPlayerMessageReader;
import io.luna.net.msg.in.NpcClickMessageReader;
import io.luna.net.msg.in.ObjectClickMessageReader;
import io.luna.net.msg.in.PlayerClickMessageReader;
import io.luna.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Manages the data for and building of all incoming and outgoing messages. This class should ideally be the main
 * way a {@link Bot} interacts with the server.
 * <p>
 * Incoming messages can be retrieved and sorted by type and time. Outgoing messages can be sent through the functions
 * in this class.
 *
 * @author lare96
 */
public final class BotMessageHandler {

    // todo https://github.com/luna-rs/luna/issues/378
// split up into one class for handling read messages, and one 'actionhandler' type class for writes
    // everything in this class should return suspendablefuture for coroutines
    //  documentaition

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
     * A helper class that enables bots to use items on other items or objects.
     */
    public final class UseItemInteraction {

        /**
         * The inventory index of the used item.
         */
        private final int useInventoryIndex;

        /**
         * Creates a new {@link UseItemInteraction}.
         *
         * @param useInventoryIndex The inventory index of the used item.
         */
        public UseItemInteraction(int useInventoryIndex) {
            this.useInventoryIndex = useInventoryIndex;
        }

        /**
         * Sends the {@link ItemOnObjectMessageReader} packet.
         *
         * @param localObject The object to use the item with.
         * @return {@code true} if successfully sent.
         */
        public boolean withObject(GameObject localObject) {
            if (!bot.getPosition().isViewable(localObject)) {
                return false;
            }
            int itemId = bot.getInventory().computeIdForIndex(useInventoryIndex).orElse(0);
            if (itemId == 0) {
                return false;
            }
            ByteMessage msg = ByteMessage.raw();
            msg.putShort(localObject.getId(), ByteOrder.LITTLE);
            msg.putShort(3214, ByteOrder.LITTLE);
            msg.putShort(itemId, ByteOrder.LITTLE);
            msg.putShort(localObject.getPosition().getY(), ByteOrder.LITTLE);
            msg.putShort(useInventoryIndex);
            msg.putShort(localObject.getPosition().getX(), ByteOrder.LITTLE, ValueType.ADD);
            client.queueSimulated(new GameMessage(152, MessageType.FIXED, msg));
            return true;
        }

        /**
         * Sends the {@link ItemOnItemMessageReader} packet.
         *
         * @param withInventoryIndex The inventory index to use the item with.
         * @return {@code true} if successfully sent.
         */
        public boolean withItem(int withInventoryIndex) {
            int targetId = bot.getInventory().computeIdForIndex(withInventoryIndex).orElse(0);
            int usedId = bot.getInventory().computeIdForIndex(useInventoryIndex).orElse(0);
            if (targetId == 0 || usedId == 0) {
                return false;
            }
            ByteMessage msg = ByteMessage.raw();
            msg.putShort(targetId);
            msg.putShort(useInventoryIndex, ByteOrder.LITTLE);
            msg.putShort(usedId, ByteOrder.LITTLE);
            msg.putShort(3214, ByteOrder.LITTLE, ValueType.ADD);
            msg.putShort(withInventoryIndex, ValueType.ADD);
            msg.putShort(3214, ValueType.ADD);
            client.queueSimulated(new GameMessage(1, MessageType.FIXED, msg));
            return true;
        }

        /**
         * Sends the {@link ItemOnNpcMessageReader} packet.
         *
         * @param localNpc The npc to use the item with.
         * @return {@code true} if successfully sent.
         */
        public boolean withNpc(Npc localNpc) {
            if (!bot.getPosition().isViewable(localNpc)) {
                return false;
            }
            int itemId = bot.getInventory().computeIdForIndex(useInventoryIndex).orElse(0);
            if (itemId == 0) {
                return false;
            }
            ByteMessage msg = ByteMessage.raw();
            msg.putShort(localNpc.getIndex());
            msg.putShort(itemId, ByteOrder.LITTLE);
            msg.putShort(3214, ByteOrder.LITTLE, ValueType.ADD);
            msg.putShort(useInventoryIndex);
            client.queueSimulated(new GameMessage(57, MessageType.FIXED, msg));
            return true;
        }

        /**
         * Sends the {@link ItemOnPlayerMessageReader} packet.
         *
         * @param localPlayer The player to use the item with.
         * @return {@code true} if successfully sent.
         */
        public boolean withPlayer(Player localPlayer) {
            if (!bot.getPosition().isViewable(localPlayer)) {
                return false;
            }
            int itemId = bot.getInventory().computeIdForIndex(useInventoryIndex).orElse(0);
            if (itemId == 0) {
                return false;
            }
            ByteMessage msg = ByteMessage.raw();
            msg.putShort(itemId, ByteOrder.LITTLE);
            msg.putShort(useInventoryIndex, ByteOrder.LITTLE, ValueType.ADD);
            msg.putShort(3214);
            msg.putShort(localPlayer.getIndex(), ValueType.ADD);
            client.queueSimulated(new GameMessage(143, MessageType.FIXED, msg));
            return true;
        }
    }

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The game messages received from the server.
     */
    private final List<BotMessage> receivedMessages = new ArrayList<>(1024);

    /**
     * The bot instance.
     */
    private final Bot bot;

    /**
     * The bot client instance.
     */
    private final BotClient client;

    /**
     * Creates a new {@link BotMessageHandler}.
     *
     * @param client The bot client instance.
     */
    public BotMessageHandler(BotClient client, Bot bot) {
        this.client = client;
        this.bot = bot;
    }

    /**
     * Adds a flushed message from the server to the backing list.
     *
     * @param msg The message to add.
     */
    void addMessage(BotMessage msg) {
        receivedMessages.add(msg);
    }

    /**
     * Clears all recently received messages.
     */
    public void clearMessages() {
        receivedMessages.clear();
    }

    /**
     * Retrieves all messages sent by this server to the {@link BotClient} since the last call to
     * {@link #clearMessages()}, and received after {@code from}.
     */
    public List<BotMessage> getAllMessages(Instant from) {
        List<BotMessage> filtered = new ArrayList<>();
        for (BotMessage msg : receivedMessages) {
            Instant timestamp = msg.getTimestamp();
            if (timestamp.equals(from) || timestamp.isAfter(from)) {
                filtered.add(msg);
            }
        }
        return filtered;
    }

    /**
     * Retrieves all messages sent by this server to the {@link BotClient} since the last call to
     * {@link #clearMessages()}, and received after {@code from}.
     */
    public List<BotMessage> getAllMessages(Class<?> type) {
        List<BotMessage> filtered = new ArrayList<>();
        for (BotMessage msg : receivedMessages) {
            if (type.isAssignableFrom(msg.getMessage().getClass())) {
                filtered.add(msg);
            }
        }
        return filtered;
    }

    /**
     * Retrieves all messages sent by this server to the {@link BotClient} since the last call to
     * {@link #clearMessages()}, and received after {@code from}.
     */
    public List<BotMessage> getAllMessages(Instant from, Class<?> type) {
        List<BotMessage> filtered = new ArrayList<>();
        for (BotMessage msg : receivedMessages) {
            Instant timestamp = msg.getTimestamp();
            if ((timestamp.equals(from) || timestamp.isAfter(from)) &&
                    type.isAssignableFrom(msg.getMessage().getClass())) {
                filtered.add(msg);
            }
        }
        return filtered;
    }

    /**
     * Retrieves all messages sent by this server since the last call to {@link #clearMessages()}. The returned
     * list is <strong>read-only</strong>.
     */
    public List<BotMessage> getAllMessages() {
        return Collections.unmodifiableList(receivedMessages);
    }

    /**
     * Deposits a single item if the banking interface is open.
     *
     * @param item The item to deposit.
     * @return
     */
    public SuspendableFuture depositItem(int inventoryIndex, int amount) {
        SuspendableFuture future = new SuspendableFuture();
        if (bot.getBank().isOpen()) {
            Item item = bot.getInventory().get(inventoryIndex);
            if(item == null) {
                return future.signal(false);
            }
            sendItemWidgetClick(5, inventoryIndex, 5064, item.getId());
            enterAmount(amount);
            return future;
        } else {
            return future.signal(false);
        }
    }

    public void enterAmount(int amount) {
        ByteMessage msg = ByteMessage.raw();
        msg.putInt(amount);
        client.queueSimulated(new GameMessage(75, MessageType.FIXED, msg));
    }

    /**
     * Sends the packets required to deposit all items on {@code inventoryIndex}.
     *
     * @param inventoryIndex The index to deposit all items on.
     * @return The future result.
     */
    public SuspendableFuture depositAll(int inventoryIndex) {
        if (bot.getBank().isOpen()) {
            int itemId = bot.getInventory().computeIdForIndex(inventoryIndex).orElse(-1);
            if (itemId == -1) {
                return new SuspendableFuture().signal(false);
            }
            return sendItemWidgetClick(4, inventoryIndex, 5064, itemId);
        } else {
            return new SuspendableFuture().signal(false);
        }
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
     * Sends one of the use-item packets.
     *
     * @param inventoryIndex The inventory item index.
     * @return The interaction helper.
     */
    public UseItemInteraction sendUseItemInteraction(int inventoryIndex) {
        return new UseItemInteraction(inventoryIndex);
    }

    /**
     * Sends the {@link WidgetItemFirstClickEvent} packet with data that will remove an item.
     *
     * @param equipmentIndex The equipment slot to remove.
     * @return {@code true} if successfully sent.
     */
    public boolean sendRemoveItem(int equipmentIndex) {
        int itemId = bot.getEquipment().computeIdForIndex(equipmentIndex).orElse(0);
        if (itemId == 0) {
            return false;
        }
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(itemId, ValueType.ADD);
        msg.putShort(1688);
        msg.putShort(equipmentIndex);
        client.queueSimulated(new GameMessage(3, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends the {@link EquipItemMessageReader} packet.
     *
     * @param inventoryIndex The inventory slot to equip.
     * @return {@code true} if successfully sent.
     */
    public boolean sendEquipItem(int inventoryIndex) {
        int itemId = bot.getInventory().computeIdForIndex(inventoryIndex).orElse(0);
        if (itemId == 0) {
            return false;
        }
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(1688, ByteOrder.LITTLE);
        msg.putShort(itemId, ByteOrder.LITTLE);
        msg.putShort(inventoryIndex, ValueType.ADD);
        client.queueSimulated(new GameMessage(24, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends the {@link DropItemMessageReader} packet.
     *
     * @param inventoryIndex The inventory index to drop.
     * @return {@code true} if successfully sent.
     */
    public boolean sendDropItem(int inventoryIndex) {
        int itemId = bot.getInventory().computeIdForIndex(inventoryIndex).orElse(0);
        if (itemId == 0) {
            return false;
        }
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(inventoryIndex, ByteOrder.LITTLE);
        msg.putShort(itemId, ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(3214, ByteOrder.LITTLE, ValueType.ADD);
        client.queueSimulated(new GameMessage(4, MessageType.FIXED, msg));
        return true;
    }

    /**
     * Sends the {@link ChatMessageReader} packet with the default color and effect.
     *
     * @param text The text to chat.
     */
    public void chat(String text) {
        // TODO not working
        chat(ChatColor.YELLOW, ChatEffect.NONE, text);
    }

    /**
     * Sends the {@link ChatMessageReader} packet.
     *
     * @param color The text color.
     * @param effect The text effect.
     * @param text The text to chat.
     */
    public void chat(ChatColor color, ChatEffect effect, String text) {
        // TODO not working
        ByteMessage msg = ByteMessage.raw();
        msg.put(color.id, ValueType.NEGATE);
        msg.put(effect.id, ValueType.ADD);
        StringUtils.packText(text, msg);
        client.queueSimulated(new GameMessage(49, MessageType.VAR, msg));
    }

    /**
     * Sends a {@link ButtonClickMessageReader} packet.
     *
     * @param buttonId The button id to send.
     */
    public void sendButton(int buttonId) {
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(buttonId);
        client.queueSimulated(new GameMessage(79, MessageType.VAR, msg));
    }

    /**
     * Sends a {@link ButtonClickMessageReader} packet that selects a dialogue option.
     *
     * @param option The option to select, between 1 and 5.
     * @return {@code true} if successfully sent.
     */
    public SuspendableFuture sendDialogueOption(int option) {
        SuspendableFuture future = new SuspendableFuture();
        StandardInterface inter = bot.getInterfaces().standardTo(OptionDialogueInterface.class).orElse(null);
        if (inter == null) {
            return future.signal(false);
        }
        switch (inter.unsafeGetId()) {
            case 14443:
                if (option == 1) {
                    sendButton(14445);
                } else if (option == 2) {
                    sendButton(14446);
                }
                break;
            case 2469:
                if (option == 1) {
                    sendButton(2471);
                } else if (option == 2) {
                    sendButton(2472);
                } else if (option == 3) {
                    sendButton(2473);
                }
                break;
            case 8207:
                if (option == 1) {
                    sendButton(8209);
                } else if (option == 2) {
                    sendButton(8210);
                } else if (option == 3) {
                    sendButton(8211);
                } else if (option == 4) {
                    sendButton(8212);
                }
                break;
            case 8219:
                if (option == 1) {
                    sendButton(8221);
                } else if (option == 2) {
                    sendButton(8222);
                } else if (option == 3) {
                    sendButton(8223);
                } else if (option == 4) {
                    sendButton(8224);
                } else if (option == 5) {
                    sendButton(8225);
                }
                break;
        }
        return future;
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
    public SuspendableFuture sendNpcInteraction(int option, Npc localNpc) {
        SuspendableFuture future = new SuspendableFuture();
        if (!bot.getPosition().isViewable(localNpc)) {
            return future.signal(false);
        }
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
        return future;
    }

    public SuspendableFuture sendItemWidgetClick(int option, int widgetIndex, int widgetId, int itemId) {
        SuspendableFuture future = new SuspendableFuture();
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
        return future;
    }

    public SuspendableFuture walk(Position target) {
        //TODO how far is too far?
        SuspendableFuture future = new SuspendableFuture();
        bot.getWalking().walk(target);
        return future;
    }

    public SuspendableFuture walk(Entity target) {        //TODO how far is too far?
        SuspendableFuture future = new SuspendableFuture();
        bot.getWalking().walk(target);
        return future;
    }

    public SuspendableFuture walkBehind(Mob target) {        //TODO how far is too far?
        SuspendableFuture future = new SuspendableFuture();
        bot.getWalking().walkBehind(target);
        return future;
    }

    /**
     * Sends the {@link PickupItemMessageReader} packet.
     *
     * @param localItem The local ground item.
     * @return {@code true} if successfully sent.
     */
    public boolean sendPickupItem(GroundItem localItem) {
        if (!bot.getPosition().isViewable(localItem)) {
            return false;
        }
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(localItem.getId(), ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(localItem.getPosition().getX(), ByteOrder.LITTLE, ValueType.ADD);
        msg.putShort(localItem.getPosition().getY(), ValueType.ADD);
        client.queueSimulated(new GameMessage(71, MessageType.FIXED, msg));
        return true;
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
     * Sends the {@link ContinueDialogueMessageReader} packet.
     */
    public SuspendableFuture sendContinueAllDialogue() {
        SuspendableFuture future = new SuspendableFuture();
        ByteMessage msg = ByteMessage.raw();
        msg.putShort(0);
        client.queueSimulated(new GameMessage(226, MessageType.FIXED, msg));
        return future;
    }

    /**
     * Sends one of the {@link ObjectClickMessageReader} packets.
     *
     * @param option The interaction option, between 1-3
     * @param localObject The target object.
     * @return {@code true} if successfully sent.
     */
    public SuspendableFuture sendObjectInteraction(int option, GameObject localObject) {
        SuspendableFuture future = new SuspendableFuture();
        if (!bot.getPosition().isViewable(localObject)) {
            return future.signal(false);
        }
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
        return future;
    }

    /**
     * Sends one of the {@link PlayerClickMessageReader} packets.
     *
     * @param option The interaction option, between 1-5.
     * @param localPlayer The target player.
     * @return {@code true} if successfully sent.
     */
    public boolean sendPlayerInteraction(int option, Player localPlayer) {
        if (!bot.getPosition().isViewable(localPlayer)) {
            return false;
        }
        checkArgument(option >= 1 && option <= 5, "[option] must be between 1 and 5.");
        int index = localPlayer.getIndex();
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
        return true;
    }
}
