package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ListenableFuture;
import io.luna.Luna;
import io.luna.LunaContext;
import io.luna.game.action.Action;
import io.luna.game.event.impl.LoginEvent;
import io.luna.game.event.impl.LogoutEvent;
import io.luna.game.event.impl.RegionChangedEvent;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.item.Bank;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.map.DynamicMap;
import io.luna.game.model.mob.block.Chat;
import io.luna.game.model.mob.block.ForcedMovement;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.controller.ControllerManager;
import io.luna.game.model.mob.dialogue.DialogueQueue;
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder;
import io.luna.game.model.mob.inter.AbstractInterfaceSet;
import io.luna.game.model.mob.inter.GameTabSet;
import io.luna.game.model.mob.persistence.PlayerData;
import io.luna.game.model.mob.varp.PersistentVarp;
import io.luna.game.model.mob.varp.PersistentVarpManager;
import io.luna.game.model.mob.varp.Varbit;
import io.luna.game.model.mob.varp.Varp;
import io.luna.game.model.object.GameObject;
import io.luna.game.service.LogoutService;
import io.luna.game.service.LogoutService.LogoutRequest;
import io.luna.game.service.PersistenceService;
import io.luna.game.task.Task;
import io.luna.net.LunaChannelFilter;
import io.luna.net.client.GameClient;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.LogoutMessageWriter;
import io.luna.net.msg.out.RegionMessageWriter;
import io.luna.net.msg.out.SoundMessageWriter;
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter;
import io.luna.net.msg.out.UpdateWeightMessageWriter;
import io.luna.net.msg.out.VarpMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.luna.util.RandomUtils;
import io.luna.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import world.player.Messages;
import world.player.Sounds;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A model representing a player-controlled mob.
 *
 * @author lare96
 */
public class Player extends Mob {

    /**
     * An enum representing prayer icons.
     */
    public enum PrayerIcon {
        NONE(-1),
        PROTECT_FROM_MELEE(0),
        PROTECT_FROM_MISSILES0(1),
        PROTECT_FROM_MAGIC(2),
        RETRIBUTION(3),
        SMITE(4),
        REDEMPTION(5);

        /**
         * The identifier.
         */
        private final int id;

        /**
         * Creates a new {@link PrayerIcon}.
         *
         * @param id The identifier.
         */
        PrayerIcon(int id) {
            this.id = id;
        }

        /**
         * @return The identifier.
         */
        public int getId() {
            return id;
        }
    }

    /**
     * An enum representing skull icons.
     */
    public enum SkullIcon { // TODO test/redo
        NONE(-1),
        WHITE(0),
        RED(1);

        /**
         * The identifier.
         */
        private final int id;

        /**
         * Creates a new {@link SkullIcon}.
         *
         * @param id The identifier.
         */
        SkullIcon(int id) {
            this.id = id;
        }

        /**
         * @return The identifier.
         */
        public int getId() {
            return id;
        }
    }

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A set of local players. Should only be accessed from the updating threads.
     */
    private final Set<Player> localPlayers = new LinkedHashSet<>(255);

    /**
     * A set of local npcs. Should only be accessed from the updating threads.
     */
    private final Set<Npc> localNpcs = new LinkedHashSet<>(255);

    /**
     * A set of local objects.
     */
    private final Set<GameObject> localObjects = new HashSet<>(4);

    /**
     * The appearance.
     */
    private final PlayerAppearance appearance = new PlayerAppearance();

    /**
     * The credentials.
     */
    private final PlayerCredentials credentials;

    /**
     * The inventory.
     */
    private final Inventory inventory = new Inventory(this);

    /**
     * The equipment.
     */
    private final Equipment equipment = new Equipment(this);

    /**
     * The bank.
     */
    private final Bank bank = new Bank(this);

    /**
     * The interface set.
     */
    private final AbstractInterfaceSet interfaces = new AbstractInterfaceSet(this);

    /**
     * The game tab set.
     */
    private final GameTabSet tabs = new GameTabSet(this);

    /**
     * The text cache.
     */
    private final Map<Integer, String> textCache = new HashMap<>();

    /**
     * The music tab data.
     */
    private PlayerMusicTab musicTab = new PlayerMusicTab();

    /**
     * The cached update block.
     */
    private ByteMessage cachedBlock;

    /**
     * The rights.
     */
    private PlayerRights rights = PlayerRights.PLAYER;

    /**
     * The game client.
     */
    private GameClient client;

    /**
     * The last known region.
     */
    private Position lastRegion;

    /**
     * If the region has changed.
     */
    private boolean regionChanged;

    /**
     * The chat message.
     */
    private Optional<Chat> chat = Optional.empty();

    /**
     * The forced movement route.
     */
    private Optional<ForcedMovement> forcedMovement = Optional.empty();

    /**
     * The prayer icon.
     */
    private PrayerIcon prayerIcon = PrayerIcon.NONE;

    /**
     * The skull icon.
     */
    private SkullIcon skullIcon = SkullIcon.NONE;

    /**
     * The model animation.
     */
    private ModelAnimation modelAnimation = ModelAnimation.DEFAULT;

    /**
     * The dialogue queue.
     */
    private Optional<DialogueQueue> dialogues = Optional.empty();

    /**
     * The private message counter.
     */
    private int privateMsgCounter = 1;

    /**
     * If a teleportation is in progress.
     */
    private boolean teleporting;

    /**
     * The friend list.
     */
    private final Set<Long> friends = new LinkedHashSet<>();

    /**
     * The ignore list.
     */
    private final Set<Long> ignores = new LinkedHashSet<>();

    /**
     * The interaction menu.
     */
    private final PlayerInteractionMenu interactions = new PlayerInteractionMenu(this);

    /**
     * The hashed password.
     */
    private String hashedPassword;

    /**
     * The last IP address logged in with.
     */
    private String lastIp;

    /**
     * When the player is unbanned.
     */
    private Instant unbanInstant;

    /**
     * When the player is unmuted.
     */
    private Instant unmuteInstant;

    /**
     * The prepared save data.
     */
    private volatile PlayerData saveData;

    /**
     * The SQL database ID.
     */
    private int databaseId = -1;

    /**
     * The run energy percentage.
     */
    private double runEnergy;

    /**
     * The combined weight of the {@link #inventory} and {@link #equipment}.
     */
    private double weight;

    /**
     * The controller manager.
     */
    private final ControllerManager controllers = new ControllerManager(this);

    /**
     * The varp manager.
     */
    private final PersistentVarpManager varpManager = new PersistentVarpManager(this);

    /**
     * The timeout timer.
     */
    private final Stopwatch timeout = Stopwatch.createUnstarted();

    /**
     * The current dynamic map the player is in.
     */
    private DynamicMap dynamicMap;

    /**
     * Creates a new {@link Player}.
     *
     * @param context The context instance.
     * @param credentials The credentials.
     */
    public Player(LunaContext context, PlayerCredentials credentials) {
        super(context, EntityType.PLAYER);
        this.credentials = credentials;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Player) {
            Player other = (Player) obj;
            return getUsernameHash() == other.getUsernameHash();
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getUsernameHash());
    }

    @Override
    public final int size() {
        return 1;
    }

    @Override
    public final int sizeX() {
        return 1;
    }

    @Override
    public final int sizeY() {
        return 1;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("username", getUsername()).
                add("index", getIndex()).
                add("rights", rights).
                add("state", state).toString();
    }

    @Override
    protected void onActive() {
        teleporting = true;
        flags.flag(UpdateFlag.APPEARANCE);
        plugins.post(new LoginEvent(this));
    }

    @Override
    protected void onInactive() {
        actions.interrupt();
        world.getTasks().forEachAttachment(this, Task::cancel);
        world.getPlayerMap().remove(getUsername());
        removeLocalObjects();
        interfaces.close();
        plugins.post(new LogoutEvent(this));
    }

    @Override
    public void onTeleport(Position position) {
        teleporting = true;
    }

    @Override
    public void reset() {
        regionChanged = false;
        teleporting = false;
        chat = Optional.empty();
        forcedMovement = Optional.empty();
    }

    @Override
    public int getTotalHealth() {
        return skill(Skill.HITPOINTS).getStaticLevel();
    }

    @Override
    public void transform(int id) {
        transformId = OptionalInt.of(id);
        flags.flag(UpdateFlag.APPEARANCE);
    }

    @Override
    public void resetTransform() {
        if (transformId.isPresent()) {
            transformId = OptionalInt.empty();
            flags.flag(UpdateFlag.APPEARANCE);
        }
    }

    @Override
    public int getCombatLevel() {
        return skills.getCombatLevel();
    }

    @Override
    public void onSubmitAction(Action action) {
        interfaces.applyActionClose();
    }

    @Override
    protected void onPositionChanged(Position oldPos) {
        checkRegionChanged(oldPos);
    }

    /**
     * Sends a {@link RegionChangedEvent} if the region ID has changed as a result of a position change.
     *
     * @param oldPos The old position.
     */
    private void checkRegionChanged(Position oldPos) {
        Region oldRegion = oldPos.getRegion();
        Region newRegion = position.getRegion();
        if (!oldRegion.equals(newRegion)) { // TODO remove, useless
            context.getPlugins().post(new RegionChangedEvent(this, oldRegion, newRegion));
        }
    }

    /**
     * Sends a save request to the {@link PersistenceService}.
     *
     * @return The result of the save task.
     */
    public ListenableFuture<Void> save() {
        return world.getPersistenceService().save(this);
    }

    /**
     * Prepares the save data to be serialized by a {@link LogoutService} worker.
     */
    public PlayerData createSaveData() {
        saveData = new PlayerData(getUsername()).save(this);
        return saveData;
    }

    /**
     * Loads the argued save data into this player.
     */
    public void loadData(PlayerData data) {
        if (data != null) {
            // Load saved data.
            data.load(this);
        } else {
            // New player!
            setPosition(Luna.settings().game().startingPosition());
            rights = Luna.settings().game().betaMode() || LunaChannelFilter.WHITELIST.contains(client.getIpAddress()) ?
                    PlayerRights.DEVELOPER : PlayerRights.PLAYER;
        }
    }

    /**
     * Prepares the player for logout.
     */
    public void cleanUp() {
        if (getState() == EntityState.ACTIVE) {
            setState(EntityState.INACTIVE);
            createSaveData();
            world.getLogoutService().submit(getUsername(), new LogoutRequest(this));
        }
    }

    /**
     * Unregisters all assigned local objects.
     */
    public void removeLocalObjects() {
        if (!localObjects.isEmpty()) {
            Iterator<GameObject> objectIterator = localObjects.iterator();
            while (objectIterator.hasNext()) {
                world.getObjects().unregister(objectIterator.next());
                objectIterator.remove();
            }
        }
    }

    /**
     * Adds {@code item} to the inventory. If the inventory is full, add it to the bank. If the bank is full, will drop
     * it on the floor.
     *
     * @param item The item to give the player.
     */
    public void giveItem(Item item) {
        if (inventory.hasSpaceFor(item)) {
            inventory.add(item);
        } else if (bank.hasSpaceFor(item)) {
            bank.add(item);
            sendMessage(StringUtils.addArticle(item.getItemDef().getName()) + " has been deposited into your bank.");
        } else {
            world.getItems().register(new GroundItem(context, item.getId(), item.getAmount(),
                    position, ChunkUpdatableView.localView(this)));
            sendMessage(StringUtils.addArticle(item.getItemDef().getName()) + " has been dropped on the floor under you.");
        }
    }

    /**
     * Determines if this player has any item with {@code id} in the inventory, bank, equipped, or if it's dropped
     * nearby on the floor and visible.
     *
     * @param id The item ID to check for.
     */
    public boolean hasItem(int id) {
        if (inventory.contains(id) ||
                bank.contains(id) ||
                equipment.contains(id)) {
            return true;
        }
        return world.getChunks().getViewableEntities(position, EntityType.ITEM).stream().anyMatch(it -> {
            GroundItem groundItem = (GroundItem) it;
            return groundItem.getId() == id && groundItem.getView().isViewableFor(this);
        });
    }

    /**
     * Shortcut to queue a new {@link GameChatboxMessageWriter} packet.
     *
     * @param msg The message to send.
     */
    public void sendMessage(Object msg) {
        if (msg instanceof Messages) {
            msg = ((Messages) msg).getText();
        }
        queue(new GameChatboxMessageWriter(msg));
    }

    /**
     * Shortcut to queue a new {@link VarpMessageWriter} packet for an arbitrary varp.
     *
     * @param varp The varp to send.
     */
    public void sendVarp(Varp varp) {
        PersistentVarp persistentVarp = PersistentVarp.ALL.get(varp.getId());
        if (persistentVarp != null) {
            varpManager.setValue(persistentVarp, varp.getValue());
        }
        queue(new VarpMessageWriter(varp));
    }

    /**
     * Shortcut to queue a new {@link VarpMessageWriter} packet for a persistent varp.
     *
     * @param persistentVarp The persistent varp id.
     * @param value The new integer value to set.
     */
    public void sendVarp(PersistentVarp persistentVarp, int value) {
        sendVarp(new Varp(persistentVarp.getClientId(), value));
    }

    /**
     * Shortcut to queue a new {@link VarpMessageWriter} packet for a persistent varp.
     *
     * @param persistentVarp The persistent varp id.
     * @param value The new boolean value to set.
     */
    public void sendVarp(PersistentVarp persistentVarp, boolean value) {
        sendVarp(persistentVarp, value ? 1 : 0);
    }

    /**
     * Shortcut to queue a new {@link VarpMessageWriter} packet for a varbit.
     *
     * @param varbit The varbit.
     */
    public void sendVarbit(Varbit varbit) {
        Varp varp = varbit.toVarp();
        sendVarp(varp);
    }

    /**
     * Shortcut to queue a new {@link WidgetTextMessageWriter} packet. This function makes use of caching mechanisms that
     * can boost performance when invoked repetitively.
     *
     * @param msg The message to send.
     * @param id The widget identifier.
     */
    public void sendText(Object msg, int id) {
        // Retrieve the text that's already on the interface.
        String pending = msg.toString();
        String previous = textCache.put(id, pending);
        if (!pending.equals(previous)) {
            // Only queue the packet if we're sending different text.
            queue(new WidgetTextMessageWriter(pending, id));
        }
    }

    /**
     * Clears the {@link #textCache} for entry {@code id}.
     *
     * @param id The widget identifier.
     */
    public void clearText(int id) {
        textCache.remove(id);
    }

    /**
     * @return {@code true} if this player is a bot.
     */
    public boolean isBot() {
        return this instanceof Bot;
    }

    /**
     * @return This instance, cast to the {@link Bot} type. If this player is not a bot, {@link ClassCastException}
     * will be thrown.
     */
    public Bot asBot() {
        return (Bot) this;
    }

    /**
     * Logs out this player using the logout packet. The proper way to logout the player.
     */
    public void logout() {
        var channel = client.getChannel();
        if (channel.isActive()) {
            queue(new LogoutMessageWriter());
            client.setPendingLogout(true);
        }
    }

    /**
     * Disconnects this player's channel. Use this if an error occurs with the player.
     */
    public void disconnect() {
        var channel = client.getChannel();
        if (channel.isActive()) {
            channel.disconnect();
        }
    }

    /**
     * Sends the {@code chat} message.
     *
     * @param chat The chat instance.
     */
    public void chat(Chat chat) {
        this.chat = Optional.of(chat);
        flags.flag(UpdateFlag.CHAT);
    }

    /**
     * Traverses the path in {@code forcedMovement}.
     *
     * @param forcedMovement The forced movement path.
     */
    public void forceMovement(ForcedMovement forcedMovement) {
        this.forcedMovement = Optional.of(forcedMovement);
        flags.flag(UpdateFlag.FORCED_MOVEMENT);
    }

    /**
     * A shortcut function to {@link GameClient#queue(GameMessageWriter, Player)}.
     *
     * @param msg The message to queue in the buffer.
     */
    public void queue(GameMessageWriter msg) {
        client.queue(msg, this);
    }

    /**
     * Sends a region update if needed, and refreshes all {@link Entity} types that need to be displayed.
     *
     * @param oldPosition The player's old position before movement processing.
     */
    public void sendRegionUpdate(Position oldPosition) {
        boolean fullRefresh = false;
        if (lastRegion == null || needsRegionUpdate()) {
           if (isInDynamicMap()) {
                regionChanged = true;
                lastRegion = position;
                // comment ^ above out makes the player appear in a diff place????
                // todo cache palette? or current dynamic map?>
                // todo still need to update objects
             // dynamicMap.sendUpdate(this);
                return;
            }

            fullRefresh = true;
            regionChanged = true;
            lastRegion = position;
            queue(new RegionMessageWriter(position));
        }
        if (isTeleporting()) {
            fullRefresh = true;
        }
        world.getChunks().sendUpdates(this, oldPosition, fullRefresh);
    }

    /**
     * Determines if the player needs to send a region update message.
     *
     * @return {@code true} if the player needs a region update.
     */
    public boolean needsRegionUpdate() {
        int deltaX = position.getLocalX(lastRegion);
        int deltaY = position.getLocalY(lastRegion);
        return deltaX <= 15 || deltaX >= 88 || deltaY <= 15 || deltaY >= 88;
    }

    /**
     * Plays a random sound from {@code sounds}.
     */
    public void playRandomSound(Sounds... sound) {
        playSound(RandomUtils.random(sound), 0);
    }

    /**
     * Plays the sound with {@code id} with {@code delay}.
     */
    public void playSound(int soundId, int delay) {
        int volume = varpManager.getValue(PersistentVarp.EFFECTS_VOLUME);
        queue(new SoundMessageWriter(soundId, volume, delay));
    }

    /**
     * Plays sound with {@code id} with no delay.
     */
    public void playSound(int soundId) {
        playSound(soundId, 0);
    }

    /**
     * Plays {@code sound} with no delay.
     */
    public void playSound(Sounds sound) {
        playSound(sound, 0);
    }

    /**
     * Plays {@code sound} with {@code delay}.
     */
    public void playSound(Sounds sound, int delay) {
        int volume = varpManager.getValue(PersistentVarp.EFFECTS_VOLUME);
        queue(new SoundMessageWriter(sound.getId(), volume, delay));
    }

    /**
     * Returns a new builder that will be used to create dialogues.
     *
     * @return The dialogue builder.
     */
    public DialogueQueueBuilder newDialogue() {
        return new DialogueQueueBuilder(this, 10);
    }

    /**
     * @return The run energy percentage.
     */
    public double getRunEnergy() {
        return runEnergy;
    }

    /**
     * Sets the run energy percentage.
     *
     * @param newRunEnergy The value to set to.
     */
    public void setRunEnergy(double newRunEnergy, boolean update) {
        if (newRunEnergy > 100.0) {
            newRunEnergy = 100.0;
        }

        if (runEnergy != newRunEnergy) {
            runEnergy = newRunEnergy;
            if (update) {
                queue(new UpdateRunEnergyMessageWriter((int) runEnergy));
            }
        }
    }

    /**
     * Increases the current run energy level.
     *
     * @param amount The value to change by.
     */
    public void increaseRunEnergy(double amount) {
        double newEnergy = runEnergy + amount;
        if (newEnergy > 100.0) {
            newEnergy = 100.0;
        } else if (newEnergy < 0.0) {
            newEnergy = 0.0;
        }
        setRunEnergy(newEnergy, true);
    }

    /**
     * @return The combined weight of the inventory and equipment.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Sets the combined weight of the inventory and equipment.
     */
    public void setWeight(double newWeight, boolean update) {
        if (weight != newWeight) {
            weight = newWeight;
            if (update) {
                queue(new UpdateWeightMessageWriter((int) weight));
            }
        }
    }

    /**
     * Sets when the player is unmuted.
     *
     * @param unmuteInstant The value to set to.
     */
    public void setUnmuteInstant(Instant unmuteInstant) {
        this.unmuteInstant = unmuteInstant;
    }

    /**
     * @return When the player is unmuted.
     */
    public Instant getUnmuteInstant() {
        return unmuteInstant;
    }

    /**
     * Sets when the player is unbanned.
     *
     * @param unbanInstant The value to set to.
     */
    public void setUnbanInstant(Instant unbanInstant) {
        this.unbanInstant = unbanInstant;
    }

    /**
     * @return When the player is unbanned.
     */
    public Instant getUnbanInstant() {
        return unbanInstant;
    }

    /**
     * @return {@code true} if the player is muted.
     */
    public boolean isMuted() {
        return unmuteInstant != null && !Instant.now().isAfter(unmuteInstant);
    }

    /**
     * @return The prepared save data.
     */
    public PlayerData getSaveData() {
        if (saveData == null) {
            throw new NullPointerException("No data has been prepared yet.");
        }
        return saveData;
    }

    /**
     * @return The rights.
     */
    public PlayerRights getRights() {
        return rights;
    }

    /**
     * Sets the rights.
     *
     * @param rights The new rights.
     */
    public void setRights(PlayerRights rights) {
        this.rights = rights;
    }

    /**
     * @return The username.
     */
    public String getUsername() {
        return credentials.getUsername();
    }

    /**
     * @return The hashed password..
     */
    public String getHashedPassword() {
        return hashedPassword;
    }

    /**
     * Sets the hashed password.
     */
    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    /**
     * Sets the plaintext password.
     */
    public void setPassword(String password) {
        credentials.setPassword(password);
        hashedPassword = null;
    }

    /**
     * @return The password.
     */
    public String getPassword() {
        return credentials.getPassword();
    }

    /**
     * @return The username hash.
     */
    public long getUsernameHash() {
        return credentials.getUsernameHash();
    }

    /**
     * @return The game client.
     */
    public GameClient getClient() {
        return client;
    }

    /**
     * Sets the game client.
     *
     * @param newClient The value to set to.
     */
    public void setClient(GameClient newClient) {
        if (client != null) {
            logger.warn("GameClient can only be set once.");
            return;
        }
        client = newClient;
    }

    /**
     * @return A set of local players. Should only be accessed from the updating threads.
     */
    public Set<Player> getLocalPlayers() {
        return localPlayers;
    }

    /**
     * @return A set of local npcs. Should only be accessed from the updating threads.
     */
    public Set<Npc> getLocalNpcs() {
        return localNpcs;
    }

    /**
     * Sets the music tab data.
     *
     * @param musicTab The new value.
     */
    public void setMusicTab(PlayerMusicTab musicTab) {
        this.musicTab = musicTab;
    }

    /**
     * @return The music tab data.
     */
    public PlayerMusicTab getMusicTab() {
        return musicTab;
    }

    /**
     * Sets if this player is running.
     *
     * @param running The new value.
     */
    public void setRunning(boolean running) {
        sendVarp(PersistentVarp.RUNNING, running);
    }

    /**
     * @return {@code true} if this player is running.
     */
    public boolean isRunning() {
        return varpManager.getValue(PersistentVarp.RUNNING) == 1;
    }

    /**
     * @return The cached update block.
     */
    public ByteMessage getCachedBlock() {
        return cachedBlock;
    }

    /**
     * @return {@code true} if the player has a cached block.
     */
    public boolean hasCachedBlock() {
        return cachedBlock != null;
    }

    /**
     * Sets the cached update block.
     *
     * @param newMsg The value to set to.
     */
    public void setCachedBlock(ByteMessage newMsg) {
        // We have a cached block, release a reference to it.
        if (cachedBlock != null) {
            cachedBlock.release();
        }

        // Retain a reference to the new cached block.
        if (newMsg != null) {
            newMsg.retain();
        }

        cachedBlock = newMsg;
    }

    /**
     * @return The last known region.
     */
    public Position getLastRegion() {
        return lastRegion;
    }

    /**
     * Sets the last known region.
     *
     * @param lastRegion The value to set to.
     */
    public void setLastRegion(Position lastRegion) {
        this.lastRegion = lastRegion;
    }

    /**
     * @return {@code true} if the region has changed.
     */
    public boolean isRegionChanged() {
        return regionChanged;
    }

    /**
     * Sets if the region has changed.
     *
     * @param regionChanged The value to set to.
     */
    public void setRegionChanged(boolean regionChanged) {
        this.regionChanged = regionChanged;
    }

    /**
     * @return The chat message.
     */
    public Optional<Chat> getChat() {
        return chat;
    }

    /**
     * @return The forced movement route.
     */
    public Optional<ForcedMovement> getForcedMovement() {
        return forcedMovement;
    }

    /**
     * @return The appearance.
     */
    public PlayerAppearance getAppearance() {
        return appearance;
    }

    /**
     * @return The transformation identifier.
     */
    public OptionalInt getTransformId() {
        return transformId;
    }

    /**
     * @return The inventory.
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * @return The equipment.
     */
    public Equipment getEquipment() {
        return equipment;
    }

    /**
     * @return The bank.
     */
    public Bank getBank() {
        return bank;
    }

    /**
     * @return The prayer icon.
     */
    public PrayerIcon getPrayerIcon() {
        return prayerIcon;
    }

    /**
     * Sets the prayer icon.
     *
     * @param prayerIcon The value to set to.
     */
    public void setPrayerIcon(PrayerIcon prayerIcon) {
        this.prayerIcon = prayerIcon;
        flags.flag(UpdateFlag.APPEARANCE);
    }

    /**
     * @return The skull icon.
     */
    public SkullIcon getSkullIcon() {
        return skullIcon;
    }

    /**
     * Sets the skull icon.
     *
     * @param skullIcon The value to set to.
     */
    public void setSkullIcon(SkullIcon skullIcon) {
        this.skullIcon = skullIcon;
        flags.flag(UpdateFlag.APPEARANCE);
    }

    /**
     * @return The model animation.
     */
    public ModelAnimation getModelAnimation() {
        return modelAnimation;
    }

    /**
     * Sets the model animation.
     *
     * @param modelAnimation The value to set to.
     */
    public void setModelAnimation(ModelAnimation modelAnimation) {
        this.modelAnimation = modelAnimation;
        flags.flag(UpdateFlag.APPEARANCE);
    }

    /**
     * @return The interface set.
     */
    public AbstractInterfaceSet getInterfaces() {
        return interfaces;
    }

    /**
     * @return The game tab set.
     */
    public GameTabSet getTabs() {
        return tabs;
    }

    /**
     * Resets the current dialogue queue.
     */
    public void resetDialogues() {
        setDialogues(null);
    }

    /**
     * Sets the dialouge queue.
     *
     * @param dialogues The new value.
     */
    public void setDialogues(DialogueQueue dialogues) {
        this.dialogues = Optional.ofNullable(dialogues);
    }

    /**
     * @return The dialogue queue.
     */
    public Optional<DialogueQueue> getDialogues() {
        return dialogues;
    }

    /**
     * Returns the private message identifier and subsequently increments it by {@code 1}.
     *
     * @return The private message identifier.
     */
    public int newPrivateMessageId() {
        return privateMsgCounter++;
    }

    /**
     * @return The friend list.
     */
    public Set<Long> getFriends() {
        return friends;
    }

    /**
     * @return The ignore list.
     */
    public Set<Long> getIgnores() {
        return ignores;
    }

    /**
     * @return {@code true} if a teleportation is in progress.
     */
    public boolean isTeleporting() {
        return teleporting;
    }

    /**
     * @return The interaction menu.
     */
    public PlayerInteractionMenu getInteractions() {
        return interactions;
    }

    /**
     * @return The IP address currently logged in with.
     */
    public String getCurrentIp() {
        return client.getIpAddress();
    }

    /**
     * @return The last IP address logged in with.
     */
    public String getLastIp() {
        return lastIp;
    }

    /**
     * Sets the last IP address logged in with.
     *
     * @param lastIp The new value.
     */
    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    /**
     * @return The SQL database ID.
     */
    public int getDatabaseId() {
        return databaseId;
    }

    /**
     * Sets the SQL database ID.
     *
     * @param databaseId The new value.
     */
    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    /**
     * @return The controller manager.
     */
    public ControllerManager getControllers() {
        return controllers;
    }

    /**
     * @return The varp manager.
     */
    public PersistentVarpManager getVarpManager() {
        return varpManager;
    }

    /**
     * @return The timeout timer.
     */
    public Stopwatch getTimeout() {
        return timeout;
    }

    /**
     * Sets the current dynamic map the player is in.
     */
    public void setDynamicMap(DynamicMap dynamicMap) {
        this.dynamicMap = dynamicMap;
    }

    /**
     * @return The current dynamic map the player is in.
     */
    public DynamicMap getDynamicMap() {
        return dynamicMap;
    }

    /**
     * @return {@code true} If the player is in a dynamic map.
     */
    public boolean isInDynamicMap() {
        return dynamicMap != null;
    }

}
