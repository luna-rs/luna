package io.luna.game.model.mob.bot;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import io.luna.util.RandomUtils;

import java.time.Duration;
import java.time.Instant;

/**
 * A model that represents how long {@link Bot} types will stay logged in and logged out for.
 *
 * @author lare96
 */
public final class BotSchedule {

    /**
     * Creates a new {@link BotSchedule} with a random login/logout schedule.
     *
     * @param username The persistent bot the session is being created for.
     */
    public static BotSchedule createRandomSession(String username) {
        int selected = RandomUtils.inclusive(3);
        int logoutFor = 1;
        int playFor = 1;
        if (selected == 0) {
            // Login for 6-10 hours, take 14-18 hour breaks.
            logoutFor = RandomUtils.inclusive(6, 10);
            playFor = RandomUtils.inclusive(14, 18);
        } else if (selected == 1) {
            // Login for 1-6 hours, take 8 hour-3 day breaks.
            logoutFor = RandomUtils.inclusive(8, 72);
            playFor = RandomUtils.inclusive(1, 6);
        } else if (selected == 2) {
            // Play up to entire day, logout for up to the entire day.
            logoutFor = RandomUtils.inclusive(1, 24);
            playFor = RandomUtils.inclusive(1, 24);
        } else if (selected == 3) {
            // 1-12 hour intervals.
            logoutFor = RandomUtils.inclusive(1, 12);
            playFor = RandomUtils.inclusive(1, 12);
        }
        return new BotSchedule(username, Duration.ofHours(logoutFor), Duration.ofHours(playFor));
    }

    /**
     * The username of the persistent bot.
     */
    private final String username;

    /**
     * How long the bot will logout for.
     */
    private final Duration logoutFor;

    /**
     * How long the bot will play for.
     */
    private final Duration loginFor;

    /**
     * Creates a new {@link BotSchedule}.
     *
     * @param username The username of the persistent bot.
     * @param logoutFor When the bot is scheduled for login.
     * @param loginFor How long the bot will play for.
     */
    public BotSchedule(String username, Duration logoutFor, Duration loginFor) {
        this.username = username;
        this.logoutFor = logoutFor;
        this.loginFor = loginFor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BotSchedule)) return false;
        BotSchedule session = (BotSchedule) o;
        return Objects.equal(username, session.username);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(username);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("username", username)
                .add("logoutFor", logoutFor)
                .add("playFor", loginFor)
                .toString();
    }

    /**
     * Determines if the bot can login based on {@code timeOnline}.
     *
     * @param timeOnline The time that the bot has already spent online.
     * @return {@code true} if the bot is ready to logout.
     */
    public boolean canLogout(Duration timeOnline) {
        return timeOnline.toMinutes() > loginFor.toMinutes();
    }

    /**
     * Determines if the bot can login based on {@code logoutTime}.
     *
     * @param logoutTime The timestamp
     * @return {@code true} if the bot is ready to logout.
     */
    public boolean canLogin(Instant logoutTime) {
        if(logoutTime == null) {
            return true;
        }
        return Instant.now().isAfter(logoutTime.plus(logoutFor));
    }

    /**
     * @return The username of the persistent bot.
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return How long the bot will logout for.
     */
    public Duration getLogoutFor() {
        return logoutFor;
    }

    /**
     * @return How long the bot will play for.
     */
    public Duration getLoginFor() {
        return loginFor;
    }
}
