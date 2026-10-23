package game.skill

/**
 * Configuration container for skill-related gameplay behavior.
 *
 * @author lare96
 */
class SkillsSettings(
    private val woodcuttingSpeed: Int = 4,
    private val slayerEquipmentNeeded: Boolean = false,
    private val maxFiremakingLightTicks: Int = 10,
    private val teleOtherThrottleSeconds: Int = 30,
) {

    /**
     * The interval (in game ticks) between each attempt to cut a log while woodcutting.
     *
     * Lower values result in faster log collection, while higher values slow it down. A value of `4` matches the
     * timing behavior in both 317 and OSRS.
     */
    fun woodcuttingSpeed() = woodcuttingSpeed

    /**
     * When enabled, players must wear the appropriate Slayer gear (e.g., masks, helmets, protective items) in order
     * to successfully attack specific monsters tied to Slayer mechanics.
     *
     * When disabled, Slayer monsters can be fought without their normally required equipment.
     */
    fun slayerEquipmentNeeded() = slayerEquipmentNeeded

    /**
     * The maximum number of game ticks a player may wait before an ignite action succeeds.
     *
     * If the player does not manually light the log within this duration, the system will trigger the lighting
     * action once this threshold (measured in server ticks) is reached.
     */
    fun maxFiremakingLightTicks() = maxFiremakingLightTicks

    /**
     * The cooldown duration, in seconds, that must elapse between successive tele-other requests sent to the same
     * target player.
     *
     * This throttle prevents players from repeatedly spamming tele-other requests and ensures a minimum delay between
     * attempts.
     */
    fun teleOtherThrottleSeconds() = teleOtherThrottleSeconds
}