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
     * The chance that a newly created bot will enjoy trading with other players.
     */
    private final double merchantingChance;

    /**
     * Optional maximum merchant sale stacks per batch. Omitted settings use seven stacks.
     */
    private Integer merchantSaleStackLimit;

    /**
     * Optional minimum total asking value for each merchant sale stack. Omitted settings use 1,000 GP.
     */
    private Long merchantSaleMinimumStackValue;

    /**
     * The base value an item stack must have for a bot to loot it. Affected by combat level, intelligence, and
     * greed.
     */
    private final int baseLootValue;

    /**
     * Optional new-account scavenger fraction. Omitted settings use two percent.
     */
    private Double newAccountScavengingChance;

    /**
     * Optional new-account rune-runner fraction. Omitted settings use two percent.
     */
    private Double newAccountRuneRunningChance;

    /**
     * Returns the configured one-to-three percent fraction; zero explicitly disables assignment.
     */
    public double newAccountRuneRunningChance() {
        if (newAccountRuneRunningChance == null || !Double.isFinite(newAccountRuneRunningChance)) return 0.02;
        return newAccountRuneRunningChance == 0 ? 0 : Math.clamp(newAccountRuneRunningChance, 0.01, 0.03);
    }

    /**
     * Returns the configured one-to-three percent fraction; zero explicitly disables assignment.
     */
    public double newAccountScavengingChance() {
        if (newAccountScavengingChance == null || !Double.isFinite(newAccountScavengingChance)) return 0.02;
        return newAccountScavengingChance == 0 ? 0 : Math.clamp(newAccountScavengingChance, 0.01, 0.03);
    }

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

    /**
     * Returns the chance that a newly created bot will enjoy trading with other players.
     */
    public double merchantingChance() {
        return merchantingChance;
    }

    /**
     * Returns the merchant sale stack limit, bounded to 1-27 to reserve an inventory slot for payment.
     */
    public int merchantSaleStackLimit() {
        return merchantSaleStackLimit == null ? 7 : Math.clamp(merchantSaleStackLimit, 1, 27);
    }

    /**
     * Returns the non-negative asking value that each merchant sale stack must strictly exceed.
     */
    public long merchantSaleMinimumStackValue() {
        return merchantSaleMinimumStackValue == null ? 1_000L : Math.max(0L, merchantSaleMinimumStackValue);
    }

    /**
     * Returns the base value an item stack must have for a bot to loot it. Affected by combat level, intelligence,
     * and greed.
     */
    public int baseLootValue() {
        return baseLootValue;
    }

    // Never called.
    private BotSettings(int baseOnlineCount, int baseLoginHours, int baseLogoutHours, int minimumLoginHours,
                        int maximumLoginHours, int minimumLogoutHours, int maximumLogoutHours, double highIntelligenceChance,
                        double lowIntelligenceChance, double merchantingChance, int baseLootValue) {
        this.baseOnlineCount = baseOnlineCount;
        this.baseLoginHours = baseLoginHours;
        this.baseLogoutHours = baseLogoutHours;
        this.minimumLoginHours = minimumLoginHours;
        this.maximumLoginHours = maximumLoginHours;
        this.minimumLogoutHours = minimumLogoutHours;
        this.maximumLogoutHours = maximumLogoutHours;
        this.highIntelligenceChance = highIntelligenceChance;
        this.lowIntelligenceChance = lowIntelligenceChance;
        this.merchantingChance = merchantingChance;
        this.baseLootValue = baseLootValue;
    }
}
