package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import io.luna.LunaConstants;
import io.luna.LunaContext;
import io.luna.game.event.impl.LoginEvent;
import io.luna.game.event.impl.LogoutEvent;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.item.Bank;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.mob.attr.AttributeValue;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.AssignmentMessageWriter;
import io.luna.net.msg.out.ConfigMessageWriter;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.LogoutMessageWriter;
import io.luna.net.msg.out.RegionChangeMessageWriter;
import io.luna.net.msg.out.SkillUpdateMessageWriter;
import io.luna.net.msg.out.TabInterfaceMessageWriter;
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter;
import io.luna.net.msg.out.UpdateWeightMessageWriter;
import io.luna.net.session.GameClient;
import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static io.luna.game.model.item.Bank.BANK_DISPLAY_ID;
import static io.luna.game.model.item.Equipment.EQUIPMENT_DISPLAY_ID;
import static io.luna.game.model.item.Inventory.INVENTORY_DISPLAY_ID;

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
     * The tab interfaces.
     */
    private static final ImmutableList<Integer> TAB_INTERFACES =
            ImmutableList.of(2423, 3917, 638, 3213, 1644, 5608, 1151, -1, 5065, 5715, 2449, 904, 147, 962);

    /**
     * A set of local players.
     */
    private final Set<Player> localPlayers = new LinkedHashSet<>();

    /**
     * A set of local npcs.
     */
    private final Set<Npc> localNpcs = new LinkedHashSet<>();

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
     * The text cache.
     */
    private final Map<Integer, String> textCache =
            LunaConstants.PACKET_126_CACHING ? new HashMap<>() : new HashMap<>(0);

    /**
     * The cached update block.
     */
    // TODO Might have to be volatile?
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
     * The serializer.
     */
    private final PlayerSerializer serializer;

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
     * Creates a new {@link Player}.
     *
     * @param context The context instance.
     * @param credentials The credentials.
     */
    public Player(LunaContext context, PlayerCredentials credentials) {
        super(context, EntityType.PLAYER);
        this.credentials = credentials;
        serializer = new PlayerSerializer(this);
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
        updateFlags.flag(UpdateFlag.APPEARANCE);

        queue(new AssignmentMessageWriter(true));

        displayTabInterfaces();

        int size = SkillSet.size();
        for (int index = 0; index < size; index++) {
            queue(new SkillUpdateMessageWriter(index));
        }

        int runEnergy = (int) getRunEnergy();
        queue(new UpdateRunEnergyMessageWriter(runEnergy));

        queue(inventory.constructRefresh(INVENTORY_DISPLAY_ID));
        queue(equipment.constructRefresh(EQUIPMENT_DISPLAY_ID));
        queue(bank.constructRefresh(BANK_DISPLAY_ID));

        sendMessage("Welcome to Luna.");
        sendMessage("You currently have " + rights.getFormattedName() + " privileges.");

        plugins.post(new LoginEvent(this));

        LOGGER.info("{} has logged in.", this);
    }

    @Override
    protected void onInactive() {

        plugins.post(new LogoutEvent(this));
        asyncSave();
        LOGGER.info("{} has logged out.", this);
    }

    @Override
    public void reset() {
        chat = Optional.empty();
        forcedMovement = Optional.empty();
        regionChanged = false;
    }

    @Override
    public int getTotalHealth() {
        return skill(Skill.HITPOINTS).getStaticLevel();
    }

    @Override
    public void transform(int id) {
        transformId = Optional.of(id);
        updateFlags.flag(UpdateFlag.APPEARANCE);
    }

    @Override
    public void resetTransform() {
        if (transformId.isPresent()) {
            transformId = Optional.empty();
            updateFlags.flag(UpdateFlag.APPEARANCE);
        }
    }

    @Override
    public int getCombatLevel() {
        return skillSet.getCombatLevel();
    }

    /**
     * Saves this player's data.
     */
    public void save() {
        serializer.save();
    }

    /**
     * Asynchronously saves this player's data.
     */
    public ListenableFuture<?> asyncSave() {
        return serializer.asyncSave();
    }

    /**
     * Displays the default tab interfaces.
     */
    public void displayTabInterfaces() {
        for (int index = 0; index < TAB_INTERFACES.size(); index++) {
            int tabId = TAB_INTERFACES.get(index);
            queue(new TabInterfaceMessageWriter(index, tabId));
        }

       /* interfaces.open(new TabInterface(2423, Tab.COMBAT));
        interfaces.open(new TabInterface(3917, Tab.SKILL));
        interfaces.open(new TabInterface(638, Tab.QUEST));
        interfaces.open(new TabInterface(3213, Tab.INVENTORY));
        interfaces.open(new TabInterface(1644, Tab.EQUIPMENT));
        interfaces.open(new TabInterface(5608, Tab.PRAYER));
        interfaces.open(new TabInterface(1151, Tab.MAGIC)); TODO ancient magicks support
        interfaces.open(new TabInterface(5065, Tab.FRIENDS));
        interfaces.open(new TabInterface(5715, Tab.IGNORES));
        interfaces.open(new TabInterface(2449, Tab.LOGOUT));
        interfaces.open(new TabInterface(904, Tab.SETTINGS));
        interfaces.open(new TabInterface(147, Tab.EMOTE));
        interfaces.open(new TabInterface(962, Tab.MUSIC)); */
    }

    /**
     * Shortcut to queue a new {@link GameChatboxMessageWriter} packet. It's used enough where this
     * is warranted.
     *
     * @param msg The message to send.
     */
    public void sendMessage(String msg) {
        queue(new GameChatboxMessageWriter(msg));
    }

    /**
     * Disconnects this player.
     */
    public void logout() {
        Channel channel = client.getChannel();
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
        this.chat = Optional.of(chat);
        updateFlags.flag(UpdateFlag.CHAT);
    }

    /**
     * Traverses the path in {@code forcedMovement}.
     *
     * @param forcedMovement The forced movement path.
     */
    public void forceMovement(ForcedMovement forcedMovement) {
        this.forcedMovement = Optional.of(forcedMovement);
        updateFlags.flag(UpdateFlag.FORCED_MOVEMENT);
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

        return deltaX < 16 || deltaX >= 88 || deltaY < 16 || deltaY > 88;
    }

    /**
     * Sets the 'withdraw_as_note' attribute.
     *
     * @param withdrawAsNote The value to set to.
     */
    public void setWithdrawAsNote(boolean withdrawAsNote) {
        AttributeValue<Boolean> attr = attributes.get("withdraw_as_note");
        attr.set(withdrawAsNote);

        queue(new ConfigMessageWriter(Bank.WITHDRAW_MODE_STATE_ID, withdrawAsNote ? 1 : 0));
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
        attr.set(runEnergy);

        queue(new UpdateRunEnergyMessageWriter((int) runEnergy));
    }

    /**
     * Changes the 'run_energy' attribute.
     *
     * @param runEnergy The value to change by.
     */
    public void changeRunEnergy(double runEnergy) {
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
        return !getUnmuteDate().equals("n/a");
    }

    /**
     * @return {@code true} if the 'unban_date' attribute is not equal to 'n/a'.
     */
    public boolean isBanned() {
        return !getUnbanDate().equals("n/a");
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
     * @return The set of local players.
     */
    public Set<Player> getLocalPlayers() {
        return localPlayers;
    }

    /**
     * @return The set of local npcs.
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
    public Optional<Integer> getTransformId() {
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
        updateFlags.flag(UpdateFlag.APPEARANCE);
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
        updateFlags.flag(UpdateFlag.APPEARANCE);
    }

    /**
     * @return The text cache.
     */
    public Map<Integer, String> getTextCache() {
        return textCache;
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
        updateFlags.flag(UpdateFlag.APPEARANCE);
    }
}
