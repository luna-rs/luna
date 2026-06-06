package io.luna.game.model.mob.bot;

import api.bot.action.BotActionHandler;
import api.bot.zone.SubZone;
import api.bot.zone.Zone;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import game.bot.scripts.LootItemBotScript;
import io.luna.Luna;
import io.luna.LunaContext;
import io.luna.game.model.EntityState;
import io.luna.game.model.Position;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.block.LocalMobRepository;
import io.luna.game.model.mob.block.PlayerAppearance;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.bot.brain.BotBrain;
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator;
import io.luna.game.model.mob.bot.brain.BotEmotion;
import io.luna.game.model.mob.bot.brain.BotPersonality;
import io.luna.game.model.mob.bot.brain.BotPreference;
import io.luna.game.model.mob.bot.brain.BotReflex;
import io.luna.game.model.mob.bot.io.BotClient;
import io.luna.game.model.mob.bot.io.BotInputMessageHandler;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler;
import io.luna.game.model.mob.bot.script.BotScriptStack;
import io.luna.game.model.mob.bot.speech.BotSpeechStack;
import io.luna.game.persistence.PlayerData;
import io.luna.net.msg.GameMessageWriter;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A fully autonomous {@link Player} controlled by server-side bot systems instead of a real client connection.
 * <p>
 * From the game world's perspective, a bot is still a normal player. It can move, skill, fight, trade, speak,
 * receive packets, send packets, save data, and participate in regular gameplay systems. The difference is that its
 * client is simulated by {@link BotClient}, and its decisions are driven by bot-specific behavior layers.
 * <p>
 * The main bot behavior stack is split into several cooperating systems:
 * <ul>
 *     <li>{@link BotReflex}, for immediate interrupt-style reactions.</li>
 *     <li>{@link BotSpeechStack}, for queued, contextual, and randomized speech.</li>
 *     <li>{@link BotScriptStack}, for active scripted behavior such as skilling or combat.</li>
 *     <li>{@link BotBrain}, for choosing new work when no script is currently active.</li>
 *     <li>{@link BotPersonality}, {@link BotPreference}, and {@link BotEmotion}, for long-term behavioral flavor.</li>
 * </ul>
 * This lets Luna treat bots as lightweight simulated players while keeping their behavior extensible and debuggable.
 *
 * @author lare96
 */
public final class Bot extends Player {

    // TODO@0.5.0 Optional debugging mode for bots that allows player updating processing (for stress testing).

    /**
     * The logger used for bot lifecycle events and diagnostic messages.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Builds {@link Bot} instances with optional overrides for spawn position, persistence, and behavior systems.
     * <p>
     * The builder keeps simple bot creation small while still allowing tests, scripted scenarios, and specialized bot
     * types to provide custom behavior models. Any subsystem not explicitly configured is created during {@link #build()}.
     */
    public static final class Builder {

        private final LunaContext context;
        private final BotManager manager;
        private boolean temporary;
        private String username;
        private Position spawnPosition = Luna.settings().game().startingPosition();
        private BotReflex reflex;
        private BotBrain brain;
        private BotPersonality personality;
        private BotPreference preferences;

        /**
         * Creates a new bot builder bound to the supplied Luna context.
         *
         * @param context The Luna context used to access the world and bot manager.
         */
        public Builder(LunaContext context) {
            this.context = context;
            manager = context.getWorld().getBotManager();
        }

        /**
         * Sets the username assigned to the bot.
         * <p>
         * This only checks the active bot repository. Persistent data may still be loaded later during {@link Bot#login()}.
         *
         * @param username The username to assign.
         * @return This builder.
         * @throws IllegalStateException If an active bot with this username is already online.
         */
        public Builder setUsername(String username) {
            // TODO apply same name filtering from login
            BotRepository repository = context.getWorld().getBots();
            if (repository.isOnline(username)) {
                throw new IllegalStateException("Bot is already logged in.");
            }
            this.username = username;
            return this;
        }

        /**
         * Sets the fallback spawn position for the bot.
         * <p>
         * Persistent bots continue from their saved location when data exists. This position is only used when the bot has
         * no saved player data, or when the bot is temporary and therefore skips normal persistence loading.
         *
         * @param spawnPosition The fallback spawn position.
         * @return This builder.
         * @throws NullPointerException If {@code spawnPosition} is {@code null}.
         */
        public Builder setSpawnPosition(Position spawnPosition) {
            this.spawnPosition = requireNonNull(spawnPosition);
            return this;
        }

        /**
         * Marks the bot as temporary.
         * <p>
         * Temporary bots do not load existing data and return {@code null} from {@link Bot#createSaveData()}, allowing the
         * normal player persistence pipeline to ignore them.
         *
         * @return This builder.
         */
        public Builder setTemporary() {
            temporary = true;
            return this;
        }

        /**
         * Sets the reflex model used by the bot.
         * <p>
         * Reflex processing runs before speech, event injection, script processing, and brain coordination. It is intended
         * for immediate behavior such as danger avoidance, emergency recovery, or other interrupt-style decisions.
         *
         * @param reflex The reflex model to assign.
         * @return This builder.
         */
        public Builder setReflex(BotReflex reflex) {
            this.reflex = reflex;
            return this;
        }

        /**
         * Sets the brain model used by the bot.
         * <p>
         * The brain is consulted when the script stack has no remaining work and the bot needs to choose a new activity.
         *
         * @param brain The brain model to assign.
         * @return This builder.
         */
        public Builder setBrain(BotBrain brain) {
            this.brain = brain;
            return this;
        }

        /**
         * Sets the personality profile used by the bot.
         * <p>
         * Personality represents long-term behavioral flavor. It may affect generated preferences, emotions, speech style,
         * activity selection, reaction speed, risk tolerance, and other bot-specific behavior systems.
         *
         * @param personality The personality profile to assign.
         * @return This builder.
         */
        public Builder setPersonality(BotPersonality personality) {
            this.personality = personality;
            return this;
        }

        /**
         * Sets the preference model used by the bot.
         * <p>
         * Preferences describe what the bot tends to enjoy, avoid, or prioritize when choosing activities. If omitted, a
         * smart randomized preference model is generated from the configured personality.
         *
         * @param preferences The preference model to assign.
         * @return This builder.
         */
        public Builder setPreferences(BotPreference preferences) {
            this.preferences = preferences;
            return this;
        }

        /**
         * Constructs a fully initialized {@link Bot} with generated credentials and default behavior subsystems.
         * <p>
         * Any missing subsystem is created here. The default profile includes a new {@link BotReflex}, a new
         * {@link BotBrain}, a smart randomized {@link BotPersonality}, a new {@link BotEmotion}, and a smart randomized
         * {@link BotPreference}. A username is required because bots still use the normal player credential and persistence
         * pipeline.
         *
         * @return The constructed bot.
         * @throws IllegalStateException If no username was configured before building.
         */
        public Bot build() {
            if (username == null) {
                throw new IllegalStateException("Username was not set.");
            }
            if (reflex == null) {
                reflex = new BotReflex();
            }
            if (brain == null) {
                brain = new BotBrain();
            }
            if (personality == null) {
                personality = new BotPersonality.Builder(manager.getPersonalityManager()).randomizeSmart().build();
            }
            if (preferences == null) {
                preferences = new BotPreference.Builder(manager.getPersonalityManager(), personality).randomizeSmart()
                        .build();
            }
            return new Bot(context, username, "lunaisthebest1997", reflex, brain, personality, preferences,
                    spawnPosition, temporary);
        }
    }

    /**
     * The manager that records and exports this bot's debug log messages.
     */
    private final BotLogManager logManager = new BotLogManager(this);

    /**
     * The global bot manager that owns active bot registries and shared bot subsystem managers.
     */
    private final BotManager manager;

    /**
     * The active script stack that drives scripted bot behavior.
     */
    private final BotScriptStack scriptStack;

    /**
     * The human players currently visible to this bot.
     * <p>
     * Bots do not use the normal player local-mob update repository, so human visibility is tracked separately.
     */
    private final Set<Player> localHumans = Sets.newConcurrentHashSet();

    /**
     * The simulated client connection backing this bot.
     */
    private final BotClient botClient;

    /**
     * The direct action handler used by scripts and behavior systems to interact with the game world.
     */
    private final BotActionHandler actionHandler = new BotActionHandler(this);

    /**
     * The fallback spawn position used when the bot has no saved position data.
     */
    private final Position spawnPosition;

    /**
     * Whether this bot skips normal persistence loading and saving.
     */
    private final boolean temporary;

    /**
     * The number of bot processing cycles completed since construction.
     */
    private long cycles;

    /**
     * The reflex model used for immediate interrupt-style behavior.
     */
    private final BotReflex reflex;

    /**
     * The brain model used to choose new work when the script stack is idle.
     */
    private final BotBrain brain;

    /**
     * The bot's current emotional state model.
     */
    private final BotEmotion emotions = new BotEmotion(this);

    /**
     * The speech stack responsible for queued, contextual, and randomized bot chat.
     */
    private final BotSpeechStack speechStack;

    /**
     * The bot's long-term personality profile.
     */
    private BotPersonality personality;

    /**
     * The bot's long-term activity and behavior preferences.
     */
    private final BotPreference preferences;

    /**
     * The bot's current zone.
     */
    private Zone zone;

    /**
     * The bot's current sub-zone.
     */
    private SubZone subZone;

    /**
     * Local sub-zones in the bots current region.
     */
    private ImmutableSet<SubZone> localSubZones = ImmutableSet.of();

    private final Set<GroundItem> viewableItems = new HashSet<>();

    /**
     * Creates a new {@link Bot}.
     * <p>
     * Construction wires the bot to a simulated client and initializes its script and speech stacks. The bot is not added
     * to the world until {@link #login()} succeeds.
     *
     * @param context The Luna context used to access world and server services.
     * @param username The bot username.
     * @param password The generated bot password.
     * @param reflex The reflex model used for immediate behavior checks.
     * @param brain The brain model used for high-level activity selection.
     * @param personality The personality profile assigned to the bot.
     * @param preferences The preference model assigned to the bot.
     * @param position The fallback spawn position used when no saved position exists.
     * @param temporary Whether this bot should skip normal persistence.
     */
    private Bot(LunaContext context, String username, String password, BotReflex reflex, BotBrain brain,
                BotPersonality personality, BotPreference preferences, Position position, boolean temporary) {
        super(context, new PlayerCredentials(username, password));
        this.reflex = reflex;
        this.brain = brain;
        this.personality = personality;
        this.preferences = preferences;
        this.temporary = temporary;

        spawnPosition = position;
        botClient = new BotClient(this, context.getServer().getMessageRepository());
        manager = world.getBotManager();
        scriptStack = new BotScriptStack(this, manager.getScriptManager());
        speechStack = new BotSpeechStack(this);
        setClient(botClient);
    }

    /**
     * Queues an outbound game message through the bot's simulated client.
     * <p>
     * This preserves the normal {@link Player#queue(GameMessageWriter)} contract while allowing bots to receive outbound
     * messages without a real network socket.
     */
    @Override
    public void queue(GameMessageWriter msg) {
        botClient.queue(msg);
    }

    @Override
    protected void onActive() {
        super.onActive();
        reflex.add(new LootItemBotScript(this));
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        scriptStack.shutdown();
    }

    @Override
    public void forceLogout() {
        scriptStack.shutdown();
        botClient.onInactive();
    }

    @Override
    public PlayerData createSaveData() {
        // Temporary bots don't create any data.
        return temporary ? null : new PlayerData(getUsername()).save(this);
    }

    /**
     * Gets the regular local mob repository for this player.
     * <p>
     * Bots do not use the normal local-player viewport pipeline because they are not updated like regular players, so
     * this method is unsupported. Use {@link #getLocalHumans()} for bot-specific human visibility tracking.
     *
     * @return This method never returns normally.
     * @throws IllegalStateException Always thrown because bots do not use a regular local mob repository.
     */
    @Override
    public LocalMobRepository getLocalMobs() {
        throw new IllegalStateException("Bots are not updated like regular players. Use getLocalHumans() instead.");
    }

    /**
     * Asynchronously logs this bot into the world.
     * <p>
     * Login fails if the world is full, another player with the same username is online, or the previous save for this
     * username is still pending. If persistent data exists, it is loaded through the normal player data pipeline. If no
     * data exists, or if this is a temporary bot, the bot starts at its configured spawn position.
     *
     * @return A future completed with loaded data, {@code null} for a new or temporary bot, or completed exceptionally
     * if login validation fails.
     */
    public CompletableFuture<PlayerData> login() {
        CompletableFuture<PlayerData> future = new CompletableFuture<>();
        String username = getUsername();
        if (world.isFull()) {
            future.completeExceptionally(new IllegalStateException("World is full."));
            return future;
        }
        if (world.getPlayerMap().containsKey(username)) {
            future.completeExceptionally(new IllegalStateException("Bot is already logged in!"));
            return future;
        }
        if (world.getLogoutService().isSavePending(username)) {
            future.completeExceptionally(new IllegalStateException("Bot data is still being saved!"));
            return future;
        }
        // Load data for bot based on username.
        return world.getPersistenceService().load(username).
                thenApplyAsync(data -> {
                    if (temporary) {
                        data = null;
                    }
                    loadData(data);
                    if (data == null) {
                        position = spawnPosition;
                    }
                    if (world.getPlayers().add(this)) {
                        world.getBots().add(this);
                        setState(EntityState.ACTIVE);
                        log("I'm alive!");
                        logger.info("{} has logged in.", username);
                        return data;
                    }
                    return data;
                }, service.getGameExecutor());
    }

    /**
     * Processes one game cycle of bot behavior.
     * <p>
     * Bot processing is intentionally layered from highest-priority interruption to lowest-priority planning:
     * <ol>
     *     <li>Run reflex behavior for immediate decisions.</li>
     *     <li>Process speech so the bot can speak while active.</li>
     *     <li>Inject contextual events from the bot injector manager.</li>
     *     <li>Process the active script stack.</li>
     *     <li>Ask the brain for a new coordinator when no script work remains.</li>
     * </ol>
     * The cycle counter is incremented in a {@code finally} block so failed or interrupted processing attempts are still
     * reflected in diagnostics.
     */
    public void process() {
        try {
            // TODO@1.0 Dumber bots have delayed processing cycles? Which means slower reaction time, etc.
            // Process speech before scripts so we can still talk while doing anything.
            speechStack.process();

            // First process any instincts our bot has.
            if (reflex.process(this)) {
                // Process context injectors before scripts so we can still react to events while doing stuff.
                manager.getInjectorManager().injectEvents(this);

                // Short-circuit if we still have stuff to do (scripts still in buffer).
                if (!scriptStack.process()) {
                    return;
                }

                // No scripts in buffer, consult brain for something to do.
                BotCoordinator coordinator = brain.process(this);
                if (coordinator != null) {
                    coordinator.accept(this);
                }
            }
        } finally {
            cycles++;
        }
    }

    /**
     * Appends a message to this bot's internal debug log.
     *
     * @param text The message text to record.
     */
    public void log(String text) {
        logManager.log(text);
    }

    /**
     * Randomizes this bot's appearance, skill levels, and equipment.
     * <p>
     * This is mostly useful for development, stress testing, filler bot generation, or quickly creating visually distinct
     * bots without hand-authoring every profile.
     */
    public void randomize() {
        randomizeAppearance();
        randomizeSkills();
        randomizeEquipment();
    }

    /**
     * Randomizes the bot's visible appearance and flags the appearance block for updating.
     */
    public void randomizeAppearance() {
        getAppearance().setValues(PlayerAppearance.random());
        getFlags().flag(UpdateFlag.APPEARANCE);
    }

    /**
     * Randomizes every skill to a semi-random static and current level.
     * <p>
     * Each skill is assigned either a broad level range or a higher-level biased range. Static and current levels are kept
     * equal so the bot does not start boosted or drained.
     */
    public void randomizeSkills() {
        for (Skill skill : skills) {
            int level = ThreadLocalRandom.current().nextBoolean()
                    ? ThreadLocalRandom.current().nextInt(1, 100)
                    : ThreadLocalRandom.current().nextInt(50, 100);
            skill.setStaticLevel(level);
            skill.setLevel(level);
        }
    }

    /**
     * Randomizes the bot's equipment using every equipment definition the bot can currently wear.
     */
    public void randomizeEquipment() {
        randomizeEquipment(def -> true);
    }

    /**
     * Randomizes the bot's equipment while applying an additional eligibility filter.
     * <p>
     * For each equipment slot, this method gathers definitions assigned to that slot, keeps only definitions the bot meets
     * requirements for and that satisfy {@code filter}, then equips one random eligible item for the slot.
     *
     * @param filter Additional predicate used to restrict eligible equipment definitions.
     */
    public void randomizeEquipment(Predicate<EquipmentDefinition> filter) {
        List<EquipmentDefinition> eligible = new ArrayList<>();
        for (int index = 0; index < getEquipment().capacity(); index++) {
            List<EquipmentDefinition> defList = EquipmentDefinition.INDEXES.get(index);
            for (EquipmentDefinition def : defList) {
                if (def.meetsAllRequirements(this) && filter.test(def)) {
                    eligible.add(def);
                }
            }

            EquipmentDefinition def = RandomUtils.random(eligible);
            if (def != null) {
                Item equipItem = new Item(def.getId());
                getEquipment().set(index, equipItem);
            }
            eligible.clear();
        }
    }

    /**
     * Sets every skill's static and current level to {@code 99}.
     */
    public void maxSkills() {
        for (Skill skill : skills) {
            skill.setStaticLevel(99);
            skill.setLevel(99);
        }
    }

    /**
     * @return {@code true} if this bot is temporary, otherwise {@code false}.
     */
    public boolean isTemporary() {
        return temporary;
    }

    /**
     * @return The bot client.
     */
    public BotClient getBotClient() {
        return botClient;
    }

    /**
     * @return The total processed cycle count.
     */
    public long getCycles() {
        return cycles;
    }

    /**
     * @return The bot input handler.
     */
    public BotInputMessageHandler getInput() {
        return botClient.getInput();
    }

    /**
     * @return The bot output handler.
     */
    public BotOutputMessageHandler getOutput() {
        return botClient.getOutput();
    }

    /**
     * Gets the concurrent set of human players currently visible to this bot.
     * <p>
     * This replaces normal player local-mob tracking for bots.
     *
     * @return The visible human player set.
     */
    public Set<Player> getLocalHumans() {
        return localHumans;
    }

    /**
     * @return The bot log manager.
     */
    public BotLogManager getLogManager() {
        return logManager;
    }

    /**
     * @return The bot manager.
     */
    public BotManager getManager() {
        return manager;
    }

    /**
     * @return The bot script stack.
     */
    public BotScriptStack getScriptStack() {
        return scriptStack;
    }

    /**
     * @return The bot action handler.
     */
    public BotActionHandler getActionHandler() {
        return actionHandler;
    }

    /**
     * @return The bot speech stack.
     */
    public BotSpeechStack getSpeechStack() {
        return speechStack;
    }

    /**
     * @return The bot brain.
     */
    public BotBrain getBrain() {
        return brain;
    }

    /**
     * @return The bot emotion model.
     */
    public BotEmotion getEmotions() {
        return emotions;
    }

    /**
     * @return The bot personality.
     */
    public BotPersonality getPersonality() {
        return personality;
    }

    /**
     * Sets the bot's personality profile.
     * <p>
     * This changes future behavior decisions that consult personality, but it does not automatically rebuild derived
     * systems such as preferences unless those systems do that internally.
     *
     * @param personality The new personality profile.
     */
    public void setPersonality(BotPersonality personality) {
        this.personality = personality;
        emotions.clear();
    }

    /**
     * @return The bot reflex model.
     */
    public BotReflex getReflex() {
        return reflex;
    }

    /**
     * @return The bot preference model.
     */
    public BotPreference getPreferences() {
        return preferences;
    }

    /**
     * @return The bot's current {@link Zone}, or {@code null} if no zone has been assigned.
     */
    public Zone getZone() {
        return zone;
    }

    /**
     * Sets the main zone this bot is currently assigned to.
     *
     * @param zone The new {@link Zone}, or {@code null} to clear the current zone.
     */
    public void setZone(Zone zone) {
        this.zone = zone;
    }

    /**
     * @return The bot's current {@link SubZone}, or {@code null} if no sub-zone has been assigned.
     */
    public SubZone getSubZone() {
        return subZone;
    }

    /**
     * Sets the sub-zone this bot is currently assigned to.
     *
     * @param subZone The new {@link SubZone}, or {@code null} to clear the current sub-zone.
     */
    public void setSubZone(SubZone subZone) {
        this.subZone = subZone;
    }

    /**
     * @return An immutable list of local {@link SubZone}s.
     */
    public ImmutableSet<SubZone> getLocalSubZones() {
        return localSubZones;
    }

    /**
     * Sets the sub-zones currently considered local to this bot.
     *
     * @param localSubZones The immutable list of local {@link SubZone}s.
     */
    public void setLocalSubZones(ImmutableSet<SubZone> localSubZones) {
        this.localSubZones = localSubZones;
    }

    // todo test, docs
    public Set<GroundItem> getViewableItems() { // faster than using the locator
        return viewableItems;
    }
}
