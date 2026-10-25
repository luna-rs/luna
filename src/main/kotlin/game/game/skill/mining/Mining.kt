package game.skill.mining

import api.predef.*
import io.luna.game.model.mob.Player

/**
 * A class containing utility functions related to the Mining skill.
 *
 * @author lare96
 */
object Mining {

    /**
     * A set of charged amulet of glory identifiers.
     */
    private val CHARGED_GLORY_IDS = setOf(1712, 1710, 1708, 1706)

    /**
     * Uncut sapphire, emerald, ruby and diamond, with their weights on the mining gem table.
     */
    private val GEMS = listOf(1623 to 32, 1621 to 16, 1619 to 8, 1617 to 2)

    /**
     * Uncut opal, jade, red topaz, sapphire, emerald, ruby and diamond, with their weights out of 128 on the gem rock
     * table.
     */
    private val GEM_ROCK_GEMS = listOf(1625 to 60, 1627 to 30, 1629 to 15, 1623 to 9, 1621 to 5, 1619 to 5, 1617 to 4)

    /**
     * Determines if [plr] is wearing a charged amulet of glory.
     */
    fun hasChargedGlory(plr: Player) = CHARGED_GLORY_IDS.contains(plr.equipment.amulet?.id ?: -1)

    /**
     * The gem roll made before each ore roll: 1 in 256, or 1 in 86 with a charged amulet of glory worn. The gem table
     * itself gives nothing 1 time in 70.
     *
     * @return The uncut gem found, or `null`.
     */
    fun rollGem(plr: Player): Int? {
        if (rand().nextInt(if (hasChargedGlory(plr)) 86 else 256) != 0 || rand().nextInt(70) == 0) {
            return null
        }
        return roll(GEMS)
    }

    /**
     * Rolls the gem rock table, which always gives a gem.
     */
    fun rollGemRockTable(): Int = roll(GEM_ROCK_GEMS)

    /**
     * Picks an item from [table] by its weight.
     */
    private fun roll(table: List<Pair<Int, Int>>): Int {
        var roll = rand().nextInt(table.sumOf { it.second })
        for ((id, weight) in table) {
            roll -= weight
            if (roll < 0) {
                return id
            }
        }
        throw IllegalStateException("The roll is always below the total weight.")
    }

    /**
     * The uncut gem [id] named as its cut gem, with an article, like "a Sapphire" or "an Opal".
     */
    fun gemName(id: Int): String {
        val gem = itemName(id).removePrefix("Uncut ").replaceFirstChar { it.uppercase() }
        val article = if (gem.first().lowercaseChar() in "aeiou") "an" else "a"
        return "$article $gem"
    }

    /**
     * The message for finding the uncut gem [id] while mining, like "You just found a Sapphire!".
     */
    fun gemMessage(id: Int) = "You just found ${gemName(id)}!"
}
