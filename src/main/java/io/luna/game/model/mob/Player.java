package io.luna.game.model.mob;

import com.google.common.base.MoreObjects;
import com.google.common.base.Stopwatch;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import game.item.consumable.potion.PotionCountdownTimer;
import game.player.Messages;
import game.player.Sounds;
import io.luna.Luna;
import io.luna.LunaContext;
import io.luna.game.LogoutService;
import io.luna.game.event.impl.InteractableEvent;
import io.luna.game.event.impl.LoginEvent;
import io.luna.game.event.impl.LogoutEvent;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.item.Bank;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.block.Chat;
import io.luna.game.model.mob.block.ExactMovement;
import io.luna.game.model.mob.block.LocalMobRepository;
import io.luna.game.model.mob.block.PlayerAppearance;
import io.luna.game.model.mob.block.PlayerModelAnimation;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.controller.ControllerManager;
import io.luna.game.model.mob.dialogue.DialogueQueue;
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder;
import io.luna.game.model.mob.overlay.AbstractOverlaySet;
import io.luna.game.model.mob.overlay.GameTabSet;
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex;
import io.luna.game.model.mob.varp.PersistentVarp;
import io.luna.game.model.mob.varp.PersistentVarpManager;
import io.luna.game.model.mob.varp.Varbit;
import io.luna.game.model.mob.varp.Varp;
import io.luna.game.persistence.PersistenceService;
import io.luna.game.persistence.PlayerData;
import io.luna.game.task.TaskState;
import io.luna.net.LunaChannelFilter;
import io.luna.net.client.GameClient;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.AssignmentMessageWriter;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.LogoutMessageWriter;
import io.luna.net.msg.out.RegionMessageWriter;
import io.luna.net.msg.out.SoundMessageWriter;
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter;
import io.luna.net.msg.out.UpdateWeightMessageWriter;
import io.luna.net.msg.out.VarpMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a player-controlled mob (human client or {@link Bot}) within the game world.
 *
 * @author lare96
 */
public class Player extends Mob {

    /**
     * Asynchronous logger used for player-specific events and errors.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Repository of local mobs visible to this player for update processing.
     */
    private final LocalMobRepository localMobs = new LocalMobRepository(this);

    /**
     * The current overhead prayer icon.
     */
    private PrayerIcon prayerIcon = PrayerIcon.NONE;

    /**
     * The current skull icon.
     */
    private SkullIcon skullIcon = SkullIcon.NONE;

    /**
     * The current model animation set used by this player.
     */
    private PlayerModelAnimation model = PlayerModelAnimation.DEFAULT;

    /**
     * Credentials backing this account (username, password, hash, username hash).
     */
    private final PlayerCredentials credentials;

    /**
     * Current appearance data for this player.
     */
    private final PlayerAppearance appearance = new PlayerAppearance();

    /**
     * The inventory container for this player.
     */
    private final Inventory inventory = new Inventory(this);

    /**
     * The equipment container for this player.
     */
    private final Equipment equipment = new Equipment(this);

    /**
     * The bank container for this player.
     */
    private final Bank bank = new Bank(this);

    /**
     * Active overlay (interface) set for this player.
     */
    private final AbstractOverlaySet overlays = new AbstractOverlaySet(this);

    /**
     * The game tab set (inventory, equipment, magic, etc.).
     */
    private final GameTabSet tabs = new GameTabSet(this);

    /**
     * Cache of widget text values keyed by widget id, used to avoid redundant text packets.
     */
    private final Map<Integer, String> textCache = new HashMap<>();

    /**
     * Player privacy options (PM, trade, etc.).
     */
    private PlayerPrivacy privacyOptions = new PlayerPrivacy();

    /**
     * Current spellbook in use by this player.
     */
    private Spellbook spellbook = Spellbook.REGULAR;

    /**
     * The account rights for this player.
     */
    private PlayerRights rights = PlayerRights.PLAYER;

    /**
     * The active {@link GameClient} backing this player's session.
     */
    private GameClient client;

    /**
     * The last region position used for region-change detection.
     */
    private Position lastRegion;

    /**
     * Whether the region changed this tick.
     */
    private boolean regionChanged;

    /**
     * The current dialogue queue, or {@code null} if none is active.
     */
    private DialogueQueue dialogues;

    /**
     * Local counter used to assign unique private message ids for this player.
     */
    private int privateMsgCounter = 1;

    /**
     * The friend list, stored as username hashes.
     */
    private final Set<Long> friends = new LinkedHashSet<>();

    /**
     * The ignore list, stored as username hashes.
     */
    private final Set<Long> ignores = new LinkedHashSet<>();

    /**
     * The interaction menu state (right-click options, custom actions).
     */
    private final PlayerInteractionMenu interactions = new PlayerInteractionMenu(this);

    /**
     * The hashed password. May be {@code null} for legacy/plaintext accounts until conversion.
     */
    private String hashedPassword;

    /**
     * The last known IP address this player logged in with.
     */
    private String lastIp;

    /**
     * Timestamp when this player will be unbanned, or {@code null} if not banned.
     */
    private Instant unbanInstant;

    /**
     * Timestamp when this player will be unmuted, or {@code null} if not muted.
     */
    private Instant unmuteInstant;

    /**
     * SQL database primary key for this account, or {@code -1} if not yet assigned.
     */
    private int databaseId = -1;

    /**
     * Current run energy percentage (0–100).
     */
    private double runEnergy;

    /**
     * Combined weight of {@link #inventory} and {@link #equipment}.
     */
    private double weight;

    /**
     * Timestamp when this account was first created.
     */
    private Instant createdAt = Instant.now();

    /**
     * Total time played (accumulated across logins). See {@link #getTimePlayed()} for details.
     */
    private Duration timePlayed = Duration.ZERO;

    /**
     * Controller manager for this player, handling region/area-specific behaviour.
     */
    private final ControllerManager controllers = new ControllerManager(this);

    /**
     * Manager for persistent varps attached to this player.
     */
    private final PersistentVarpManager varpManager = new PersistentVarpManager(this);

    /**
     * Cached client-side varp values, keyed by varp id. Used for efficient varbit packing.
     */
    private final Map<Integer, Integer> cachedVarps = new HashMap<>();

    /**
     * Stopwatch tracking how long this player has been online for the current session.
     */
    private final Stopwatch timeOnline = Stopwatch.createUnstarted();

    /**
     * The current interaction task (e.g., walking to an entity and performing an action), or {@code null}.
     */
    private InteractionTask interactionTask;

    /**
     * Creates a new {@link Player} for the given {@link PlayerCredentials}.
     *
     * @param context The global context instance.
     * @param credentials The credentials backing this account.
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
        pendingPlacement = true;
        flags.flag(UpdateFlag.APPEARANCE);
        queue(new AssignmentMessageWriter(true));
        timeOnline.start();
        plugins.post(new LoginEvent(this));
    }

    @Override
    protected void onInactive() {
        overlays.closeWindows();
        plugins.post(new LogoutEvent(this));
    }

    @Override
    public void onMove(Position position) {
        overlays.closeWindows();
    }

    @Override
    public void reset() {
        regionChanged = false;
    }

    @Override
    public int getTotalHealth() {
        return skill(Skill.HITPOINTS).getStaticLevel();
    }

    @Override
    public void transform(int npcId) {
        transformId = npcId;
        flags.flag(UpdateFlag.APPEARANCE);
    }

    @Override
    public void resetTransform() {
        if (transformId > -1) {
            transformId = -1;
            flags.flag(UpdateFlag.APPEARANCE);
        }
    }

    @Override
    public int getCombatLevel() {
        return skills.getCombatLevel();
    }

    /**
     * Sends a save request for this player to the {@link PersistenceService}.
     *
     * @return A {@link CompletableFuture} that completes when the save has finished.
     */
    public CompletableFuture<Void> save() {
        return world.getPersistenceService().save(this);
    }

    /**
     * Prepares a {@link PlayerData} snapshot for this player, ready to be serialized by {@link LogoutService} or
     * other persistence workers.
     *
     * @return A new {@link PlayerData} instance describing the current state of this player.
     */
    public PlayerData createSaveData() {
        return new PlayerData(getUsername()).save(this);
    }

    /**
     * Loads the given {@link PlayerData} into this player.
     * <p>
     * If {@code data} is {@code null}, this is treated as a first-time login and the player is placed at the
     * configured starting position with default rights.
     * </p>
     *
     * @param data The data to load, or {@code null} for a new player.
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
            if (isBot()) {
                rights = PlayerRights.PLAYER;
            }
        }
    }

    /**
     * Schedules an {@link InteractableEvent} to be executed once this player reaches the target {@link Entity}.
     * <p>
     * If an existing {@link InteractionTask} is running, it is cancelled and replaced.
     * </p>
     *
     * @param entity The entity to interact with.
     * @param event The event to post once the entity is reached.
     * @param action A callback to run if the interaction completes successfully.
     */
    public void handleInteractableEvent(Entity entity, InteractableEvent event, Runnable action) {
        if (interactionTask == null || interactionTask.getState() == TaskState.CANCELLED) {
            // Cancelled or missing interaction task, create new one.
            interactionTask = new InteractionTask(this, entity, event, action);
            world.schedule(interactionTask);
        } else if (interactionTask.getState() == TaskState.RUNNING) {
            // We have an interaction pending, cancel old task and create new one.
            interactionTask.cancel();
            interactionTask = new InteractionTask(this, entity, event, action);
            world.schedule(interactionTask);
        }
    }

    /**
     * Cancels the current {@link InteractionTask}, if present.
     */
    public void resetInteractionTask() {
        if (interactionTask != null) {
            interactionTask.cancel();
        }
    }

    /**
     * Gives the argued {@link Item} to this player.
     * <p>
     * The item is:
     * </p>
     * <ol>
     *     <li>First attempt to add to the inventory.</li>
     *     <li>Otherwise placed in the bank if there is space.</li>
     *     <li>Otherwise dropped on the ground beneath the player.</li>
     * </ol>
     *
     * @param item The item to give this player.
     */
    public void giveItem(Item item) {
        String name = item.getItemDef().getName();
        int freeSlots = inventory.computeRemainingSize();
        if (item.getItemDef().isStackable()) {
            int amount = inventory.computeAmountForId(item.getId());
            if ((amount == 0 && freeSlots > 0) || (amount + item.getAmount() > 0)) {
                inventory.add(item);
                sendMessage(name + "(x" + item.getAmount() + ")" + " has been added into your inventory.");
                return;
            }
        } else {
            int leftover = item.getAmount() - freeSlots;
            if (leftover > 0) {
                inventory.add(item.withAmount(freeSlots)); // Add what we can fit.
                sendMessage(name + "(x" + item.getAmount() + ")" + " has been added into your inventory.");
                item = item.withAmount(leftover); // Add the rest to the bank or on the ground.
            } else {
                inventory.add(item);
                return;
            }
        }

        if (bank.hasSpaceFor(item)) {
            bank.add(item);
            sendMessage(name + "(x" + item.getAmount() + ")" + " has been deposited into your bank.");
        } else {
            world.getItems().register(new GroundItem(context, item.getId(), item.getAmount(),
                    position, ChunkUpdatableView.localView(this)));
            sendMessage(name + "(x" + item.getAmount() + ")" + " has been dropped on the floor under you.");
        }
    }

    /**
     * Determines whether this player has an item with the given id:
     * <ul>
     *     <li>In their {@link Inventory},</li>
     *     <li>In their {@link Bank},</li>
     *     <li>In their {@link Equipment}, or</li>
     *     <li>On the ground nearby and visible to this player.</li>
     * </ul>
     *
     * @param id The item id to search for.
     * @return {@code true} if at least one such item is found.
     */
    public boolean hasItem(int id) {
        if (inventory.contains(id) ||
                bank.contains(id) ||
                equipment.contains(id)) {
            return true;
        }
        return world.getItems().stream().
                anyMatch(it -> it.getId() == id && it.getView().isViewableFor(this));
    }

    /**
     * Shortcut for queuing a {@link GameChatboxMessageWriter} packet.
     *
     * @param msg The message or {@link Messages} enum to send.
     */
    public void sendMessage(Object msg) {
        if (msg instanceof Messages) {
            msg = ((Messages) msg).getText();
        }
        queue(new GameChatboxMessageWriter(msg));
    }

    /**
     * Sends an arbitrary {@link Varp} update to the client and records it in the cached varp map.
     * <p>
     * If the varp is also tracked as a {@link PersistentVarp}, the persistent value is updated as well.
     * </p>
     *
     * @param varp The varp to send to this player.
     */
    public void sendVarp(Varp varp) {
        PersistentVarp persistentVarp = PersistentVarp.ALL.get(varp.getId());
        if (persistentVarp != null) {
            varpManager.setValue(persistentVarp, varp.getValue());
        }
        queue(new VarpMessageWriter(varp));
        cachedVarps.put(varp.getId(), varp.getValue());
    }

    /**
     * Sends a {@link Varp} update for a {@link PersistentVarp} using an integer value.
     *
     * @param persistentVarp The persistent varp id.
     * @param value The new integer value.
     */
    public void sendVarp(PersistentVarp persistentVarp, int value) {
        sendVarp(new Varp(persistentVarp.getClientId(), value));
    }

    /**
     * Sends a {@link Varp} update for a {@link PersistentVarp} using a boolean value.
     *
     * @param persistentVarp The persistent varp id.
     * @param value The new boolean value.
     */
    public void sendVarp(PersistentVarp persistentVarp, boolean value) {
        sendVarp(persistentVarp, value ? 1 : 0);
    }

    /**
     * Sends a single {@link Varbit} update by packing its value into the parent varp.
     *
     * @param varbit The varbit instance.
     */
    public void sendVarbit(Varbit varbit) {
        int parentId = varbit.getDef().getParentVarpId();
        int parentValue = cachedVarps.getOrDefault(parentId, 0);
        Varp varp = new Varp(parentId, varbit.pack(parentValue));
        sendVarp(varp);
    }

    /**
     * Sends multiple {@link Varbit} updates that share the same parent varp.
     *
     * @param parentId The id of the parent varp.
     * @param varbits The varbits to update. All must share the same parent.
     */
    public void sendVarbits(int parentId, List<Varbit> varbits) {
        if (varbits.isEmpty()) {
            return;
        } else if (varbits.size() == 1) {
            sendVarbit(varbits.iterator().next());
            return;
        }
        int parentValue = cachedVarps.getOrDefault(parentId, 0);
        int value = 0;
        for (Varbit vb : varbits) {
            if (vb.getDef().getParentVarpId() != parentId) {
                throw new IllegalArgumentException("All varbits must have the same parent.");
            }
            int packed = vb.pack(parentValue);
            if (value == 0) {
                value = packed;
            } else {
                value |= packed;
            }
        }
        sendVarp(new Varp(parentId, value));
    }

    /**
     * Sends a widget text update using {@link WidgetTextMessageWriter}, with a small cache to avoid sending
     * duplicate values.
     *
     * @param msg The text to send.
     * @param id The widget id to update.
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
     * Clears the cached widget text for the given id.
     *
     * @param id The widget identifier to clear.
     */
    public void clearText(int id) {
        textCache.remove(id);
    }

    /**
     * Returns whether this player is a {@link Bot}.
     *
     * @return {@code true} if this player is actually a bot instance.
     */
    public boolean isBot() {
        return this instanceof Bot;
    }

    /**
     * Casts this player to {@link Bot}.
     *
     * @return This instance, cast to {@link Bot}.
     * @throws ClassCastException If this player is not a bot.
     */
    public Bot asBot() {
        return (Bot) this;
    }

    /**
     * Requests a graceful logout by sending the logout packet.
     * <p>
     * This is the normal way to log a player out and will be handled via {@link LogoutService}.
     * </p>
     */
    public void logout() {
        var channel = client.getChannel();
        if (channel.isActive()) {
            queue(new LogoutMessageWriter());
        }
    }

    /**
     * Forces a logout for this player.
     */
    public void forceLogout() {
        client.setForcedLogout(true);
        logout();
    }

    /**
     * Queues a chat update block for this player.
     *
     * @param chat The chat payload to send.
     */
    public void chat(Chat chat) {
        pendingBlockData.chat(chat);
        flags.flag(UpdateFlag.CHAT);
    }

    /**
     * Queues a forced movement path using {@link ExactMovement}.
     *
     * @param move The forced movement descriptor.
     */
    public void exactMove(ExactMovement move) {
        pendingBlockData.move(move);
        flags.flag(UpdateFlag.EXACT_MOVEMENT);
    }

    /**
     * Shortcut for {@link GameClient#queue(GameMessageWriter)}.
     *
     * @param msg The message to enqueue.
     */
    public void queue(GameMessageWriter msg) {
        client.queue(msg);
    }

    /**
     * Sends a region update if needed and refreshes nearby {@link Entity} instances.
     * <p>
     * This method:
     * </p>
     * <ol>
     *     <li>Detects if a region change has occurred and sends a {@link RegionMessageWriter} if required.</li>
     *     <li>Handles pending placement (full refresh).</li>
     *     <li>Delegates to {@link io.luna.game.model.chunk.ChunkManager#sendUpdates(Player, Position, boolean)}.</li>
     * </ol>
     *
     * @param oldPosition The previous position before movement processing.
     */
    public void updateLocalView(Position oldPosition) {
        boolean fullRefresh = false;
        if (lastRegion == null || needsRegionUpdate()) {
            fullRefresh = true;
            regionChanged = true;
            lastRegion = position;
            queue(new RegionMessageWriter(position));
        }
        if (isPendingPlacement()) {
            fullRefresh = true;
        }
        world.getChunks().sendUpdates(this, oldPosition, fullRefresh);
    }

    /**
     * Determines whether this player requires a region update based on their offset from {@link #lastRegion}.
     *
     * @return {@code true} if a region update should be sent.
     */
    public boolean needsRegionUpdate() {
        int deltaX = position.getLocalX(lastRegion);
        int deltaY = position.getLocalY(lastRegion);
        return deltaX <= 15 || deltaX >= 88 || deltaY <= 15 || deltaY >= 88;
    }

    /**
     * Plays a random sound from the given set of {@link Sounds}.
     *
     * @param sounds One or more possible sounds to choose from.
     */
    public void playRandomSound(Sounds... sounds) {
        if (sounds.length == 1) {
            playSound(sounds[0]);
        } else if (sounds.length > 1) {
            playSound(RandomUtils.random(sounds), 0);
        }
    }

    /**
     * Plays a {@link Sounds} enum value immediately.
     *
     * @param sound The sound to play.
     */
    public void playSound(Sounds sound) {
        playSound(sound, 0);
    }

    /**
     * Plays a {@link Sounds} enum value with a delay in game ticks.
     *
     * @param sound The sound to play.
     * @param delayTicks The delay in game ticks before the sound is played.
     */
    public void playSound(Sounds sound, int delayTicks) {
        int delay = (delayTicks * 600) / 30;
        int volume = varpManager.getValue(PersistentVarp.EFFECTS_VOLUME);
        queue(new SoundMessageWriter(sound.getId(), volume, delay));
    }

    /**
     * Serializes all active {@link PotionCountdownTimer} actions to JSON for persistence.
     *
     * @return A {@link JsonArray} representing all active potion timers.
     */
    public JsonArray savePotionsToJson() {
        JsonArray array = new JsonArray();
        for (var timer : actions.getAll(PotionCountdownTimer.class)) {
            array.add(timer.saveJson());
        }
        return array;
    }

    /**
     * Restores all {@link PotionCountdownTimer} actions from the given JSON representation.
     *
     * @param array A {@link JsonArray} created by {@link #savePotionsToJson()}, or {@code null}.
     */
    public void loadPotionsFromJson(JsonArray array) {
        if (array != null) {
            for (JsonElement element : array) {
                PotionCountdownTimer.Companion.loadJson(this, element.getAsJsonObject());
            }
        }
    }

    /**
     * Creates a new {@link DialogueQueueBuilder} for this player with a default capacity.
     *
     * @return A new dialogue builder instance.
     */
    public DialogueQueueBuilder newDialogue() {
        return new DialogueQueueBuilder(this, 10);
    }

    /**
     * Returns the current run energy percentage.
     *
     * @return The run energy (0–100).
     */
    public double getRunEnergy() {
        return runEnergy;
    }

    /**
     * Sets the current run energy percentage and optionally updates the client.
     *
     * @param newRunEnergy The new run energy (will be clamped to [0, 100]).
     * @param update Whether to send an {@link UpdateRunEnergyMessageWriter} to the client.
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
     * Modifies run energy by a delta amount and updates the client.
     *
     * @param amount The amount to add (or subtract) from the current run energy.
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
     * Returns the combined weight of this player's inventory and equipment.
     *
     * @return The combined weight.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Sets the combined weight of this player's inventory and equipment and optionally updates the client.
     *
     * @param newWeight The new weight value.
     * @param update Whether to send an {@link UpdateWeightMessageWriter} to the client.
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
     * Sets the instant when this player will be unmuted.
     *
     * @param unmuteInstant The new unmute instant, or {@code null} to clear.
     */
    public void setUnmuteInstant(Instant unmuteInstant) {
        this.unmuteInstant = unmuteInstant;
    }

    /**
     * Returns the instant when this player will be unmuted.
     *
     * @return The unmute instant, or {@code null} if not muted.
     */
    public Instant getUnmuteInstant() {
        return unmuteInstant;
    }

    /**
     * Sets the instant when this player will be unbanned.
     *
     * @param unbanInstant The new unban instant, or {@code null} to clear.
     */
    public void setUnbanInstant(Instant unbanInstant) {
        this.unbanInstant = unbanInstant;
    }

    /**
     * Returns the instant when this player will be unbanned.
     *
     * @return The unban instant, or {@code null} if not banned.
     */
    public Instant getUnbanInstant() {
        return unbanInstant;
    }

    /**
     * Returns whether this player is currently muted.
     *
     * @return {@code true} if {@link #unmuteInstant} is set and in the future.
     */
    public boolean isMuted() {
        return unmuteInstant != null && !Instant.now().isAfter(unmuteInstant);
    }

    /**
     * Returns this player's rights.
     *
     * @return The current {@link PlayerRights}.
     */
    public PlayerRights getRights() {
        return rights;
    }

    /**
     * Sets this player's rights.
     *
     * @param rights The new rights.
     */
    public void setRights(PlayerRights rights) {
        this.rights = rights;
    }

    /**
     * Returns this player's username.
     *
     * @return The username string.
     */
    public String getUsername() {
        return credentials.getUsername();
    }

    /**
     * Returns the stored hashed password, if any.
     *
     * @return The hashed password, or {@code null}.
     */
    public String getHashedPassword() {
        return hashedPassword;
    }

    /**
     * Sets the hashed password for this player.
     *
     * @param hashedPassword The new hashed password.
     */
    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    /**
     * Sets the plaintext password and clears any previously stored hash.
     *
     * @param password The plaintext password.
     */
    public void setPassword(String password) {
        credentials.setPassword(password);
        hashedPassword = null;
    }

    /**
     * Returns the plaintext password (if stored).
     *
     * @return The plaintext password.
     */
    public String getPassword() {
        return credentials.getPassword();
    }

    /**
     * Returns the hashed username used for efficient lookups.
     *
     * @return The username hash.
     */
    public long getUsernameHash() {
        return credentials.getUsernameHash();
    }

    /**
     * Returns the {@link GameClient} associated with this player.
     *
     * @return The active game client.
     */
    public GameClient getClient() {
        return client;
    }

    /**
     * Sets the {@link GameClient} for this player.
     *
     * <p>
     * This may only be called once; subsequent calls will log a warning and be ignored.
     * </p>
     *
     * @param newClient The new client instance to associate.
     */
    public void setClient(GameClient newClient) {
        if (client != null) {
            logger.warn("GameClient can only be set once.");
            return;
        }
        client = newClient;
    }

    /**
     * Sets whether this player is currently running.
     *
     * <p>
     * This updates both the running varp and the walking queue's running mode.
     * </p>
     *
     * @param running {@code true} to enable running, {@code false} to disable.
     */
    public void setRunning(boolean running) {
        sendVarp(PersistentVarp.RUNNING, running);
        walking.setRunningPath(running);
    }

    /**
     * Returns whether this player is currently running.
     *
     * @return {@code true} if the running varp is set, {@code false} otherwise.
     */
    public boolean isRunning() {
        return varpManager.getValue(PersistentVarp.RUNNING) == 1;
    }

    /**
     * Returns the last known region position used for region-change detection.
     *
     * @return The last region position, or {@code null} if unknown.
     */
    public Position getLastRegion() {
        return lastRegion;
    }

    /**
     * Sets the last known region position for region-change detection.
     *
     * @param lastRegion The new last-region position.
     */
    public void setLastRegion(Position lastRegion) {
        this.lastRegion = lastRegion;
    }

    /**
     * Returns whether this player's region changed during the last update.
     *
     * @return {@code true} if the region changed.
     */
    public boolean isRegionChanged() {
        return regionChanged;
    }

    /**
     * Marks whether this player's region changed during the last update.
     *
     * @param regionChanged The new flag value.
     */
    public void setRegionChanged(boolean regionChanged) {
        this.regionChanged = regionChanged;
    }

    /**
     * Returns the current appearance object for this player.
     *
     * @return The {@link PlayerAppearance}.
     */
    public PlayerAppearance getAppearance() {
        return appearance;
    }

    /**
     * Returns the inventory container.
     *
     * @return The {@link Inventory}.
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Returns the equipment container.
     *
     * @return The {@link Equipment}.
     */
    public Equipment getEquipment() {
        return equipment;
    }

    /**
     * Returns the bank container.
     *
     * @return The {@link Bank}.
     */
    public Bank getBank() {
        return bank;
    }

    /**
     * Returns the current player model animation set.
     *
     * @return The {@link PlayerModelAnimation}.
     */
    public PlayerModelAnimation getModel() {
        return model;
    }

    /**
     * Sets the player model animation set and flags an appearance update.
     *
     * @param model The new model animation set.
     */
    public void setModel(PlayerModelAnimation model) {
        this.model = model;
        flags.flag(UpdateFlag.APPEARANCE);
    }

    /**
     * Returns the active overlay set.
     *
     * @return The {@link AbstractOverlaySet}.
     */
    public AbstractOverlaySet getOverlays() {
        return overlays;
    }

    /**
     * Returns the game tab set for this player.
     *
     * @return The {@link GameTabSet}.
     */
    public GameTabSet getTabs() {
        return tabs;
    }

    /**
     * Clears the current dialogue queue, if any.
     */
    public void resetDialogues() {
        setDialogues(null);
    }

    /**
     * Sets the current dialogue queue.
     *
     * @param dialogues The new dialogue queue, or {@code null}.
     */
    public void setDialogues(DialogueQueue dialogues) {
        this.dialogues = dialogues;
    }

    /**
     * Returns the current dialogue queue.
     *
     * @return The active {@link DialogueQueue}, or {@code null}.
     */
    public DialogueQueue getDialogues() {
        return dialogues;
    }

    /**
     * Returns a new private message id and increments the internal counter.
     *
     * @return A new private message identifier.
     */
    public int newPrivateMessageId() {
        return privateMsgCounter++;
    }

    /**
     * Returns the friend list (username hashes).
     *
     * @return The friend set.
     */
    public Set<Long> getFriends() {
        return friends;
    }

    /**
     * Returns the ignore list (username hashes).
     *
     * @return The ignore set.
     */
    public Set<Long> getIgnores() {
        return ignores;
    }

    /**
     * Returns this player's interaction menu.
     *
     * @return The {@link PlayerInteractionMenu}.
     */
    public PlayerInteractionMenu getInteractions() {
        return interactions;
    }

    /**
     * Returns the current IP address this player is logged in with.
     *
     * @return The current IP address.
     */
    public String getCurrentIp() {
        return client.getIpAddress();
    }

    /**
     * Returns the last IP address this player logged in with.
     *
     * @return The last login IP address, or {@code null}.
     */
    public String getLastIp() {
        return lastIp;
    }

    /**
     * Sets the last IP address this player logged in with.
     *
     * @param lastIp The new last login IP.
     */
    public void setLastIp(String lastIp) {
        this.lastIp = lastIp;
    }

    /**
     * Returns this player's SQL database id, or {@code -1} if not yet assigned.
     *
     * @return The database id.
     */
    public int getDatabaseId() {
        return databaseId;
    }

    /**
     * Sets this player's SQL database id.
     *
     * @param databaseId The new database id.
     */
    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    /**
     * Returns this player's {@link ControllerManager}.
     *
     * @return The controller manager.
     */
    public ControllerManager getControllers() {
        return controllers;
    }

    /**
     * Returns this player's {@link PersistentVarpManager}.
     *
     * @return The varp manager.
     */
    public PersistentVarpManager getVarpManager() {
        return varpManager;
    }

    /**
     * Returns the session stopwatch tracking how long this player has been online.
     *
     * @return The {@link Stopwatch} instance.
     */
    public Stopwatch getTimeOnline() {
        return timeOnline;
    }

    /**
     * Returns the current {@link InteractionTask}, if any.
     *
     * @return The interaction task, or {@code null}.
     */
    public InteractionTask getInteractionTask() {
        return interactionTask;
    }

    /**
     * Sets the current {@link InteractionTask}.
     *
     * @param interactionTask The new interaction task, or {@code null}.
     */
    public void setInteractionTask(InteractionTask interactionTask) {
        this.interactionTask = interactionTask;
    }

    /**
     * Returns the cached varp values keyed by varp id.
     *
     * @return The cached varp map.
     */
    public Map<Integer, Integer> getCachedVarps() {
        return cachedVarps;
    }

    /**
     * Returns the currently active spellbook, defaulting to {@link Spellbook#REGULAR} if {@code null}.
     *
     * @return The active spellbook.
     */
    public Spellbook getSpellbook() {
        if (spellbook == null) {
            spellbook = Spellbook.REGULAR;
        }
        return spellbook;
    }

    /**
     * Updates the active spellbook and optionally refreshes the magic tab.
     *
     * @param newSpellbook The new spellbook, or {@code null} to reset to regular.
     * @param updateTab Whether the magic tab interface should be refreshed.
     */
    public void updateSpellbook(Spellbook newSpellbook, boolean updateTab) {
        if (newSpellbook == null) {
            newSpellbook = Spellbook.REGULAR;
        }
        spellbook = newSpellbook;
        if (updateTab) {
            tabs.reset(TabIndex.MAGIC);
        }
    }

    /**
     * Convenience method to set the active spellbook and refresh the magic tab.
     *
     * @param newSpellbook The new spellbook.
     */
    public void setSpellbook(Spellbook newSpellbook) {
        updateSpellbook(newSpellbook, true);
    }

    /**
     * Sets the instant this account was first created.
     *
     * @param createdAt The creation instant.
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the instant this account was first created.
     *
     * @return The creation instant.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the total amount of time this account has been played.
     *
     * <p>
     * This method updates {@link #timePlayed} by adding the elapsed {@link #timeOnline} duration
     * since the last call, then resets and restarts the stopwatch. Avoid calling this too frequently
     * if you need a stable snapshot of playtime.
     * </p>
     *
     * @return The accumulated time played.
     */
    public Duration getTimePlayed() {
        if (timePlayed == null) {
            timePlayed = Duration.ZERO;
        }
        // Update on the fly.
        timePlayed = timePlayed.plus(timeOnline.elapsed());
        timeOnline.reset().start();
        return timePlayed;
    }

    /**
     * Returns this player's local mob repository used during updating.
     *
     * @return The {@link LocalMobRepository}.
     */
    public LocalMobRepository getLocalMobs() {
        return localMobs;
    }

    /**
     * Sets the total time played stored for this account.
     *
     * @param timePlayed The new total playtime value.
     */
    public void setTimePlayed(Duration timePlayed) {
        this.timePlayed = timePlayed;
    }

    /**
     * Sets this player's privacy options.
     *
     * @param privacyOptions The new privacy options.
     */
    public void setPrivacyOptions(PlayerPrivacy privacyOptions) {
        this.privacyOptions = privacyOptions;
    }

    /**
     * Returns this player's privacy options.
     *
     * @return The privacy options (never {@code null}).
     */
    public PlayerPrivacy getPrivacyOptions() {
        if (privacyOptions == null) {
            privacyOptions = new PlayerPrivacy();
        }
        return privacyOptions;
    }

    /**
     * Returns the current prayer icon for this player.
     *
     * @return The {@link PrayerIcon}.
     */
    public PrayerIcon getPrayerIcon() {
        return prayerIcon;
    }

    /**
     * Sets the prayer icon for this player and flags an appearance update.
     *
     * @param prayerIcon The new prayer icon.
     */
    public void setPrayerIcon(PrayerIcon prayerIcon) {
        this.prayerIcon = prayerIcon;
        flags.flag(UpdateFlag.APPEARANCE);
    }

    /**
     * Returns the current skull icon for this player.
     *
     * @return The {@link SkullIcon}.
     */
    public SkullIcon getSkullIcon() {
        return skullIcon;
    }

    /**
     * Sets the skull icon for this player and flags an appearance update.
     *
     * @param skullIcon The new skull icon.
     */
    public void setSkullIcon(SkullIcon skullIcon) {
        this.skullIcon = skullIcon;
        flags.flag(UpdateFlag.APPEARANCE);
    }
}
