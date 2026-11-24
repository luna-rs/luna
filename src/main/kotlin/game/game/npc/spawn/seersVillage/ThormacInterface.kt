package game.npc.spawn.seersVillage

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Thormac's window, where a player picks the battlestaff he enchants into a mystic staff.
 */
class ThormacInterface : StandardInterface(205) {

    /**
     * A battlestaff Thormac enchants, and its button on the window.
     *
     * @property button The model button the staff is shown on.
     * @property battlestaff The battlestaff the player hands over.
     * @property mysticStaff The mystic staff he hands back.
     */
    enum class Staff(val button: Int, val battlestaff: Int, val mysticStaff: Int) {
        AIR(button = 1734, battlestaff = 1397, mysticStaff = 1405),
        WATER(button = 1735, battlestaff = 1395, mysticStaff = 1403),
        EARTH(button = 1736, battlestaff = 1399, mysticStaff = 1407),
        FIRE(button = 1737, battlestaff = 1393, mysticStaff = 1401),
        LAVA(button = 1738, battlestaff = 3053, mysticStaff = 3054),
        MUD(button = 15348, battlestaff = 6562, mysticStaff = 6563)
    }

    override fun onOpen(player: Player) {
        for (staff in Staff.values()) {
            player.queue(WidgetItemModelMessageWriter(staff.button, 150, staff.battlestaff))
        }
    }
}
