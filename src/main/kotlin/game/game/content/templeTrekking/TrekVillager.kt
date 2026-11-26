package game.content.templeTrekking

/**
 * How hard a villager is to escort. Weaker villagers draw more monsters into each fight, which is what makes their
 * treks worth more.
 *
 * @property monsters How many monsters a fight brings.
 * @property defence The villager's answer to "Could you defend yourself...?".
 */
enum class TrekTier(val monsters: Int, val defence: String) {
    EASY(3, "Let's just say that I'm somewhat experienced and I'm happy to take on my fair share of bad guys. You " +
        "don't need to worry about that."),
    MEDIUM(4, "I can defend myself somewhat if we run into trouble...yes, but let's hope that we don't, eh!"),
    HARD(5, "What sort of guide are you? I'm pretty healthy, but you'll have to protect me else I'll be sure to " +
        "die... Oh, I do hope you can manage that.")
}

/**
 * The Burgh de Rott villagers who want guiding to Paterdomus.
 *
 * @property parent The NPC spawned in Burgh de Rott, shown once varbit 1954 is set.
 * @property npc The villager itself, which also follows the player through the trek.
 * @property tier How hard the villager is to escort.
 * @property fights Whether the villager hits back when attacked.
 */
enum class TrekVillager(val parent: Int, val npc: Int, val tier: TrekTier, val fights: Boolean) {
    SMIDDI_RYAK(3623, 3624, TrekTier.HARD, false),
    ROLAYNE_TWICKIT(3625, 3626, TrekTier.HARD, true),
    JAYENE_KLIYN(3627, 3628, TrekTier.MEDIUM, true),
    VALANTAY_EPPEL(3629, 3630, TrekTier.MEDIUM, true),
    DALCIAN_FANG(3631, 3632, TrekTier.EASY, true),
    FYIONA_FRAY(3633, 3634, TrekTier.EASY, true);

    /**
     * The villager's name, as the cache gives it.
     */
    val displayName = name.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }

    companion object {

        /**
         * The villager for an NPC id, whether the parent placed in town or the villager itself.
         */
        fun of(id: Int): TrekVillager? = entries.firstOrNull { it.parent == id || it.npc == id }
    }
}
