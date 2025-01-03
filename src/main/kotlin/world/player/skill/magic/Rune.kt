package world.player.skill.magic

enum class Rune(val id: Int, val represents: Set<Int>) {
    AIR(id = 556, represents = setOf(556)),
    MIND(id = 556, represents = setOf(558)),
    WATER(id = 555, represents = setOf(555)),
    EARTH(id = 557, represents = setOf(557)),
    FIRE(id = 554, represents = setOf(554)),
    BODY(id = 559, represents = setOf(559)),
    COSMIC(id = 564, represents = setOf(564)),
    CHAOS(id = 562, represents = setOf(562)),
    NATURE(id = 561, represents = setOf(561)),
    LAW(id = 563, represents = setOf(563)),
    DEATH(id = 560, represents = setOf(560)),
    BLOOD(id = 565, represents = setOf(565)),
    SOUL(id = 566, represents = setOf(566)),
    MIST(id = 4695, represents = setOf(556, 555)),
    DUST(id = 4696, represents = setOf(556, 557)),
    MUD(id = 4698, represents = setOf(555, 557)),
    SMOKE(id = 4697, represents = setOf(566, 554)),
    STEAM(id = 4694, represents = setOf(555, 554)),
    LAVA(id = 556, represents = setOf(557, 554)),
} // TODO decouple combination runes