package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.ListenableFuture;
import io.luna.Luna;
import io.luna.LunaContext;
import io.luna.game.action.Action;
import io.luna.game.event.impl.LoginEvent;
import io.luna.game.event.impl.LogoutEvent;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.item.Bank;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.dialogue.DialogueQueue;
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder;
import io.luna.game.model.mob.inter.AbstractInterfaceSet;
import io.luna.game.model.mob.inter.GameTabSet;
import io.luna.game.model.mob.persistence.PlayerData;
import io.luna.game.model.object.GameObject;
import io.luna.game.service.LogoutService;
import io.luna.game.service.PersistenceService;
import io.luna.net.LunaChannelFilter;
import io.luna.net.client.GameClient;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.LogoutMessageWriter;
import io.luna.net.msg.out.RegionChangeMessageWriter;
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter;
import io.luna.net.msg.out.UpdateWeightMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a player-controlled mob.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Player extends Mob {

    /**
     * An enum representing prayer icons.
     */
    public enum PrayerIcon {
        NONE(-1),
        PROTECT_FROM_MELEE(0),
        PROTECT_FROM_MISSILES(1),
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
    public enum SkullIcon {
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
     * The settings.
     */
    private PlayerSettings settings = new PlayerSettings();

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
     * The running direction.
     */
    private Direction runningDirection = Direction.NONE;

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
    private LocalDateTime unbanDate;

    /**
     * When the player is unmuted.
     */
    private LocalDateTime unmuteDate;

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
    public boolean equals(Object obj) {
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
    public int hashCode() {
        return Objects.hash(getUsernameHash());
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("username", getUsername()).
                add("index", getIndex()).
                add("rights", rights).toString();
    }

    @Override
    protected void onActive() {
        world.getAreas().notifyLogin(this);
        teleporting = true;
        flags.flag(UpdateFlag.APPEARANCE);
        plugins.post(new LoginEvent(this));
    }

    @Override
    protected void onInactive() {
        actions.interrupt();
        world.getPlayerMap().remove(getUsername());
        world.getAreas().notifyLogout(this);
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
    protected void onPositionChange(Position oldPos) {
        world.getAreas().notifyPositionChange(this, oldPos, position);
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
    public void createSaveData() {
        saveData = new PlayerData().save(this);
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
            setPosition(Luna.settings().startingPosition());
            rights = Luna.settings().betaMode() || LunaChannelFilter.WHITELIST.contains(client.getIpAddress()) ?
                    PlayerRights.DEVELOPER : PlayerRights.PLAYER;
        }
        settings.setPlayer(this);
    }

    /**
     * Prepares the player for logout.
     */
    public void cleanUp() {
        if (getState() == EntityState.ACTIVE) {
            setState(EntityState.INACTIVE);
            createSaveData();
            world.getLogoutService().submit(getUsername(), this);
        }
    }

    /**
     * Unregisters all assigned local objects.
     */
    public void removeLocalObjects() {
        if (localObjects.size() > 0) {
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
        } else {
            world.getItems().register(new GroundItem(context, item.getId(), item.getAmount(),
                    position, Optional.of(this)));
        }
    }

    /**
     * Shortcut to queue a new {@link GameChatboxMessageWriter} packet.
     *
     * @param msg The message to send.
     */
    public void sendMessage(Object msg) {
        queue(new GameChatboxMessageWriter(msg));
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
     * Sends a region update, if one is needed.
     */
    public void sendRegionUpdate() {
        if (lastRegion == null || needsRegionUpdate()) {
            regionChanged = true;
            lastRegion = position;
            queue(new RegionChangeMessageWriter());
        }
    }

    /**
     * Determines if the player needs to send a region update message.
     *
     * @return {@code true} if the player needs a region update.
     */
    public boolean needsRegionUpdate() {
        int deltaX = position.getLocalX(lastRegion);
        int deltaY = position.getLocalY(lastRegion);

        return deltaX < 16 || deltaX >= 88 || deltaY < 16 || deltaY > 88; // TODO does last y need >= ?
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
     * @param unmuteDate The value to set to.
     */
    public void setUnmuteDate(LocalDateTime unmuteDate) {
        this.unmuteDate = unmuteDate;
    }

    /**
     * @return When the player is unmuted.
     */
    public LocalDateTime getUnmuteDate() {
        return unmuteDate;
    }

    /**
     * Sets when the player is unbanned.
     *
     * @param unbanDate The value to set to.
     */
    public void setUnbanDate(LocalDateTime unbanDate) {
        this.unbanDate = unbanDate;
    }

    /**
     * @return When the player is unbanned.
     */
    public LocalDateTime getUnbanDate() {
        return unbanDate;
    }

    /**
     * @return {@code true} if the player is muted.
     */
    public boolean isMuted() {
        return unmuteDate != null && !LocalDateTime.now().isAfter(unmuteDate);
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
        checkState(client == null, "GameClient can only be set once.");
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
     * Sets the settings.
     *
     * @param settings The new value.
     */
    public void setSettings(PlayerSettings settings) {
        this.settings = settings;
    }

    /**
     * @return The settings.
     */
    public PlayerSettings getSettings() {
        return settings;
    }

    /**
     * Sets if this player is running.
     *
     * @param running The new value.
     */
    public void setRunning(boolean running) {
        settings.setRunning(running);
    }

    /**
     * @return {@code true} if this player is running.
     */
    public boolean isRunning() {
        return settings.isRunning();
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
     * @return The running direction.
     */
    public Direction getRunningDirection() {
        return runningDirection;
    }

    /**
     * Sets the running direction.
     *
     * @param runningDirection The value to set to.
     */
    public void setRunningDirection(Direction runningDirection) {
        this.runningDirection = runningDirection;
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
     * Advances the current dialogue queue.
     */
    public void advanceDialogues() {
        dialogues.ifPresent(DialogueQueue::advance);
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
    public final boolean isTeleporting() {
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
}
