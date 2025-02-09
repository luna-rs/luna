package io.luna.game.model.mob.bot;
import api.bot.ExampleBotScript;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.luna.LunaContext;
import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerAppearance;
import io.luna.game.model.mob.PlayerAppearance.DesignPlayerInterface;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.attr.Attribute;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.game.model.mob.persistence.PlayerData;
import io.luna.net.msg.out.LogoutMessageWriter;
import io.luna.util.RandomUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Player} implementation representing an artificial player controlled and processed by the server. Bots take
 * up less networking resources than a typical player while using much more game processing resources due to having
 * to locally process script logic.
 * <p>
 * All passwords for bots by default are randomized series of numbers and letters. Bots are seen as regular players
 * by the server, the only big differences really being persistence and the way networking is handled. For more info
 * see {@link BotRepository} (persistence) and {@link BotClient} (networking).
 * All logic processing is done through a {@link BotScript} which controls how the bot will function in the world, and
 * {@link BotClient} gives access to all bot IO. For bots that are persisted and automatically login when the server
 * starts, see {@link BotRepository}.
 *
 * @author lare96
 */
public final class Bot extends Player {

    // TODO bot session scheduling https://github.com/luna-rs/luna/issues/387

    /**
     * The default script generator function, uses {@link ExampleBotScript}.
     */
    private static final Function<Bot, BotScript> DEFAULT_SCRIPT_GENERATOR = ExampleBotScript::new;

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The bot status attribute. Used to determine what the bot was doing in between server restarts.
     */
    public static final Attribute<String> STATUS = new Attribute<>("null").persist("status");

    /**
     * A builder class that creates {@link Bot} instances.
     */
    public static final class Builder {

        /**
         * The context.
         */
        private final LunaContext context;

        /**
         * If this bot will be remembered by the server.
         */
        private boolean temporary = true;

        /**
         * The username of this bot. Sets {@link #temporary} to {@code false}.
         */
        private String username;

        /**
         * The script generator.
         */
        private Function<Bot, BotScript> scriptGen = DEFAULT_SCRIPT_GENERATOR;

        /**
         * Creates a new {@link Builder}.
         *
         * @param context The context.
         */
        public Builder(LunaContext context) {
            this.context = context;
        }

        /**
         * Sets the username of a persistent bot. Will throw an {@link IllegalArgumentException} if no persistent bot
         * is found with {@code username}.
         */
        public Builder setUsername(String username) throws IllegalArgumentException {
            World world = context.getWorld();
            if (!world.getBots().containsPersistent(username)) {
                throw new IllegalArgumentException("Persistent bot with username[" + username + "] not found!");
            }
            this.username = username;
            temporary = false;
            return this;
        }

        /**
         * Sets the script generator.
         */
        public Builder setScript(Function<Bot, BotScript> scriptFunc) {
            this.scriptGen = requireNonNull(scriptFunc);
            return this;
        }

        /**
         * Builds the {@link Bot} instance, generating a random username if needed.
         */
        public Bot build() {
            if (username == null) {
                username = BotCredentials.generateUsername(context.getWorld(), temporary);
            }
            Bot bot = new Bot(context, username, BotCredentials.generatePassword(), temporary);
            bot.script = scriptGen.apply(bot);
            return bot;
        }
    }

    /**
     * The bot client.
     */
    private final BotClient botClient;

    /**
     * If this bot will be remembered by this server. Temporary bots are logged out whenever their script terminates,
     * if no fallback script is present.
     */
    private final boolean temporary;

    /**
     * The total number of times {@link #process()} has been called. Acts as an internal time clock for a bot while
     * the server is online. For persistence based timing operations, use attributes like a regular player.
     */
    private long cycles;

    /**
     * The next cycle that {@link BotScript#process()} will be called on. Used for timing of scripts.
     */
    private long nextExecution = -1;

    /**
     * The script that will control this bot.
     */
    private BotScript script;

    /**
     * Creates a new {@link Bot}.
     *
     * @param context The context instance.
     * @param username The username.
     * @param password The password.
     * @param temporary If this bot will be remembered by this server.
     */
    private Bot(LunaContext context, String username, String password, boolean temporary) {
        super(context, new PlayerCredentials(username, password));
        this.temporary = temporary;
        botClient = new BotClient(this, context.getServer().getMessageRepository());
        setClient(botClient);
    }

    @Override
    protected void onActive() {
        script.init();
    }

    @Override
    protected void onInactive() {
        world.getBots().remove(this);
    }

    @Override
    public void logout() {
        queue(new LogoutMessageWriter());
        botClient.setPendingLogout(true);
    }

    /**
     * Attempts to log in this bot. Will create a new account if the credentials don't exist, otherwise the existing
     * account data for the bot will be grabbed.
     *
     * @return The result of the attempt, if failed the generated username will not be consumed.
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
            logger.info("{} has logged in.", username);
        }, service.getExecutor());
        return result;
    }

    /**
     * Processes basic actions for bots before any script processing. This function essentially acts as a lizard
     * brain performing tasks that nearly every script will have to implement.
     *
     * @return {@code true} to proceed to the main script processing.
     */
    boolean processBasicActions() {
        // Bot is a new account, select random appearance.
        if (getInterfaces().standardTo(DesignPlayerInterface.class).isPresent()) {
            getMessageHandler().sendCharacterDesignSelection();
            return false;
        }
        return true;
    }

    /**
     * Called every tick before pre-synchronization, should only be used for script logic processing.
     */
    public void process() throws Exception {
        try {
            if (processBasicActions() && script != null && cycles > 1) {
                if (nextExecution == -1 || cycles >= nextExecution) {
                    int delay = script.process();
                    script.addExecution();
                    if (delay < 1) {
                        // Delay below 1, terminate script and get possible fallback.
                        terminateScript();
                        return;
                    }
                    nextExecution = cycles + delay;
                }
            }
        } finally {
            cycles++;
        }
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
     * Randomizes the appearance of this bot.
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
     * Terminates the current script, and attempts to run a fallback. If no fallback is available, the bot will either
     * disconnect (temporary bot) or go idle (permanent bot).
     */
    void terminateScript() {
        BotScript fallback = script.stop();
        if (fallback != null) {
            // Run fallback script when main script terminated.
            setScript(fallback);
        } else if (temporary) {
            // Bot with no fallback script, disconnect.
            logout();
        } else {
            // Permanent bot with no fallback script, enter idle mode.
            script = null;
            nextExecution = -1;
            logger.warn("{} has entered IDLE mode.", getUsername());
        }
    }

    /**
     * @return If this bot will be remembered by this server. Temporary bots are logged out whenever their script
     * terminates, if no fallback script is present.
     */
    public boolean isTemporary() {
        return temporary;
    }

    /**
     * @return The message handler.
     */
    public BotMessageHandler getMessageHandler() {
        return botClient.getMessageHandler();
    }

    /**
     * @return The script that will control this bot.
     */
    BotScript getScript() {
        return script;
    }

    /**
     * Sets the script that will control this bot.
     *
     * @param newScript The new script.
     */
    void setScript(BotScript newScript) {
        script = newScript;
        newScript.init();
    }

    /**
     * Sets a script that will make this bot idle ({@code null}).
     */
    void setIdleScript() {
        script = null;
    }

    /**
     * @return The bot client.
     */
    public BotClient getBotClient() {
        return botClient;
    }

    /**
     * @return The {@link #process()} execution counter.
     */
    public long getCycles() {
        return cycles;
    }
}
