package io.luna.game.model.mob.bot;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
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
import io.luna.game.model.mob.bot.brain.BotBrain;
import io.luna.game.model.mob.bot.brain.BotEmotion;
import io.luna.game.model.mob.bot.brain.BotIntelligence;
import io.luna.game.model.mob.bot.brain.BotPersonality;
import io.luna.game.model.mob.bot.brain.BotPreference;
import io.luna.game.model.mob.bot.brain.BotReflex;
import io.luna.game.model.mob.bot.io.BotClient;
import io.luna.game.model.mob.bot.io.BotInputMessageHandler;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler;
import io.luna.game.model.mob.bot.script.BotScriptStack;
import io.luna.game.model.mob.bot.speech.BotSpeechStack;
import io.luna.game.persistence.PlayerData;
import io.luna.game.task.Task;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


/**
 * A {@link Player} implementation that represents a fully autonomous artificial player (bot) managed and processed
 * entirely by the server. Bots occupy minimal network resources but consume additional CPU resources for AI processing
 * and local script execution.
 * <p>
 * Each bot is backed by a {@link BotClient} for IO handling and is controlled by a {@link BotIntelligence}
 * instance, which orchestrates the bot’s {@link BotBrain}, {@link BotPersonality}, {@link BotReflex}, and other
 * behavioral traits. The lifecycle and persistence of bots are managed by {@link BotRepository} and
 * {@link BotScheduleService}.
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

        // todo make sure temporary bot code isnt hardcoded.
        private final LunaContext context;
        private boolean temporary = true;
        private String username;
        private BotIntelligence intelligence;

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
         * Sets a custom {@link BotIntelligence} instance for this bot.
         *
         * @param intelligence The intelligence module to use.
         * @return This builder instance.
         */
        public Builder setIntelligence(BotIntelligence intelligence) {
            this.intelligence = intelligence;
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
         * Builds and returns a fully constructed {@link Bot} instance. A random username and password will be
         * generated if not specified.
         *
         * @return A new {@link Bot} instance.
         */
        public Bot build() {
            if (intelligence == null) {
                intelligence = new BotIntelligence.Builder(context.getWorld().getBotManager()).build();
            }
            if (username == null) {
                username = BotCredentials.generateUsername(context.getWorld(), temporary);
            }
            return new Bot(context, username, BotCredentials.generatePassword(), intelligence, temporary);
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
     * The AI intelligence system controlling this bot’s behavior.
     */
    private final BotIntelligence intelligence;

    /**
     * The script stack.
     */
    private final BotScriptStack scriptStack;

    /**
     * The speech stack.
     */
    private final BotSpeechStack speechStack;

    /**
     * A concurrent set of human players within the bot’s local viewport.
     */
    private final Set<Player> localHumans = Sets.newConcurrentHashSet();

    /**
     * The bot client.
     */
    private final BotClient botClient;

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
     * @param intelligence The AI intelligence system controlling this bot’s behavior.
     * @param temporary Whether this bot is attempting to log out (e.g., due to a schedule trigger).
     */
    private Bot(LunaContext context, String username, String password, BotIntelligence intelligence, boolean temporary) {
        super(context, new PlayerCredentials(username, password));
        this.temporary = temporary;
        this.intelligence = intelligence;

        botClient = new BotClient(this, context.getServer().getMessageRepository());
        manager = world.getBotManager();
        scriptStack = new BotScriptStack(this, manager.getScriptManager());
        speechStack = new BotSpeechStack(this, manager.getSpeechManager());
        setClient(botClient);
    }

    @Override
    protected void onInactive() {
        world.getBots().remove(this);
        super.onInactive();
    }

    @Override
    public void logout() {
        scriptStack.shutdown();
        botClient.onInactive();
    }

    /**
     * Attempts to asynchronously log in this bot. If the bot’s credentials do not exist, a new account will be created.
     *
     * @return A {@link ListenableFuture} representing the async login operation. If the login fails, the generated
     * username will not be consumed.
     */
    public ListenableFuture<PlayerData> login() {
        String username = getUsername();
        if (world.getPlayerMap().containsKey(username)) {
            return Futures.immediateFailedFuture(new IllegalStateException("Bot is already logged in!"));
        }
        if (world.getLogoutService().isSavePending(username)) {
            return Futures.immediateFailedFuture(new IllegalStateException("Bot data is still being saved!"));
        }

        // Load data for bot based on username.
        ListenableFuture<PlayerData> result = world.getPersistenceService().load(username);
        result.addListener(() -> {
            PlayerData data = Futures.getUnchecked(result);
            if (data != null && temporary) {
                data = null;
            }
            loadData(data);
            world.getBots().add(this);
            world.getPlayers().add(this);
            setState(EntityState.ACTIVE);
            log("I'm alive!");
            logger.info("{} has logged in.", username);
            world.schedule(new Task(1) {
                @Override
                protected void execute() {
                    intelligence.start();
                    cancel();
                }
            });
        }, service.getExecutor());
        return result;
    }

    /**
     * Processes one AI cycle for this bot. Invokes {@link BotIntelligence#process(Bot)} and increments the internal
     * cycle counter.
     *
     * @throws Exception If any error occurs during AI processing.
     */
    public void process() throws Exception {
        try {
            intelligence.process(this);
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
     * @return The intelligence system controlling this bot’s overall decision-making.
     */
    public BotIntelligence getIntelligence() {
        return intelligence;
    }

    /**
     * @return The script stack that manages all active {@link api.bot.BotScript} instances for this bot.
     */
    public BotScriptStack getScriptStack() {
        return scriptStack;
    }

    /**
     * @return The speech stack managing spoken messages, chatter, and conversational output.
     */
    public BotSpeechStack getSpeechStack() {
        return speechStack;
    }

    /**
     * @return The reflex module responsible for reactive, low-level responses.
     */
    public BotReflex getReflex() {
        return intelligence.getReflex();
    }

    /**
     * @return The brain module controlling reasoning and goal-based decision-making.
     */
    public BotBrain getBrain() {
        return intelligence.getBrain();
    }

    /**
     * @return The personality module that defines behavioral traits and tendencies.
     */
    public BotPersonality getPersonality() {
        return intelligence.getPersonality();
    }

    /**
     * @return The emotion controller representing the bot’s current affective state.
     */
    public BotEmotion getEmotions() {
        return intelligence.getEmotions();
    }

    /**
     * @return The preference system that stores likes, dislikes, and behavioral biases.
     */
    public BotPreference getPreferences() {
        return intelligence.getPreferences();
    }
}
