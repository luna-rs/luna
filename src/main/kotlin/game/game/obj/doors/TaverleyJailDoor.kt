package game.obj.doors

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The locked gates of Taverley Dungeon: the dusty-key gate into the western section and the cells of the Black
 * Knights' jail. Each is unlocked with its key from outside, the side of its own tile, and opens freely from inside.
 * The key is kept.
 *
 * @param closed The closed gate.
 * @param key The key that unlocks it.
 * @param noun What the messages call it.
 */
enum class TaverleyJailDoor(val closed: Int, val key: Int, private val noun: String) {
    DUSTY_KEY_GATE(closed = 2623, key = 1590, noun = "gate"),
    JAIL_CELL(closed = 2631, key = 1591, noun = "door");

    companion object {

        /**
         * The open gate that both swing to.
         */
        const val OPEN = 37

        private val byId = values().associateBy { it.closed }

        /**
         * @return The gate with [id], or `null` if there is none.
         */
        fun forId(id: Int): TaverleyJailDoor? = byId[id]
    }

    private val type by lazy { DoorType(closed, OPEN, null, Sound.GRATE_OPEN, null, 3) }

    /**
     * Clicking Open, which only lets a player out.
     */
    fun open(plr: Player, door: GameObject) = cross(plr, door, false)

    /**
     * Using the key, which lets a player through either way.
     */
    fun unlock(plr: Player, door: GameObject) = cross(plr, door, true)

    private fun cross(plr: Player, door: GameObject, keyUsed: Boolean) {
        if (plr.actions.first(Crossing::class.java) == null) {
            plr.submitAction(Crossing(plr, door, keyUsed))
        }
    }

    private inner class Crossing(plr: Player, door: GameObject, private val keyUsed: Boolean) :
        DoorPassageAction(plr, door, type) {
        override fun checkAccess(entering: Boolean): Boolean {
            if (entering && !keyUsed) {
                mob.sendMessage("This $noun is locked.")
                return false
            }
            if (keyUsed && !mob.inventory.contains(key)) {
                return false
            }
            mob.sendMessage(if (keyUsed) "You unlock the $noun." else "The $noun locks shut behind you.")
            return true
        }
    }
}
