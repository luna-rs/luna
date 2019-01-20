package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import io.luna.LunaConstants;
import io.luna.LunaContext;
import io.luna.game.action.Action;
import io.luna.game.event.entity.player.LoginEvent;
import io.luna.game.event.entity.player.LogoutEvent;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.World;
import io.luna.game.model.item.Bank;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.mob.attr.AttributeValue;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.dialogue.DialogueQueue;
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder;
import io.luna.game.model.mob.inter.AbstractInterfaceSet;
import io.luna.game.model.mob.inter.GameTabSet;
import io.luna.game.model.mob.persistence.SqlPlayerSerializer;
import io.luna.net.client.GameClient;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.ConfigMessageWriter;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.LogoutMessageWriter;
import io.luna.net.msg.out.RegionChangeMessageWriter;
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter;
import io.luna.net.msg.out.UpdateWeightMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

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
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * A set of local players.
     */
    private final Set<Player> localPlayers = new LinkedHashSet<>(255);

    /**
     * A set of local npcs.
     */
    private final Set<Npc> localNpcs = new LinkedHashSet<>(255);

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
    private final Map<Integer, String> textCache = LunaConstants.PACKET_126_CACHING ? new HashMap<>() : Map.of();

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
    private Chat chat;

    /**
     * The forced movement route.
     */
    private ForcedMovement forcedMovement;

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
    private DialogueQueue dialogues;

    /**
     * The private message counter.
     */
    private int privateMsgCounter = 1;

    /**
     * The database identifier. Will always be -1 unless the {@link SqlPlayerSerializer} is being used.
     */
    private long databaseId = -1;

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
        if (!(obj instanceof Player)) {
            return false;
        }
        
        return getUsernameHash() == ((Player) obj).getUsernameHash();
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
        return MoreObjects.toStringHelper(this)
                .add("username", getUsername())
                .add("index", getIndex())
                .add("rights", rights)
                .toString();
    }

    @Override
    protected void onActive() {
        teleporting = true;
        flags.flag(UpdateFlag.APPEARANCE);
        plugins.post(new LoginEvent(this));
        LOGGER.info("{} has logged in.", this);
    }

    @Override
    protected void onInactive() {
        world.getItems().removeLocal(this);
        world.getObjects().removeLocal(this);
        plugins.post(new LogoutEvent(this));
        save();
        LOGGER.info("{} has logged out.", this);
    }

    @Override
    public void onTeleport(Position position) {
        teleporting = true;
    }

    @Override
    public void reset() {
        teleporting = false;
        chat = null;
        forcedMovement = null;
        regionChanged = false;
    }

    @Override
    public int getTotalHealth() {
        return skill(Skill.HITPOINTS).getStaticLevel();
    }

    @Override
    public void transform(int id) {
        transformId = id;
        flags.flag(UpdateFlag.APPEARANCE);
    }

    @Override
    public void resetTransform() {
        if (transformId != -1) {
            transformId = -1;
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

    /**
     * Forwards to {@link World#savePlayer(Player)}.
     */
    public Future<Boolean> save() {
        return world.savePlayer(this);
    }

    /**
     * Shortcut to queue a new {@link GameChatboxMessageWriter} packet. It's used enough where this
     * is warranted.
     *
     * @param msg The message to send.
     */
    public void sendMessage(Object msg) {
        queue(new GameChatboxMessageWriter(msg));
    }

    /**
     * Shortcut to queue a new {@link WidgetTextMessageWriter} packet. It's used enough where this
     * is warranted.
     *
     * @param text The text to send.
     * @param id The widget identifier.
     */
    public void sendText(String text, int id) {
        requireNonNull(text);
        
        String previous = LunaConstants.PACKET_126_CACHING ? textCache.put(id, text) : null;
        
        if (!text.equals(previous)) {
            queue(new WidgetTextMessageWriter(text, id));
        }
    }

    /**
     * Disconnects this player.
     */
    public void logout() {
        var channel = client.getChannel();
        
        if (channel.isActive()) {
            queue(new LogoutMessageWriter());
        }
    }

    /**
     * Sends the {@code chat} message.
     *
     * @param chat The chat instance.
     */
    public void chat(Chat chat) {
        this.chat = chat;
        flags.flag(UpdateFlag.CHAT);
    }

    /**
     * Traverses the path in {@code forcedMovement}.
     *
     * @param forcedMovement The forced movement path.
     */
    public void forceMovement(ForcedMovement forcedMovement) {
        this.forcedMovement = forcedMovement;
        flags.flag(UpdateFlag.FORCED_MOVEMENT);
    }

    /**
     * A shortcut function to {@link GameClient#queue(GameMessageWriter)}.
     *
     * @param msg The message to queue in the buffer.
     */
    public void queue(GameMessageWriter msg) {
        client.queue(msg);
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
     * Sets the 'withdraw_as_note' attribute.
     *
     * @param withdrawAsNote The value to set to.
     */
    // TODO No point in this being an attribute. Migrate to boolean within "Bank" class
    public void setWithdrawAsNote(boolean withdrawAsNote) {
        AttributeValue<Boolean> attr = attributes.get("withdraw_as_note");
        
        if (attr.get() != withdrawAsNote) {
            attr.set(withdrawAsNote);
            queue(new ConfigMessageWriter(115, withdrawAsNote ? 1 : 0));
        }
    }

    /**
     * @return The 'withdraw_as_note' attribute.
     */
    public boolean isWithdrawAsNote() {
        AttributeValue<Boolean> attr = attributes.get("withdraw_as_note");
        return attr.get();
    }

    /**
     * Sets the 'run_energy' attribute.
     *
     * @param runEnergy The value to set to.
     */
    public void setRunEnergy(double runEnergy) {
        if (runEnergy > 100.0) {
            runEnergy = 100.0;
        }

        AttributeValue<Double> attr = attributes.get("run_energy");
        
        if (attr.get() != runEnergy) {
            attr.set(runEnergy);
            queue(new UpdateRunEnergyMessageWriter((int) runEnergy));
        }
    }

    /**
     * Changes the 'run_energy' attribute.
     *
     * @param runEnergy The value to change by.
     */
    public void changeRunEnergy(double runEnergy) {
        if (runEnergy <= 0.0) {
            return;
        }

        AttributeValue<Double> attr = attributes.get("run_energy");
        double newEnergy = attr.get() + runEnergy;
        
        if (newEnergy > 100.0) {
            newEnergy = 100.0;
        } else if (newEnergy < 0.0) {
            newEnergy = 0.0;
        }
        
        attr.set(newEnergy);
        queue(new UpdateRunEnergyMessageWriter((int) runEnergy));
    }

    /**
     * @return The 'run_energy' attribute.
     */
    public double getRunEnergy() {
        AttributeValue<Double> attr = attributes.get("run_energy");
        return attr.get();
    }

    /**
     * Sets the 'unmute_date' attribute.
     *
     * @param unmuteDate The value to set to.
     */
    public void setUnmuteDate(String unmuteDate) {
        AttributeValue<String> attr = attributes.get("unmute_date");
        attr.set(unmuteDate);
    }

    /**
     * @return The 'unmute_date' attribute.
     */
    public String getUnmuteDate() {
        AttributeValue<String> attr = attributes.get("unmute_date");
        return attr.get();
    }

    /**
     * Sets the 'unban_date' attribute.
     *
     * @param unbanDate The value to set to.
     */
    public void setUnbanDate(String unbanDate) {
        AttributeValue<String> attr = attributes.get("unban_date");
        attr.set(unbanDate);
    }

    /**
     * @return The 'unban_date' attribute.
     */
    public String getUnbanDate() {
        AttributeValue<String> attr = attributes.get("unban_date");
        return attr.get();
    }

    /**
     * Sets the 'weight' attribute.
     *
     * @param weight The value to set to.
     */
    public void setWeight(double weight) {
        AttributeValue<Double> attr = attributes.get("weight");
        attr.set(weight);
        queue(new UpdateWeightMessageWriter((int) weight));
    }

    /**
     * @return The 'weight' attribute.
     */
    public double getWeight() {
        AttributeValue<Double> attr = attributes.get("weight");
        return attr.get();
    }

    /**
     * @return {@code true} if the 'unmute_date' attribute is not equal to 'n/a'.
     */
    public boolean isMuted() {
        String date = getUnmuteDate();
        
        switch (date) {
            case "never":
                return true;
            case "n/a":
                return false;
            default:
                LocalDate lift = LocalDate.parse(date);
                LocalDate now = LocalDate.now();
                
                if (now.isAfter(lift)) {
                    setUnmuteDate("n/a");
                    return false;
                }
                
                return true;
        }
    }

    /**
     * @return {@code true} if the 'unban_date' attribute is not equal to 'n/a'.
     */
    public boolean isBanned() {
        String date = getUnbanDate();
        
        switch (date) {
            case "never":
                return true;
            case "n/a":
                return false;
            default:
                LocalDate lift = LocalDate.parse(date);
                LocalDate now = LocalDate.now();
                
                if (now.isAfter(lift)) {
                    setUnbanDate("n/a");
                    return false;
                }
                
                return true;
        }
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
     * @return A set of local players.
     */
    public Set<Player> getLocalPlayers() {
        return localPlayers;
    }

    /**
     * @return A set of local npcs.
     */
    public Set<Npc> getLocalNpcs() {
        return localNpcs;
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
        return Optional.ofNullable(chat);
    }

    /**
     * @return The forced movement route.
     */
    public Optional<ForcedMovement> getForcedMovement() {
        return Optional.ofNullable(forcedMovement);
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
    public int getTransformId() {
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
        if (dialogues != null) {
            dialogues.advance();
        }
    }

    /**
     * Sets the dialouge queue.
     *
     * @param dialogues The new value.
     */
    public void setDialogues(DialogueQueue dialogues) {
        this.dialogues = dialogues;
    }

    /**
     * @return The dialogue queue.
     */
    public Optional<DialogueQueue> getDialogues() {
        return Optional.ofNullable(dialogues);
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
     * Sets the database identifier.
     *
     * @param databaseId The new value.
     */
    public void setDatabaseId(long databaseId) {
        this.databaseId = databaseId;
    }

    /**
     * @return The database identifier.
     */
    public long getDatabaseId() {
        return databaseId;
    }

    /**
     * Sets the backing set of friends.
     *
     * @param newFriends The new value.
     */
    public void setFriends(long[] newFriends) {
        friends.clear();
        
        for (long name : newFriends) {
            friends.add(name);
        }
    }

    /**
     * @return The friend list.
     */
    public Set<Long> getFriends() {
        return friends;
    }

    /**
     * Sets the backing set of ignores.
     *
     * @param newIgnores The new value.
     */
    public void setIgnores(long[] newIgnores) {
        ignores.clear();
        
        for (long name : newIgnores) {
            ignores.add(name);
        }
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
}
