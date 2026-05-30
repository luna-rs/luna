package io.luna.game.model.mob.bot.schedule;

import api.bot.script.BotScript;
import com.google.common.collect.Range;
import com.google.common.primitives.Chars;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.reflect.TypeToken;
import game.bot.scripts.LogoutBotScript;
import game.bot.scripts.combat.CombatBotScript;
import io.luna.Luna;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.BotLogManager.BotStreamType;
import io.luna.game.model.mob.bot.BotSettings;
import io.luna.game.model.mob.bot.brain.BotPersonality;
import io.luna.game.model.mob.bot.brain.BotPreference;
import io.luna.util.GsonUtils;
import io.luna.util.RandomUtils;
import io.luna.util.StringUtils;
import io.luna.util.markov.MarkovChain;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Periodically manages the simulated online population for Luna bots.
 * <p>
 * This service keeps the world near the configured bot population by handling three related jobs:
 * <ul>
 *     <li>Scheduling logged-in bots to eventually log out.</li>
 *     <li>Scheduling logged-out bots to eventually return.</li>
 *     <li>Creating fresh bots when saved bots and pending login requests are not enough.</li>
 * </ul>
 * <p>
 * Login requests are persisted to disk so bots that logged out before shutdown can still be brought back after a
 * restart. Logout requests are kept in memory because they only matter while the bot is currently online.
 *
 * @author lare96
 */
public final class BotScheduleService extends AbstractScheduledService {

    /**
     * The logger for bot schedule activity.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The total base number of bots this service may try to log in during a single scheduler pass.
     */
    private static final int BASE_LOGIN_COUNT = 15;

    /**
     * The base range amount of new bots this service may try to log in during a single scheduler pass.
     */
    private static final Range<Integer> NEW_LOGIN_COUNT_RANGE = Range.closed(1, 5);

    /**
     * The path used to persist pending bot login requests across server restarts.
     */
    private static final Path LOGIN_REQUESTS_PATH = Paths.get("data", "game", "bots", "login_requests.json");

    /**
     * Tracks whether pending login requests should be saved at the end of the current scheduler pass.
     */
    private final AtomicBoolean saveRequested = new AtomicBoolean();

    /**
     * The world this scheduler logs bots into and out of.
     */
    private final World world;

    /**
     * The bot settings used to calculate target population, login time, logout time, and new-bot personality odds.
     */
    private final BotSettings settings;

    /**
     * In-memory logout requests mapped by username hash.
     */
    private final Map<Long, Instant> logoutRequests = new ConcurrentHashMap<>();

    /**
     * Persistent login requests mapped by username.
     */
    private final Map<String, Instant> loginRequests = new ConcurrentHashMap<>();

    /**
     * A multiplier applied to the calculated login goal.
     * <p>
     * This allows other systems to dynamically reduce or increase the desired bot population without changing the
     * configured base online count.
     */
    private final AtomicDouble onlineMultiplier = new AtomicDouble(1.0);

    /**
     * The number of bots this service still wants to log in during the current scheduler pass.
     */
    private int loginGoal;

    /**
     * The Markov chain used to generate usernames for newly created bots.
     */
    private MarkovChain<Character> usernameChain;

    /**
     * Creates a new bot schedule service for [world].
     *
     * @param world The world this scheduler will manage bots for.
     */
    public BotScheduleService(World world) {
        this.world = world;
        settings = Luna.settings().bots();
    }

    @Override
    protected void startUp() throws Exception {
        // Stop the scheduler if user does not want auto-logged in bots.
        if (Luna.settings().bots().baseOnlineCount() < 1) {
            logger.warn("Bot schedule service was stopped because \"base_online_count\" in \"luna.jsonc\" is < 1.");
            stopAsync();
            return;
        }

        // Load all previously saved login requests.
        if (Files.exists(LOGIN_REQUESTS_PATH)) {
            Map<String, Number> loadedLogouts = GsonUtils.readAsType(LOGIN_REQUESTS_PATH, Map.class);
            if (loadedLogouts != null) {
                for (var entry : loadedLogouts.entrySet()) {
                    Instant loginAt = Instant.ofEpochMilli(entry.getValue().longValue());
                    loginRequests.put(entry.getKey(), loginAt);
                }
            }
        }

        // Create our Markov chain that will generate usernames for new bots.
        usernameChain = new MarkovChain.Builder<Character>()
                .setName("username")
                .setOrder(12)
                .train("username", new TypeToken<>() {
                }).build();

        logger.info("Bot scheduler is now running!");
    }

    @Override
    protected void runOneIteration() throws Exception {
        handleLoginRequests();
        handleLogoutRequests();

        if (saveRequested.compareAndSet(true, false)) {
            Map<String, Long> saveMap = loginRequests.entrySet().stream()
                    .collect(Collectors.toMap(Entry::getKey, it -> it.getValue().toEpochMilli()));
            GsonUtils.writeJson(saveMap, LOGIN_REQUESTS_PATH);
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(Duration.ofSeconds(RandomUtils.inclusive(15, 30)), Duration.ofMinutes(1));
    }

    /**
     * Schedules an online bot to log out after a personality-adjusted amount of time.
     *
     * @param usernameHash The base-37 username hash of the bot.
     * @param personality The personality used to calculate how long the bot should stay online.
     */
    public void addLogoutRequest(long usernameHash, BotPersonality personality) {
        addLogoutRequest(usernameHash, calculateNextLogout(personality));
    }

    /**
     * Schedules an online bot to log out after a specific amount of time.
     *
     * @param usernameHash The base-37 username hash of the bot.
     * @param instant How long the bot should stay online.
     */
    public void addLogoutRequest(long usernameHash, Instant instant) {
        logoutRequests.putIfAbsent(usernameHash, instant);
    }

    /**
     * Schedules a bot username to log back in after a personality-adjusted amount of time.
     *
     * @param username The username to log back in.
     * @param personality The personality used to calculate how long the bot should stay offline.
     */
    public void addLoginRequest(String username, BotPersonality personality) {
        loginRequests.putIfAbsent(username, calculateNextLogin(personality));
        saveRequested.set(true);
    }

    /**
     * Adjusts the current online-ratio multiplier.
     * <p>
     * The ratio is clamped to zero or higher so population pressure can be disabled but never becomes negative.
     *
     * @param amount The amount to add to the current ratio.
     */
    public void adjustOnlineRatio(double amount) {
        onlineMultiplier.updateAndGet(oldValue -> Math.max(0.0, oldValue + amount));
    }

    /**
     * Sets the current online-ratio multiplier.
     * <p>
     * The ratio is clamped to zero or higher so population pressure can be disabled but never becomes negative.
     *
     * @param multiplier The new online-ratio multiplier.
     */
    public void setOnlineMultiplier(double multiplier) {
        onlineMultiplier.set(Math.max(0.0, multiplier));
    }

    /**
     * Builds and logs in a bot with the given username and personality.
     *
     * @param username The username assigned to the bot.
     * @param personality The personality assigned to the bot.
     */
    private void login(String username, BotPersonality personality, BotPreference preferences) {
        try {
            Bot.Builder builder = new Bot.Builder(world.getContext())
                    .setUsername(username);
            if (personality != null) {
                builder.setPersonality(personality);
            }
            if (preferences != null) {
                builder.setPreferences(preferences);
            }

            Bot bot = builder.build();
            if (Luna.settings().game().betaMode()) {
                bot.getLogManager().setStreamType(BotStreamType.FILE);
            }
            bot.login().join();
        } catch (Exception e) {
            logger.error("Error when trying to create new bot.", e);
        }
    }

    /**
     * Logs in an existing bot username using a newly randomized personality.
     *
     * @param username The username to log in.
     */
    private void login(String username) {
        login(username, null, null);
    }

    /**
     * Handles all pending login work for the current scheduler pass.
     * <p>
     * Login priority is:
     * <ol>
     *     <li>Previously scheduled login requests.</li>
     *     <li>Saved bot usernames that are currently offline.</li>
     *     <li>Newly generated bots using the username Markov chain.</li>
     * </ol>
     */
    private void handleLoginRequests() {
        loginGoal = getLoginGoal();

        while (loginGoal > 0) {
            // First check any previously scheduled login requests.
            loginRequests.entrySet().removeIf(entry -> {
                String username = entry.getKey();
                Optional<Bot> optionalBot = world.getPlayer(username).filter(it -> it instanceof Bot).map(Player::asBot);
                if (optionalBot.isPresent()) {
                    Bot bot = optionalBot.get();
                    addLogoutRequest(bot.getUsernameHash(), bot.getPersonality());
                    return true;
                }

                Instant loginAt = entry.getValue();
                if (Instant.now().isAfter(loginAt) && loginGoal-- > 0) {
                    // We're ready to log back in.
                    login(username);
                    return true;
                }

                return false;
            });

            // Next check any other logged out bots without login requests.
            for (String username : world.getBots().getSavedNames()) {
                if (world.getBots().isOnline(username) ||
                        loginRequests.containsKey(username) ||
                        loginGoal-- < 1) {
                    continue;
                }

                login(username);
            }

            // Finally, if we still need bots, create a new randomized amount of bots.
            loginGoal = loginGoal > NEW_LOGIN_COUNT_RANGE.upperEndpoint() ? RandomUtils.random(NEW_LOGIN_COUNT_RANGE) : loginGoal;
            while (loginGoal-- > 0) {
                BotPersonality.Builder personalityBuilder = new BotPersonality.Builder(
                        world.getBotManager().getPersonalityManager()).randomizeSmart();


                // Small chance of either an unusually low or high intelligence.
                double intelligence = personalityBuilder.getIntelligence();
                if (RandomUtils.roll(settings.highIntelligenceChance()) && intelligence < 0.85) {
                    personalityBuilder.setIntelligence(ThreadLocalRandom.current().nextDouble(0.85, 1.0));
                } else if (RandomUtils.roll(settings.lowIntelligenceChance()) && intelligence > 0.15) {
                    personalityBuilder.setIntelligence(ThreadLocalRandom.current().nextDouble(0.0, 0.15));
                }

                // Generate a username and attempt to log the bot in. The username quality scales with intelligence.
                int quality = Math.max(1, (int) Math.floor(15 * intelligence));
                List<Character> generatedUsername = usernameChain.generate(quality);
                if (generatedUsername.isEmpty()) {
                    continue;
                }
                String username = new String(Chars.toArray(generatedUsername));
                username = username.trim().toLowerCase(Locale.CANADA).replace(' ', '_'); // No spaces.
                if (username.length() >= 12) {// Max 1-12 characters.
                    username = username.substring(0, 11);
                }

                BotPersonality personality = personalityBuilder.build();
                BotPreference.Builder preferences = new BotPreference.Builder(
                        world.getBotManager().getPersonalityManager(), personality).randomizeSmart();
                if (username.contains("miner")) {
                    preferences.addSkill(Skill.MINING);
                }
                // TODO replace spaces in regular player usernames with _ as well
                // TODO If a bot has certain 'tags' in their name like 'miner' or 'cutter' 'woodcut' etc.
                //  make them more likely to do that skill! Use a map tag -> skill_id
                //  or even map tag -> (prefrences) -> void, so you can set any prefrence based on tag, pking ,etc.

                if (!world.getBots().exists(username)) {
                    login(username, personality, preferences.build());
                }
            }
        }
    }

    /**
     * Handles all pending logout work for the current scheduler pass.
     * <p>
     * If the bot is already offline, it is moved into the login request table. If the bot is still online and its logout
     * time has passed, a logout script is pushed or updated according to its current script state.
     */
    private void handleLogoutRequests() {
        logoutRequests.entrySet().removeIf(entry -> {
            long usernameHash = entry.getKey();

            // Bot is already logged out, add a best-guess login request if needed.
            if (world.getPlayer(usernameHash).isEmpty()) {
                addLoginRequest(StringUtils.decodeFromBase37(usernameHash), BotPersonality.DEFAULT);
                return true;
            }

            // Attempt a logout if ready.
            Instant logoutAt = entry.getValue();
            if (Instant.now().isAfter(logoutAt)) {
                world.getPlayer(entry.getKey())
                        .filter(plr -> plr instanceof Bot)
                        .ifPresent(value -> world.getContext().getGame().sync(() -> {
                            Bot bot = value.asBot();
                            BotScript script = bot.getScriptStack().current();

                            if (script instanceof LogoutBotScript) {
                                bot.forceLogout();
                            } else if (script instanceof CombatBotScript) {
                                // We're in combat. Push a logout script to run after combat completes.
                                bot.getScriptStack().softPushHead(new LogoutBotScript(bot));
                            } else if (bot.getControllers().checkLogout()) {
                                // Otherwise, push a logout script that runs instantly.
                                bot.getScriptStack().pushHead(new LogoutBotScript(bot));
                            } else if (script != null) {
                                // We can't log out right now, but have an active script. Push a logout script to the tail.
                                bot.getScriptStack().pushTail(new LogoutBotScript(bot));
                            } else {
                                // We can't log out right now, and have no active script. Try again in 10 minutes.
                                logoutRequests.putIfAbsent(usernameHash, Instant.now().plus(10, ChronoUnit.MINUTES));
                            }
                        }));
                return true;
            }
            return false;
        });
    }

    /**
     * Calculates how many bots should be logged in during the current scheduler pass.
     * <p>
     * The target is based on the configured base online count, the current online bot count, and the current
     * {@code onlineRatio} multiplier.
     *
     * @return The number of bots this pass should attempt to log in.
     */
    private int getLoginGoal() {
        int baseCount = (int) (Luna.settings().bots().baseOnlineCount() * onlineMultiplier.get());
        if (baseCount < 1) {
            // Automatic bot logins are disabled when no baseline population is configured.
            return 0;
        }

        int onlineCount = world.getBots().getOnlineCount();
        if (onlineCount >= baseCount) {
            // We already have enough bots online to satisfy the base count.
            return 0;
        }

        // Randomize the target population slightly so the online count does not sit at an exact fixed number.
        double goalCount = Math.max(
                RandomUtils.inclusive((int) (baseCount * 0.85), (int) (baseCount * 1.15)),
                1
        );

        /*
         * Scale login pressure down as the world approaches the randomized target population.
         *
         * Examples:
         * - 0% of target online  -> full BASE_LOGIN_COUNT pressure.
         * - 50% of target online -> half BASE_LOGIN_COUNT pressure.
         * - 100%+ target online  -> no automatic logins this cycle.
         */
        double goalPercent = 1.0 - Math.min(1.0, onlineCount / goalCount);
        int goal = (int) Math.min((int) Math.floor(BASE_LOGIN_COUNT * goalPercent), goalCount);

        // Ensure login goal does not exceed max player count.
        int remaining = world.getPlayers().capacity() - world.getPlayerMap().size(); // Thread-safe version.
        if (remaining < 1) {
            return 0;
        }
        return Math.min(remaining, goal);
    }

    /**
     * Calculates the next time the bot should attempt to log out.
     * <p>
     * Social bots stay online longer, while antisocial bots log out sooner.
     *
     * @param personality The personality used to adjust the logout time.
     * @return The next logout time.
     */
    private Instant calculateNextLogout(BotPersonality personality) {
        int baseHours = calculateBaseHours(settings.baseLoginHours());
        int boostHours = calculateBoostHours(baseHours, personality);
        int totalHours = (int) clamp(
                personality.isSocial() ? baseHours + boostHours : baseHours - boostHours,
                settings.minimumLoginHours(),
                settings.maximumLoginHours()
        );
        return Instant.now().plus(30, ChronoUnit.MINUTES).plus(totalHours, ChronoUnit.HOURS);
    }

    /**
     * Calculates the next time the bot should attempt to log in.
     * <p>
     * Social bots return sooner, while antisocial bots remain offline longer.
     *
     * @param personality The personality used to adjust the login time.
     * @return The next login time.
     */
    private Instant calculateNextLogin(BotPersonality personality) {
        int baseHours = calculateBaseHours(settings.baseLogoutHours());
        int boostHours = calculateBoostHours(baseHours, personality);
        int totalHours = (int) clamp(
                personality.isSocial() ? baseHours - boostHours : baseHours + boostHours,
                settings.minimumLogoutHours(),
                settings.maximumLogoutHours()
        );

        return Instant.now().plus(30, ChronoUnit.MINUTES).plus(totalHours, ChronoUnit.HOURS);
    }

    /**
     * Calculates how many hours should be added to or removed from a base online/offline duration.
     * <p>
     * The boost is based on the side of the social spectrum represented by the bot's personality. Social bots use their
     * social score directly, while antisocial bots use the inverse social score.
     *
     * @param baseHours The randomized base hour count.
     * @param personality The personality used to calculate the boost.
     * @return The hour boost to apply to the base duration.
     */
    private int calculateBoostHours(int baseHours, BotPersonality personality) {
        double social = personality.getSocial();
        double socialBoost = clamp(personality.isSocial() ? social : 1.0 - social, 0.0, 1.0);

        if (socialBoost > 0.0) {
            socialBoost = ThreadLocalRandom.current().nextDouble(socialBoost / 2, socialBoost * 2);
        } else {
            socialBoost = 0.0;
        }

        socialBoost = clamp(socialBoost, 0.0, 1.0);
        return (int) Math.floor(baseHours * socialBoost);
    }

    /**
     * Randomizes a configured base hour count.
     * <p>
     * The configured value is clamped to at least four hours, then randomized from half to double that value.
     *
     * @param baseHours The configured base hour count.
     * @return The randomized base hour count.
     */
    private int calculateBaseHours(int baseHours) {
        int base = Math.max(baseHours, 4);
        return RandomUtils.inclusive(base / 2, base * 2);
    }

    /**
     * Clamps {@code value} between {@code min} and {@code max}.
     *
     * @param value The value to clamp.
     * @param min The minimum allowed value.
     * @param max The maximum allowed value.
     * @return The clamped value.
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}