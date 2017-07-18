package io.luna.game.model.mobile;

import com.google.common.base.MoreObjects;
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
import io.luna.game.model.mobile.attr.AttributeValue;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.MessageWriter;
import io.luna.net.msg.out.AssignmentMessageWriter;
import io.luna.net.msg.out.ConfigMessageWriter;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.LogoutMessageWriter;
import io.luna.net.msg.out.SkillUpdateMessageWriter;
import io.luna.net.msg.out.TabInterfaceMessageWriter;
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter;
import io.luna.net.msg.out.UpdateWeightMessageWriter;
import io.luna.net.session.GameSession;
import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
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
     * The cached update block.
     */
    private ByteMessage cachedBlock;

    /**
     * The rights.
     */
    private PlayerRights rights = PlayerRights.PLAYER;

    /**
     * The game session.
     */
    private GameSession session;

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
     * The transformation identifier.
     */
    private OptionalInt transformId = OptionalInt.empty();

    /**
     * The currently open shop name.
     */
    private Optional<String> currentShop = Optional.empty();

    /**
     * The prayer icon.
     */
    private PrayerIcon prayerIcon = PrayerIcon.NONE;

    /**
     * The skull icon.
     */
    private SkullIcon skullIcon = SkullIcon.NONE;

    /**
     * Creates a new {@link Player}.
     *
     * @param context The context instance.
     * @param credentials The credentials.
     */
    public Player(LunaContext context, PlayerCredentials credentials) {
        super(context, EntityType.PLAYER);
        this.credentials = credentials;

        setPosition(LunaConstants.STARTING_POSITION);

        if (credentials.getUsername().equals("lare96")) {
            rights = PlayerRights.DEVELOPER;
        }
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsernameHash());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("username", getUsername()).add("rights", rights).toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Player) {
            Player other = (Player) obj;
            return other.getUsernameHash() == getUsernameHash();
        }
        return false;
    }

    @Override
    public void onActive() {
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

        queue(new GameChatboxMessageWriter("Welcome to Luna!"));

        plugins.post(new LoginEvent(this));

        LOGGER.info("{} has logged in.", this);
    }

    @Override
    public void onInactive() {
        plugins.post(new LogoutEvent(this));

        PlayerSerializer serializer = new PlayerSerializer(this);
        serializer.asyncSave(service);

        LOGGER.info("{} has logged out.", this);
    }

    @Override
    public void reset() {
        chat = Optional.empty();
        forcedMovement = Optional.empty();
        regionChanged = false;
    }

    /**
     * Displays the default tab interfaces.
     */
    public void displayTabInterfaces() {
        int[] interfaces = { 2423, 3917, 638, 3213, 1644, 5608, 1151, -1, 5065, 5715, 2449, 904, 147, 962 };
        for (int index = 0; index < interfaces.length; index++) {
            queue(new TabInterfaceMessageWriter(index, interfaces[index]));
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
     * Disconnects this player.
     */
    public void logout() {
        Channel channel = session.getChannel();
        if (channel.isActive()) {
            queue(new LogoutMessageWriter());
        }
    }

    /**
     * Sends the {@code chat} message.
     */
    public void chat(Chat chat) {
        this.chat = Optional.of(chat);
        updateFlags.flag(UpdateFlag.CHAT);
    }

    /**
     * Traverses the path in {@code forcedMovement}.
     */
    public void forceMovement(ForcedMovement forcedMovement) {
        this.forcedMovement = Optional.of(forcedMovement);
        updateFlags.flag(UpdateFlag.FORCE_MOVEMENT);
    }

    /**
     * Transforms this player into an npc with {@code id}.
     */
    public void transform(int id) {
        transformId = OptionalInt.of(id);
        updateFlags.flag(UpdateFlag.APPEARANCE);
    }

    /**
     * Turns a transformed player back into a player.
     */
    public void untransform() { /* TODO better method name than 'untransform' ? */
        if (transformId.isPresent()) {
            transformId = OptionalInt.empty();
            updateFlags.flag(UpdateFlag.APPEARANCE);
        }
    }

    /**
     * A shortcut function to {@code GameSession.queue(MessageWriter)}.
     */
    public void queue(MessageWriter msg) {
        session.queue(msg);
    }

    /**
     * Determines if the player needs to send a region update message.
     */
    public boolean needsRegionUpdate() {
        int deltaX = position.getLocalX(lastRegion);
        int deltaY = position.getLocalY(lastRegion);

        return deltaX < 16 || deltaX >= 88 || deltaY < 16 || deltaY > 88;
    }

    /**
     * Sets the 'withdraw_as_note' attribute.
     */
    public void setWithdrawAsNote(boolean withdrawAsNote) {
        AttributeValue<Boolean> attr = attributes.get("withdraw_as_note");
        attr.set(withdrawAsNote);

        queue(new ConfigMessageWriter(Bank.WITHDRAW_MODE_STATE_ID, withdrawAsNote ? 1 : 0));
    }

    /**
     * Gets the 'withdraw_as_note' attribute.
     */
    public boolean isWithdrawAsNote() {
        AttributeValue<Boolean> attr = attributes.get("withdraw_as_note");
        return attr.get();
    }

    /**
     * Sets the 'run_energy' attribute.
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
     * Gets the 'run_energy' attribute.
     */
    public double getRunEnergy() {
        AttributeValue<Double> attr = attributes.get("run_energy");
        return attr.get();
    }

    /**
     * Sets the 'unmute_date' attribute.
     */
    public void setUnmuteDate(String unmuteDate) {
        AttributeValue<String> attr = attributes.get("unmute_date");
        attr.set(unmuteDate);
    }

    /**
     * Gets the 'unmute_date' attribute.
     */
    public String getUnmuteDate() {
        AttributeValue<String> attr = attributes.get("unmute_date");
        return attr.get();
    }

    /**
     * Sets the 'unban_date' attribute.
     */
    public void setUnbanDate(String unbanDate) {
        AttributeValue<String> attr = attributes.get("unban_date");
        attr.set(unbanDate);
    }

    /**
     * Gets the 'unban_date' attribute.
     */
    public String getUnbanDate() {
        AttributeValue<String> attr = attributes.get("unban_date");
        return attr.get();
    }

    /**
     * Sets the 'weight' attribute.
     */
    public void setWeight(double weight) {
        AttributeValue<Double> attr = attributes.get("weight");
        attr.set(weight);

        queue(new UpdateWeightMessageWriter((int) weight));
    }

    /**
     * Gets the 'weight' attribute.
     */
    public double getWeight() {
        AttributeValue<Double> attr = attributes.get("weight");
        return attr.get();
    }

    /**
     * Returns {@code true} if the 'unmute_date' attribute is not equal to 'n/a'.
     */
    public boolean isMuted() {
        return !getUnmuteDate().equals("n/a");
    }

    /**
     * Returns {@code true} if the 'unban_date' attribute is not equal to 'n/a'.
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
     * @return The game session.
     */
    public GameSession getSession() {
        return session;
    }

    /**
     * Sets the game session.
     */
    public void setSession(GameSession session) {
        checkState(this.session == null, "session already set!");
        this.session = session;
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
     * Sets the cached update block.
     */
    public void setCachedBlock(ByteMessage newMsg) {

        /* Release reference to old cached block. */
        if (cachedBlock != null) {
            cachedBlock.release();
        }

        /* Retain a reference to new cached block.. */
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
     * @return The currently open shop name.
     */
    public Optional<String> getCurrentShop() {
        return currentShop;
    }

    /**
     * Sets the currently open shop name.
     */
    public void setCurrentShop(String currentShop) {
        this.currentShop = Optional.ofNullable(currentShop);
    }

    /**
     * @return The prayer icon.
     */
    public PrayerIcon getPrayerIcon() {
        return prayerIcon;
    }

    /**
     * Sets the prayer icon.
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
     */
    public void setSkullIcon(SkullIcon skullIcon) {
        this.skullIcon = skullIcon;
        updateFlags.flag(UpdateFlag.APPEARANCE);
    }
}
