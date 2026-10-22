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
     * The gem roll made before each ore roll: 1 in 256, or 1 in 86 with a charged amulet of glory worn. The gem table
     * itself gives nothing 1 time in 70.
     *
     * @return The uncut gem found, or `null`.
     */
    fun rollGem(plr: Player): Int? {
        val hasChargedGlory = CHARGED_GLORY_IDS.contains(plr.equipment.amulet?.id ?: -1)
        if (rand().nextInt(if (hasChargedGlory) 86 else 256) != 0 || rand().nextInt(70) == 0) {
            return null
        }
        var roll = rand().nextInt(GEMS.sumOf { it.second })
        for ((id, weight) in GEMS) {
            roll -= weight
            if (roll < 0) {
                return id
            }
        }
        return null
    }

    /**
     * The message for finding the uncut gem [id] while mining, like "You just found a Sapphire!".
     */
    fun gemMessage(id: Int): String {
        val gem = itemName(id).removePrefix("Uncut ").replaceFirstChar { it.uppercase() }
        val article = if (gem.first().lowercaseChar() in "aeiou") "an" else "a"
        return "You just found $article $gem!"
    }
}
