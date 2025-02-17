package io.luna.game.model.mob.bot;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AbstractScheduledService;
import io.luna.Luna;
import io.luna.game.model.EntityState;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.persistence.PlayerData;
import io.luna.game.model.mob.persistence.PlayerSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A model that handles schedules for {@link Bot} types. It determines when bots login and logout in order to slowly
 * match the bot player count to {@link BotSettings#idealCount()}. It also facilitates play schedules for bots
 * independent of the ideal player count.
 *
 * @author lare96
 */
public final class BotScheduleService extends AbstractScheduledService {

    /**
     * The maximum amount of logins that can be processed per iteration.
     */
    private static final int MAX_LOGINS = 8;

    /**
     * The world instance.
     */
    private final World world;

    /**
     * A queue of bots scheduled for login on the next execution.
     */
    private final Set<String> logins = new HashSet<>();

    /**
     * A queue of bots scheduled for logout on the next execution.
     */
    private final Set<Bot> logouts = new HashSet<>();

    /**
     * The map of schedules used for bot logins and logouts.
     */
    private final Map<String, BotSchedule> scheduleMap = new HashMap<>();

    /**
     * The executions.
     */
    private int executions;

    /**
     * Creates a new {@link BotScheduleService}.
     *
     * @param world The world instance.
     */
    public BotScheduleService(World world) {
        this.world = world;
    }

    @Override
    protected void startUp() throws Exception {
        PlayerSerializer serializer = world.getSerializerManager().getSerializer();
        Map<String, BotSchedule> loadedSchedules = serializer.synchronizeBotSchedules(world);
        if (loadedSchedules.size() != world.getBots().getPersistentCount()) {
            throw new IllegalStateException("Bot sessions and persistent bot count are not synchronized.");
        }
        scheduleMap.putAll(loadedSchedules);
    }

    @Override
    protected void runOneIteration() throws Exception {
        try {
            checkSchedules();
            handleSchedules();
        } finally {
            executions++;
        }
    }

    @Override
    protected Scheduler scheduler() {
        Duration scheduleDuration = Duration.ofSeconds(10);
        return Scheduler.newFixedRateSchedule(scheduleDuration, scheduleDuration);
    }

    /**
     * Checks all persisted bot schedules and determines which need to be logged in and out.
     */
    private void checkSchedules() throws ExecutionException, InterruptedException {
        for (BotSchedule session : scheduleMap.values()) {
            String username = session.getUsername();
            Bot onlineBot = Optional.of(world.getPlayerMap().get(username)).filter(Player::isBot).
                    map(Player::asBot).orElse(null);
            if (onlineBot != null && !Luna.settings().bots().keepOnline() &&
                    !onlineBot.isLogoutScheduled() &&
                    !onlineBot.getClient().isPendingLogout() &&
                    onlineBot.getState() != EntityState.INACTIVE) {
                Duration timeOnline = onlineBot.getTimeOnline().elapsed();
                if (session.canLogout(timeOnline)) { // Queue bot for logout.
                    logouts.add(onlineBot);
                }
            } else {
                PlayerData offlineBot = world.getPersistenceService().load(username).get(); // Load player data.
                if (offlineBot != null && session.canLogin(offlineBot.logoutTime)) { // Queue bot for login.
                    logins.add(username);
                }
            }
        }
    }

    /**
     * Handles the actual queued scheduled logins and logouts.
     *
     * @throws Exception If any errors occur.
     */
    private void handleSchedules() throws Exception {
        if (executions == 1 || executions % 10 == 0) {
            int idealCount = Luna.settings().bots().idealCount();
            int worldCount = Ints.saturatedCast(world.getPlayerMap().values().stream().
                    filter(it -> it != null && it.isBot()).count());
            int neededChange = idealCount - worldCount;
            if (neededChange > 0) {
                tryLogin(neededChange);
            } else {
                tryLogout(Math.abs(neededChange));
            }
        }
    }

    /**
     * Handles up to {@code amount} logins, either from existing persisted bots or by creating new bots.
     *
     * @param amount The amount of bots to try and login.
     * @throws Exception If any errors occur.
     */
    private void tryLogin(int amount) throws Exception {
        int added = 0;
        boolean firstLoop = true;
        if (logins.isEmpty()) {
            // No sessions are ready but we need bots. Create new ones.
            for (int repeat = 0; repeat < MAX_LOGINS; repeat++) {
                if (added == amount) {
                    break;
                }
                if (firstLoop || ThreadLocalRandom.current().nextBoolean()) {
                    Bot bot = new Bot.Builder(world.getContext()).setPersistent().build();
                    bot.login().get(); // Wait until login is complete.
                    added++;
                }
                firstLoop = false;
            }
        } else {
            // Do as many scheduled logins as we can.
            for (String username : logins) {
                if (added == MAX_LOGINS ||
                        added == amount) {
                    break;
                }
                if (firstLoop || ThreadLocalRandom.current().nextBoolean()) {
                    Bot bot = new Bot.Builder(world.getContext()).setUsername(username).build();
                    bot.login().get(); // Wait until login is complete.
                    added++;
                }
                firstLoop = false;
            }
            logins.clear();
        }
    }

    /**
     * Handles up to {@code amount} logouts from currently online bots.
     *
     * @param amount The amount of bots to try and logout.
     */
    private void tryLogout(int amount) {
        int removed = 0;
        for (Bot bot : logouts) {
            if (removed == amount) {
                break;
            }
            if (bot.getState() == EntityState.INACTIVE) {
                removed++; // Already removed.
                continue;
            }
            bot.setLogoutScheduled(true);
            removed++;
        }
        logouts.clear();
    }

    /**
     * Dynamically updates the {@link BotSchedule} for a single bot.
     *
     * @param schedule The new schedule to assign.
     */
    public void updateSchedule(BotSchedule schedule) throws Exception {
        PlayerSerializer serializer = world.getSerializerManager().getSerializer();
        if (serializer.saveBotSchedule(world, schedule)) {
            scheduleMap.put(schedule.getUsername(), schedule);
        }
    }
}
