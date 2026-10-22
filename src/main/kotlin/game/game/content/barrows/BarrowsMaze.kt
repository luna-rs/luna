package game.content.barrows

import kotlin.random.Random

/**
 * Builds the packed maze state from Lost City's catacombs randomization procedure.
 * Only chamber bits 6..9 and corridor bits 10..25 change when rerolling the maze.
 *
 * @author lare96
 */
object BarrowsMaze {
    val chestDoors = BarrowsDoor.entries.filter { it.leadsToChest }

    /**
     * Chooses one entrance chamber, one exit from it and one door into the chest room.
     */
    fun randomize(parent: Int, random: Random = Random.Default): Int {
        val chestDoor = chestDoors.random(random)
        val chamber = BarrowsChamber.entries.random(random)
        return layout(parent, chamber, chamber.doors.random(random), chestDoor)
    }

    /**
     * Composes one of the 64 possible layouts while retaining the other fields in parent 452.
     */
    fun layout(parent: Int, chamber: BarrowsChamber, exit: BarrowsDoor, chest: BarrowsDoor): Int {
        require(exit in chamber.doors)
        require(chest.leadsToChest)
        var value = parent
        for (candidate in BarrowsChamber.entries) {
            value = candidate.enabled.write(value, if (candidate == chamber) 1 else 0)
        }
        for (door in BarrowsDoor.entries) {
            val locked = (door.leadsToChest && door != chest) || (door in chamber.doors && door != exit)
            value = door.locked.write(value, if (locked) 1 else 0)
        }
        return value
    }
}
