package game.content.gnomeBall

import io.luna.game.event.impl.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController

/**
 * Reserves both hands for Gnome Ball and prevents ordinary trade or combat from moving the match ball.
 * The pitch is not a safe-death area: tackles use the normal damage and death pipeline.
 *
 * @author lare96
 */
class GnomeBallController(private val player: Player) : PlayerController(player) {
    override fun combat(victim: Mob): Boolean = false
    override fun logout(): Boolean { GnomeBall.leave(player); return true }
    override fun unregister() { if (GnomeBall.session(player) != null) GnomeBall.leave(player) }
    override fun move() { if (!GnomeBall.inside(player.position)) GnomeBall.leave(player) }
    override fun process() { if (!player.isAlive) GnomeBall.leave(player) }
    override fun event(event: ControllableEvent): Boolean {
        if (event is PlayerClickEvent.PlayerFourthClickEvent) return false
        if (event is EquipItemEvent && Item(event.itemId).equipDef?.index in listOf(Equipment.WEAPON, Equipment.SHIELD)) {
            player.sendMessage("Keep your hands free while on the Gnome Ball pitch.")
            return false
        }
        if (event is WidgetItemClickEvent && event.widgetId == 1688 && event.index == Equipment.WEAPON &&
            GnomeBall.hasBall(player)) return false
        if (event is DropItemEvent && event.itemId == GnomeBall.BALL) return false
        return true
    }
}
