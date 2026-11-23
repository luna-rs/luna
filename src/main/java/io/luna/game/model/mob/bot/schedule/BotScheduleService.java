package io.luna.game.model.mob.bot.schedule;

import api.bot.BotTypes;
import api.bot.script.BotScript;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.reflect.TypeToken;
import game.bot.scripts.LogoutBotScript;
import game.bot.scripts.combat.CombatBotScript;
import io.luna.Luna;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.model.mob.bot.BotLogManager.BotStreamType;
import io.luna.game.model.mob.bot.BotSettings;
import io.luna.game.model.mob.bot.brain.BotActivity;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Periodically manages the simulated online population for Luna bots.
 * <p>
 * This service keeps the world near the configured bot population by handling three related jobs:
 * <ul>
 *     <li>Scheduling logged-in bots to eventually log out.</li>
 *     <li>Scheduling logged-out bots to eventually return.</li>
 *     <li>Creating fresh bots for remaining openings or when a pass gives a new account priority.</li>
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
     * Maps username tags to preference modifiers applied when a bot's username contains the corresponding tag.
     * <p>
     * These tags provide lightweight hints about a bot's intended skills or activities based on its generated username.
     */
    private static final ImmutableMap<String, Consumer<BotPreference.Builder>> USERNAME_TAGS = ImmutableMap.of(
            // Mining-related usernames prefer training Mining.
            "miner", preferences -> preferences.addSkill(Skill.MINING),

            // PK-related usernames strongly prefer player killing.
            "pker", preferences -> preferences.setActivity(BotActivity.PKING, 0.90),
            "pking", preferences -> preferences.setActivity(BotActivity.PKING, 0.90),

            // Magic-related usernames prefer training Magic.
            "wizard", preferences -> preferences.addSkill(Skill.MAGIC),

            // Fishing-related usernames prefer training Fishing.
            "fish", preferences -> preferences.addSkill(Skill.FISHING),

            // Woodcutting-related usernames prefer training Woodcutting.
            "yew", preferences -> preferences.addSkill(Skill.WOODCUTTING),

            // Farming-related usernames prefer training Farming.
            "farm", preferences -> preferences.addSkill(Skill.FARMING)
    );

    /**
     * The total base number of bots this service may try to log in during a single scheduler pass.
     */
    private static final Range<Integer> BASE_LOGIN_COUNT = Range.closed(30, 60);

    /**
     * The base range amount of new bots this service may try to log in during a single scheduler pass.
     */
    private static final Range<Integer> NEW_LOGIN_COUNT_RANGE = Range.closed(1, 5);

    /**
     * Delay before retrying a failed saved-bot login so other eligible bots can use the openings.
     */
    private static final Duration LOGIN_RETRY_DELAY = Duration.ofMinutes(5);

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
     * Scheduler-thread retry deadlines for failed login attempts, independent of the persisted offline timers.
     */
    private final Map<String, Instant> loginRetries = new HashMap<>();

    /**
     * A multiplier applied to the calculated login goal.
     * <p>
     * This allows other systems to dynamically reduce or increase the desired bot population without changing the
     * configured base online count.
     */
    private final AtomicDouble onlineMultiplier = new AtomicDouble(1.0);

    /**
     * Names being checked or held for a new bot that has not reached the world yet.
     */
    private final Set<String> reservedUsernames = ConcurrentHashMap.newKeySet();

    /**
     * Loads the shared username model on the first naming request, including when bot rotation is disabled.
     *
     * @author lare96
     */
    private static final class UsernameModel {
        private static final BotUsernameGenerator GENERATOR = new BotUsernameGenerator(new MarkovChain.Builder<Character>()
                .setName("username")
                .setOrder(12)
                .train("username", new TypeToken<>() {
                }).build());
    }

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
                    // Saves were renamed to canonical names at startup.
                    PlayerCredentials.canonicalUsername(entry.getKey())
                            .ifPresent(name -> loginRequests.put(name, loginAt));
                }
            }
        }

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
     * Clears the offline timer after a bot successfully joins and schedules its next logout.
     *
     * @param bot The bot that joined the world.
     */
    public void onBotLogin(Bot bot) {
        if (loginRequests.remove(bot.getUsername()) != null) {
            saveRequested.set(true);
        }
        addLogoutRequest(bot.getUsernameHash(), calculateNextLogout(bot.getPersonality()));
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
     * @param preferences The preferences assigned to the bot.
     * @return Whether the bot joined the world.
     */
    private boolean login(String username, BotPersonality personality, BotPreference preferences) {
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
            return world.getBots().get(username) == bot;
        } catch (Exception e) {
            logger.error("Could not log in bot {}.", username, e);
            return false;
        }
    }

    /**
     * Checks that no bot or player uses {@code username}, online or saved. Callers run off the game thread.
     *
     * @param username The generated username.
     * @return {@code true} if a new bot may take the username.
     */
    private boolean isUsernameFree(String username) {
        if (world.getBots().exists(username) || world.getPlayer(username).isPresent() ||
                world.getLoginService().hasRequest(username) || world.getLogoutService().isSavePending(username)) {
            return false;
        }
        try {
            return world.getSerializerManager().getSerializer().loadPlayer(world, username) == null;
        } catch (Exception e) {
            // A save that can't be read still belongs to someone.
            return false;
        }
    }

    /**
     * Reserves available names on the persistence worker and passes them to the event login on the game thread.
     * Reservations are released after the game callback, including on failure.
     *
     * @param count The number of names to reserve.
     * @param start The event start callback.
     * @param <T> The result of starting the event.
     * @return A future for the game-thread callback's result.
     */
    public <T> CompletableFuture<T> withEventUsernames(int count, Function<List<String>, T> start) {
        List<String> names = new ArrayList<>(count);
        return world.getPersistenceService().execute(() -> {
            for (int index = 0; index < count; index++) {
                names.add(reserveUsername().orElseThrow(() ->
                        new IllegalStateException("Could not find enough available names for the event.")));
            }
        }).thenCompose(ignored -> world.getContext().getGame().sync(() -> {
            return start.apply(List.copyOf(names));
        }))
                .whenComplete((ignored, error) -> names.forEach(reservedUsernames::remove));
    }

    private Optional<String> reserveUsername() {
        return UsernameModel.GENERATOR.generate(name -> {
            if (!reservedUsernames.add(name)) {
                return false;
            }
            boolean available = false;
            try {
                available = isUsernameFree(name);
                return available;
            } finally {
                if (!available) {
                    reservedUsernames.remove(name);
                }
            }
        });
    }

    /**
     * Returns the bots that can log in, ordered by when they became eligible.
     * Saved bots without an offline timer are eligible immediately and go ahead of timed requests.
     *
     * @param now The cutoff for completed offline timers.
     * @return A snapshot of the eligible login requests.
     */
    List<Entry<String, Instant>> getEligibleLogins(Instant now) {
        loginRetries.values().removeIf(retryAt -> !retryAt.isAfter(now));
        Map<String, Instant> candidates = new HashMap<>(loginRequests);
        for (String username : world.getBots().getSavedNames()) {
            candidates.putIfAbsent(username, Instant.MIN);
        }
        return candidates.entrySet().stream()
                .filter(entry -> !entry.getValue().isAfter(now))
                .filter(entry -> !loginRetries.containsKey(entry.getKey()) && canAttemptLogin(entry.getKey()))
                .sorted(Entry.<String, Instant>comparingByValue().thenComparing(Entry.comparingByKey()))
                .toList();
    }

    /**
     * Checks whether an account is outside the world and free of pending logout work.
     *
     * @param username The account to check.
     * @return Whether a login attempt can proceed.
     */
    private boolean canAttemptLogin(String username) {
        return !world.getBots().isOnline(username) && !world.getPlayerMap().containsKey(username) &&
                !world.getLogoutService().isSavePending(username);
    }

    /**
     * Fills openings from the longest-waiting eligible saved bots, with a chance to log in one new bot first.
     */
    private void handleLoginRequests() {
        int loginGoal = getLoginGoal();
        if (loginGoal < 1) {
            return;
        }
        var eligibleLogins = getEligibleLogins(Instant.now());
        double newAccountLoginChance = settings.newAccountLoginChance();
        if (!eligibleLogins.isEmpty() && newAccountLoginChance > 0.0 &&
                RandomUtils.roll(newAccountLoginChance) && createNewBot()) {
            if (--loginGoal == 0) {
                return;
            }
        }
        for (var request : eligibleLogins) {
            String username = request.getKey();
            // A concurrent logout can replace the offline timer after the snapshot was taken.
            if (!canAttemptLogin(username) ||
                    !request.getValue().equals(loginRequests.getOrDefault(username, Instant.MIN))) {
                continue;
            }
            if (!login(username, null, null)) {
                loginRetries.put(username, Instant.now().plus(LOGIN_RETRY_DELAY));
            }
            if (--loginGoal == 0) {
                return;
            }
        }
        createNewBots(loginGoal);
    }

    /**
     * Generates fresh bots to fill the remaining openings after eligible saved bots have been tried.
     *
     * @param loginGoal The number of remaining openings.
     */
    private void createNewBots(int loginGoal) {
        loginGoal = loginGoal > NEW_LOGIN_COUNT_RANGE.upperEndpoint() ? RandomUtils.random(NEW_LOGIN_COUNT_RANGE) : loginGoal;
        while (loginGoal-- > 0) {
            createNewBot();
        }
    }

    /**
     * Generates a new bot and attempts to log it in.
     *
     * @return Whether the new bot successfully logged in.
     */
    private boolean createNewBot() {
        BotPersonality.Builder personalityBuilder = new BotPersonality.Builder(
                world.getBotManager().getPersonalityManager()).randomizeSmart();

        // Small chance of either an unusually low or high intelligence.
        double intelligence = personalityBuilder.getIntelligence();
        if (RandomUtils.roll(settings.highIntelligenceChance()) && intelligence < 0.85) {
            personalityBuilder.setIntelligence(ThreadLocalRandom.current().nextDouble(0.85, 1.0));
        } else if (RandomUtils.roll(settings.lowIntelligenceChance()) && intelligence > 0.15) {
            personalityBuilder.setIntelligence(ThreadLocalRandom.current().nextDouble(0.0, 0.15));
        }

        // Generate a username and attempt to log the bot in.
        Optional<String> generatedUsername = reserveUsername();
        if (generatedUsername.isEmpty()) {
            return false;
        }
        String username = generatedUsername.get();

        try {
            BotPersonality personality = personalityBuilder.build();
            BotPreference.Builder preferences = new BotPreference.Builder(
                    world.getBotManager().getPersonalityManager(), personality).randomizeSmart();
            for (var entry : USERNAME_TAGS.entrySet()) {
                String tag = entry.getKey();
                if (username.contains(tag)) {
                    entry.getValue().accept(preferences);
                }
            }
            return login(username, personality, preferences.build());
        } finally {
            reservedUsernames.remove(username);
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
                        .filter(plr -> api.bot.BotTypes.isRealBot(plr))
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
        int goal = (int) Math.min((int) Math.floor(RandomUtils.random(BASE_LOGIN_COUNT) * goalPercent), goalCount);

        // Player list capacity includes the reserved index zero. Count online players from the thread-safe map.
        int remaining = world.getPlayers().capacity() - 1 - world.getPlayerMap().size();
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
     * Most breaks stay near the configured base duration, with uncommon one-to-two-day breaks and rare longer
     * absences at the default settings. Sociability only shortens the sampled break, by at most twenty percent.
     *
     * @param personality The personality used to adjust the login time.
     * @return The next login time.
     */
    private Instant calculateNextLogin(BotPersonality personality) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double baseHours = Math.max(1, settings.baseLogoutHours());
        double roll = random.nextDouble();
        double hours;
        if (roll < 0.90) {
            hours = random.nextDouble(baseHours * 0.5, baseHours * 1.5);
        } else if (roll < 0.98) {
            hours = random.nextDouble(baseHours * 2.0, baseHours * 4.0);
        } else if (roll < 0.999) {
            hours = random.nextDouble(baseHours * 4.0, baseHours * 8.0);
        } else {
            hours = random.nextDouble(baseHours * 8.0, baseHours * 14.0);
        }

        double social = personality.getSocial();
        double shortening = Double.isFinite(social) ? 0.20 * clamp(social, 0.0, 1.0) : 0.0;
        double totalHours = clamp(
                hours * (1.0 - shortening),
                settings.minimumLogoutHours(),
                settings.maximumLogoutHours()
        );
        return Instant.now().plus(Math.round(totalHours * 60.0), ChronoUnit.MINUTES);
    }

    /**
     * Calculates how many hours should be added to or removed from a base online duration.
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
