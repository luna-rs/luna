package engine.bot.gear;

/**
 * Describes the general purpose for equipping specific [BotGearType].
 *
 * @author lare96
 */
enum class BotGearPurpose {

    /**
     * PvP combat in the wilderness or for minigames.
     */
    PKING,

    /**
     * Social status or aesthetics.
     */
    SHOW_OFF,

    /**
     * Focused on magic combat.
     */
    MAGIC,

    /**
     * Focused on ranged combat.
     */
    RANGED,

    /**
     * Focused on melee combat.
     */
    MELEE,

    /**
     * Non-combat skill based training or money-making.
     */
    SKILLING
}