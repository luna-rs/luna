package world.player.skill.magic

/**
 * An enum representing all combination runes that can represent [Rune] types.
 */
enum class CombinationRune(val id: Int, val represents: Set<Rune>) {
    MIST(id = 4695, represents = setOf(Rune.AIR, Rune.WATER)),
    DUST(id = 4696, represents = setOf(Rune.AIR, Rune.EARTH)),
    MUD(id = 4698, represents = setOf(Rune.WATER, Rune.EARTH)),
    SMOKE(id = 4697, represents = setOf(Rune.AIR, Rune.FIRE)),
    STEAM(id = 4694, represents = setOf(Rune.WATER, Rune.FIRE)),
    LAVA(id = 4699, represents = setOf(Rune.EARTH, Rune.FIRE));

    companion object {

        /**
         * An immutable map of identifiers to rune instances.
         */
        val ID_TO_RUNE = values().associateBy { it.id }
    }
}