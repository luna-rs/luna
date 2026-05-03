package io.luna.game.model.mob.bot;

/**
 * Defines configurable settings for Luna's bot population.
 * <p>
 * These settings control the baseline number of bots that should be online, the normal login/logout timing window, and
 * whether generated bot personalities should use smarter template-based variance.
 *
 * @author lare96
 */
public final class BotSettings {

    /**
     * The baseline number of bots the world should try to keep online.
     */
    private final int baseOnlineCount;

    /**
     * The baseline number of hours bots should remain logged in.
     */
    private final int baseLoginHours;

    /**
     * The baseline number of hours bots should remain logged out before returning.
     */
    private final int baseLogoutHours;

    /**
     * The minimum number of hours a bot should remain logged in before being scheduled for logout.
     */
    private final int minimumLoginHours;

    /**
     * The maximum number of hours a bot should remain logged in before being scheduled for logout.
     */
    private final int maximumLoginHours;

    /**
     * The minimum number of hours a bot should remain logged out before being scheduled to return.
     */
    private final int minimumLogoutHours;

    /**
     * The maximum number of hours a bot should remain logged out before being scheduled to return.
     */
    private final int maximumLogoutHours;

    /**
     * The chance that a newly created bot receives an unusually low intelligence value.
     */
    private final double lowIntelligenceChance;

    /**
     * The chance that a newly created bot receives an unusually high intelligence value.
     */
    private final double highIntelligenceChance;

    /**
     * Returns the baseline number of bots the world should try to keep online.
     */
    public int baseOnlineCount() {
        return baseOnlineCount;
    }

    /**
     * Returns the baseline number of hours bots should remain logged in.
     */
    public int baseLoginHours() {
        return baseLoginHours;
    }

    /**
     * Returns the baseline number of hours bots should remain logged out before returning.
     */
    public int baseLogoutHours() {
        return baseLogoutHours;
    }

    /**
     * Returns the minimum number of hours bots should remain logged in.
     */
    public int minimumLoginHours() {
        return minimumLoginHours;
    }

    /**
     * Returns the maximum number of hours bots should remain logged in.
     */
    public int maximumLoginHours() {
        return maximumLoginHours;
    }

    /**
     * Returns the minimum number of hours bots should remain logged out before returning.
     */
    public int minimumLogoutHours() {
        return minimumLogoutHours;
    }

    /**
     * Returns the maximum number of hours bots should remain logged out before returning.
     */
    public int maximumLogoutHours() {
        return maximumLogoutHours;
    }

    /**
     * Returns the chance that a newly created bot receives an unusually high intelligence value.
     */
    public double highIntelligenceChance() {
        return highIntelligenceChance;
    }

    /**
     * Returns the chance that a newly created bot receives an unusually low intelligence value.
     */
    public double lowIntelligenceChance() {
        return lowIntelligenceChance;
    }

    // Never called.
    private BotSettings(int baseOnlineCount, int baseLoginHours, int baseLogoutHours, int minimumLoginHours,
                        int maximumLoginHours, int minimumLogoutHours, int maximumLogoutHours, double highIntelligenceChance, double lowIntelligenceChance) {
        this.baseOnlineCount = baseOnlineCount;
        this.baseLoginHours = baseLoginHours;
        this.baseLogoutHours = baseLogoutHours;
        this.minimumLoginHours = minimumLoginHours;
        this.maximumLoginHours = maximumLoginHours;
        this.minimumLogoutHours = minimumLogoutHours;
        this.maximumLogoutHours = maximumLogoutHours;
        this.highIntelligenceChance = highIntelligenceChance;
        this.lowIntelligenceChance = lowIntelligenceChance;
    }
}