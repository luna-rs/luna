package game.content.seasonalEvents

/**
 * Configuration container for seasonal event behavior.
 *
 * @author lare96
 */
class SeasonalEventSettings(
    private val enabled: Boolean = true,
    private val quota: Int = 2500,
    private val lifetimeQuota: Boolean = true,
    private val waveIntervalHours: Int = 2,
    private val dropsPerWave: Int = 10
) {

    /**
     * Determines whether seasonal events are active.
     */
    fun enabled() = enabled

    /**
     * The total number of event drops that players can claim by looting them.
     *
     * Whether this limit resets when the event completes is controlled by [lifetimeQuota].
     */
    fun quota() = quota

    /**
     * Determines whether the claim quota applies across all occurrences of an event.
     *
     * If `true`, the quota never resets. If `false`, the quota resets when the event completes.
     */
    fun lifetimeQuota() = lifetimeQuota

    /**
     * The interval, in hours, between item drop waves while an event is active.
     */
    fun waveIntervalHours() = waveIntervalHours

    /**
     * The number of items dropped during each wave.
     */
    fun dropsPerWave() = dropsPerWave
}