package io.luna.game.model.mob.bot;

import api.bot.action.BotActionHandler;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Sets;
import io.luna.LunaContext;
import io.luna.game.model.EntityState;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.LocalMobRepository;
import io.luna.game.model.mob.block.PlayerAppearance;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.bot.io.BotClient;
import io.luna.game.model.mob.bot.io.BotInputMessageHandler;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler;
import io.luna.game.model.mob.bot.movement.BotMovementStack;
import io.luna.game.model.mob.bot.script.BotScriptStack;
import io.luna.game.persistence.BotData;
import io.luna.game.persistence.PlayerData;
import io.luna.net.msg.GameMessageWriter;
import io.luna.util.RandomUtils;
import io.luna.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a fully autonomous {@link Player} controlled entirely by the Luna server.
 * <p>
 * Bots simulate human players without consuming network bandwidth, allowing them to participate in the world
 * economy, combat, and skilling systems. Each bot executes its own script logic stack, maintains an internal log for
 * debugging, and may interact with real players or other bots through shared systems such as speech and trading.
 * <p>
 * All bot logic is processed server-side on the game thread to ensure consistent world state. Asynchronous operations
 * (such as login or data saving) are dispatched through the game’s various thread pools.
 *
 * @author lare96
 */
public final class Bot extends Player {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A builder for constructing {@link Bot} instances with optional configuration.
     */
    public static final class Builder {

        /**
         * Generates a random, non-secure password for bot credentials. Used exclusively for programmatic bot creation.
         *
         * @return The generated password.
         */
        private static String generatePassword() {
            int minLength = 6;
            int maxLength = 8;
            int length = ThreadLocalRandom.current().nextInt(minLength, maxLength);
            StringBuilder sb = new StringBuilder(length);
            for (int count = 0; count < length; count++) {
                char next = RandomUtils.random(StringUtils.VALID_CHARACTERS);
                if (ThreadLocalRandom.current().nextBoolean()) {
                    sb.append(Character.toUpperCase(next));
                } else {
                    sb.append(next);
                }
            }
            return sb.toString();
        }

        private final LunaContext context;
        private boolean temporary;
        private String username;

        /**
         * Creates a new {@link Builder}.
         *
         * @param context The game context.
         */
        public Builder(LunaContext context) {
            this.context = context;
        }

        /**
         * Sets a username, marking this bot as persistent.
         *
         * @param username The desired username.
         * @return This builder instance.
         * @throws IllegalStateException If another bot with this username is already online.
         */
        public Builder setUsername(String username) {
            BotRepository repository = context.getWorld().getBots();
            if (repository.isOnline(username)) {
                throw new IllegalStateException("Bot is already logged in.");
            }
            this.username = username;
            return this;
        }

        /**
         * Marks this bot as temporary (non-persistent).
         *
         * @return This builder instance.
         */
        public Builder setTemporary() {
            temporary = true;
            return this;
        }

        /**
         * Constructs a fully initialized {@link Bot} instance with generated credentials.
         *
         * @return The new {@link Bot}.
         * @throws IllegalStateException If no username was set.
         */
        public Bot build() {
            if (username == null) {
                throw new IllegalStateException("Username was not set.");
            }
            return new Bot(context, username, generatePassword(), temporary);
        }
    }

    /**
     * The log manager responsible for recording and exporting debug messages.
     */
    private final BotLogManager logManager = new BotLogManager(this);

    /**
     * The global bot manager coordinating all active bots.
     */
    private final BotManager manager;

    /**
     * The script execution stack.
     */
    private final BotScriptStack scriptStack;

    /**
     * The movement queue and pathing controller.
     */
    private final BotMovementStack movementStack;

    /**
     * The set of visible human players currently within this bot’s viewport.
     */
    private final Set<Player> localHumans = Sets.newConcurrentHashSet();

    /**
     * The simulated client connection for this bot.
     */
    private final BotClient botClient;

    /**
     * The handler for direct actions and command execution.
     */
    private final BotActionHandler actionHandler = new BotActionHandler(this);

    /**
     * Whether this bot is temporary (non-persistent).
     */
    private boolean temporary;

    /**
     * Internal runtime counter for number of processed game cycles.
     */
    private long cycles;

    /**
     * Creates a new {@link Bot}.
     *
     * @param context The context instance.
     * @param username The username.
     * @param password The password.
     * @param temporary Whether this bot is temporary (non-persistent).
     */
    private Bot(LunaContext context, String username, String password, boolean temporary) {
        super(context, new PlayerCredentials(username, password));
        this.temporary = temporary;

        botClient = new BotClient(this, context.getServer().getMessageRepository());
        manager = world.getBotManager();
        scriptStack = new BotScriptStack(this, manager.getScriptManager());
        movementStack = new BotMovementStack(this, manager.getMovementManager());
        setClient(botClient);
    }

    @Override
    public void queue(GameMessageWriter msg) {
        botClient.queue(msg);
    }

    @Override
    protected void onInactive() {
        super.onInactive();
    }

    @Override
    public void forceLogout() {
        scriptStack.shutdown();
        botClient.onInactive();
    }

    @Override
    public PlayerData createSaveData() {
        return new BotData(getUsername());
    }

    @Override
    public LocalMobRepository getLocalMobs() {
        throw new IllegalStateException("Bots are not updated like regular players. Use getLocalHumans() instead.");
    }

    /**
     * Asynchronously attempts to log in this bot, creating a new record if it does not exist.
     *
     * @return A {@link CompletableFuture} resolving to the loaded {@link PlayerData}, or completing exceptionally
     * if the login fails.
     */
    public CompletableFuture<PlayerData> login() {
        CompletableFuture<PlayerData> future = new CompletableFuture<>();
        String username = getUsername();
        if(world.isFull()) {
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
                    if (data != null) {
                        temporary = false;
                    }
                    loadData(data);
                    world.getBots().add(this);
                    world.getPlayers().add(this);
                    setState(EntityState.ACTIVE);
                    log("I'm alive!");
                    logger.info("{} has logged in.", username);
                    return data;
                }, service.getGameExecutor());
    }

    /**
     * Executes one processing cycle for this bot.
     * <p>
     * This method advances the bot’s script stack, processes contextual injections, and increments the internal
     * cycle counter.
     */
    public void process() {
        try {
            scriptStack.process();
            manager.getInjectorManager().injectEvents(this);
        } finally {
            cycles++;
        }
    }

    /**
     * Appends a message to this bot’s internal log buffer.
     *
     * @param text The message text.
     */
    public void log(String text) {
        logManager.log(text);
    }

    /**
     * Randomizes the bot’s appearance, skills, and equipped items.
     * <p>
     * Primarily used for debugging, testing, or filler bot generation.
     */
    public void randomize() {
        randomizeAppearance();
        randomizeSkills();
        randomizeEquipment();
    }

    /**
     * Randomizes the bot’s visible appearance.
     */
    public void randomizeAppearance() {
        getAppearance().setValues(PlayerAppearance.random());
        getFlags().flag(UpdateFlag.APPEARANCE);
    }

    /**
     * Randomizes the bot’s skill levels.
     */
    public void randomizeSkills() {
        for (Skill skill : skills) {
            int level = ThreadLocalRandom.current().nextBoolean()
                    ? ThreadLocalRandom.current().nextInt(1, 100)
                    : ThreadLocalRandom.current().nextInt(50, 100);
            skill.setStaticLevel(level);
        }
    }

    /**
     * Randomizes the bot’s currently equipped items.
     */
    public void randomizeEquipment() {
        ArrayListMultimap<Integer, EquipmentDefinition> eligible = ArrayListMultimap.create();
        EquipmentDefinition.ALL.lookupAll(def -> def.meetsAllRequirements(this))
                .forEach(def -> eligible.put(def.getIndex(), def));

        for (int index = 0; index < getEquipment().capacity(); index++) {
            EquipmentDefinition def = RandomUtils.random(eligible.get(index));
            if (def != null) {
                getEquipment().set(index, new Item(def.getId()));
            }
        }
    }

    /**
     * @return {@code true} if this bot is temporary (non-persistent).
     */
    public boolean isTemporary() {
        return temporary;
    }

    /**
     * @return The {@link BotClient} simulating this bot’s network connection.
     */
    public BotClient getBotClient() {
        return botClient;
    }

    /**
     * @return The total number of cycles this bot has processed.
     */
    public long getCycles() {
        return cycles;
    }

    /**
     * @return The input message handler for simulated client packets.
     */
    public BotInputMessageHandler getInput() {
        return botClient.getInput();
    }

    /**
     * @return The output message handler for simulated server packets.
     */
    public BotOutputMessageHandler getOutput() {
        return botClient.getOutput();
    }

    /**
     * @return A concurrent set of human players currently visible to this bot.
     */
    public Set<Player> getLocalHumans() {
        return localHumans;
    }

    /**
     * @return The log manager responsible for tracking bot activity.
     */
    public BotLogManager getLogManager() {
        return logManager;
    }

    /**
     * @return The global {@link BotManager} that oversees all bots.
     */
    public BotManager getManager() {
        return manager;
    }

    /**
     * @return The active {@link BotScriptStack} managing this bot’s behavior scripts.
     */
    public BotScriptStack getScriptStack() {
        return scriptStack;
    }

    /**
     * @return The {@link BotMovementStack} controlling this bot’s pathfinding and movement.
     */
    public BotMovementStack getMovementStack() {
        return movementStack;
    }

    /**
     * @return The {@link BotActionHandler} for direct action processing.
     */
    public BotActionHandler getActionHandler() {
        return actionHandler;
    }
}