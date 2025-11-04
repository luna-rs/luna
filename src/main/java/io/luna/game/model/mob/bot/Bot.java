package io.luna.game.model.mob.bot;

import api.bot.action.BotActionHandler;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import io.luna.LunaContext;
import io.luna.game.model.EntityState;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerAppearance;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A {@link Player} implementation that represents a fully autonomous artificial player (bot) managed and processed
 * entirely by the server. Bots occupy minimal network resources but consume additional CPU resources for processing.
 *
 * @author lare96
 */
public final class Bot extends Player {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A builder class for constructing {@link Bot} instances with optional customization.
     */
    public static final class Builder {

        private final LunaContext context;
        private boolean temporary = true;
        private String username;

        /**
         * Creates a new {@link Builder}.
         *
         * @param context The Luna context.
         */
        public Builder(LunaContext context) {
            this.context = context;
        }

        /**
         * Sets a custom username, marking this bot as persistent.
         *
         * @param username The bot’s username.
         * @return This builder instance.
         * @throws IllegalStateException If the username is already taken by another temporary bot.
         */
        public Builder setUsername(String username) throws IllegalArgumentException {
            BotRepository repository = context.getWorld().getBots();
            if (repository.containsTemporary(username)) {
                throw new IllegalStateException("Username is already in use by a temporary bot.");
            }
            this.username = username;
            temporary = false;
            return this;
        }

        /**
         * Marks this bot as persistent.
         *
         * @return This builder instance.
         */
        public Builder setPersistent() {
            temporary = false;
            return this;
        }

        /**
         * Builds and returns a fully constructed {@link Bot} instance. A random password will be generated.
         *
         * @return A new {@link Bot} instance.
         */
        public Bot build() {
            if (username == null) {
                throw new IllegalStateException("Username was not configured.");
            }
            return new Bot(context, username, BotCredentials.generatePassword(), temporary);
        }
    }

    /**
     * The log manager.
     */
    private final BotLogManager logManager = new BotLogManager(this);

    /**
     * The global bot manager.
     */
    private final BotManager manager;

    /**
     * The script stack.
     */
    private final BotScriptStack scriptStack;

    /**
     * The movement stack.
     */
    private final BotMovementStack movementStack;

    /**
     * A concurrent set of human players within the bot’s local viewport.
     */
    private final Set<Player> localHumans = Sets.newConcurrentHashSet();

    /**
     * The bot client.
     */
    private final BotClient botClient;

    /**
     * The bot action handler.
     */
    private final BotActionHandler actionHandler = new BotActionHandler(this);

    /**
     * Whether this bot is temporary and will be forgotten after logout.
     */
    private final boolean temporary;

    /**
     * The number of processing cycles completed by this bot. This acts as an internal runtime counter used for
     * timing operations.
     */
    private long cycles;

    /**
     * Whether this bot is attempting to log out (e.g., due to a schedule trigger).
     */
    private boolean logoutReady;

    /**
     * Creates a new {@link Bot}.
     *
     * @param context The context instance.
     * @param username The username.
     * @param password The password.
     * @param temporary Whether this bot is attempting to log out (e.g., due to a schedule trigger).
     */
    private Bot(LunaContext context, String username, String password, boolean temporary) {
        super(context, new PlayerCredentials(username, password));
        this.temporary = temporary;

        botClient = new BotClient(this, context.getServer().getMessageRepository());
        manager = world.getBotManager();
        scriptStack = new BotScriptStack(this, manager.getScriptManager());
        movementStack = new BotMovementStack();
        setClient(botClient);
    }

    @Override
    public void queue(GameMessageWriter msg) {
        botClient.queue(msg);
    }

    @Override
    protected void onInactive() {
        world.getBots().remove(this);
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

    /**
     * Attempts to asynchronously log in this bot. If the bot’s credentials do not exist, a new account will be created.
     *
     * @return A {@link ListenableFuture} representing the async login operation. If the login fails, the generated
     * username will not be consumed.
     */
    public CompletableFuture<PlayerData> login() {
        CompletableFuture<PlayerData> future = new CompletableFuture<>();
        String username = getUsername();
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
                    if (data != null && temporary) {
                        data = null;
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
     * Processes one cycle for this bot. Increments the internal cycle counter.
     *
     * @throws Exception If any error occurs during processing.
     */
    public void process() throws Exception {
        try {
            // todo context injectors
            scriptStack.process();
        } finally {
            cycles++;
        }
    }

    /**
     * Writes a line to this bot’s internal log.
     *
     * @param text The text to log.
     */
    public void log(String text) {
        logManager.log(text);
    }

    /**
     * Randomizes the appearance, skills, and equipment of this bot.
     */
    public void randomize() {
        randomizeAppearance();
        randomizeSkills();
        randomizeEquipment();
    }

    /**
     * Randomizes the bot’s visual appearance.
     */
    public void randomizeAppearance() {
        getAppearance().setValues(PlayerAppearance.randomValues());
        getFlags().flag(UpdateFlag.APPEARANCE);
    }

    /**
     * Randomizes the skills of this bot.
     */
    public void randomizeSkills() {
        for (Skill skill : skills) {
            int level = ThreadLocalRandom.current().nextBoolean() ?
                    ThreadLocalRandom.current().nextInt(1, 100) :
                    ThreadLocalRandom.current().nextInt(50, 100);
            skill.setStaticLevel(level);
        }
    }

    /**
     * Randomizes the equipment this bot is wearing.
     */
    public void randomizeEquipment() {
        Multimap<Integer, EquipmentDefinition> sorted = ArrayListMultimap.create();
        EquipmentDefinition.ALL.lookupAll(def -> def.meetsAllRequirements(this)).forEach(def -> sorted.put(def.getIndex(), def));
        for (var entry : sorted.asMap().entrySet()) {
            int index = entry.getKey();
            List<EquipmentDefinition> equipmentList = new ArrayList<>(entry.getValue());

            getEquipment().set(index, new Item(RandomUtils.random(equipmentList).getId()));
        }
    }

    /**
     * @return {@code true} if this bot is temporary (non-persistent).
     */
    public boolean isTemporary() {
        return temporary;
    }

    /**
     * @return The {@link BotClient} instance managing this bot’s input/output.
     */
    public BotClient getBotClient() {
        return botClient;
    }

    /**
     * @return The total number of processing cycles completed by this bot.
     */
    public long getCycles() {
        return cycles;
    }

    /**
     * @return {@code true} if this bot has a scheduled logout pending.
     */
    public boolean isLogoutReady() {
        return logoutReady;
    }

    /**
     * Sets whether this bot should attempt to log out due to a schedule trigger.
     *
     * @param logoutReady {@code true} if this bot should begin logout.
     */
    public void setLogoutReady(boolean logoutReady) {
        this.logoutReady = logoutReady;
    }

    /**
     * @return The input message handler for this bot.
     */
    public BotInputMessageHandler getInput() {
        return botClient.getInput();
    }

    /**
     * @return The output message handler for this bot.
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
     * @return The log manager responsible for recording and debugging bot activity.
     */
    public BotLogManager getLogManager() {
        return logManager;
    }

    /**
     * @return The global {@link BotManager} overseeing all bots in the world.
     */
    public BotManager getManager() {
        return manager;
    }

    /**
     * @return The script stack that manages all active {@link api.bot.BotScript} instances for this bot.
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
}
