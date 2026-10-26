package game.content.castleWars

import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Npc
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent
import io.luna.game.model.mob.controller.PlayerController

/**
 * Keeps team equipment and safe combat rules attached to every Castle Wars participant.
 *
 * @author lare96
 */
class CastleWarsController(private val player: Player, val game: CastleWarsGame) : PlayerController(player) {
    override fun combat(victim: Mob): Boolean = when (victim) {
        is Player -> game.canFight(player, victim)
        is Npc -> game.match.isPlaying(player) && CastleWars.siege.isBarricade(victim)
        else -> false
    }

    override fun teleport(action: TeleportAction): Boolean {
        player.sendMessage("Leave Castle Wars through a portal before teleporting.")
        return false
    }

    override fun unregister() { game.detach(player) }

    override fun logout(): Boolean {
        game.exit(player)
        return true
    }

    override fun move() {
        if (!CastleWarsGame.inArena(player.position) && !CastleWarsGame.inWaitingRoom(player.position)) {
            game.exit(player)
        }
    }

    override fun event(event: ControllableEvent): Boolean {
        if (event is PlayerFourthClickEvent) return false
        if (event is EquipItemEvent) {
            val slot = Item(event.itemId).equipDef?.index
            if (slot == Equipment.HEAD || slot == Equipment.CAPE) return false
            if (game.match.carried(player) != null && (slot == Equipment.WEAPON || slot == Equipment.SHIELD)) return false
        }
        if (event is WidgetItemClickEvent && event.widgetId == 1688) {
            if (event.index == Equipment.CAPE) return false
            if (event.index == Equipment.WEAPON && game.match.carried(player) != null) {
                game.dropFlag(player)
                return false
            }
        }
        if (event is DropItemEvent && event.itemId in CastleWarsGame.TOOLS) {
            // Supplies are destroyed rather than becoming transferable ground items.
            if (player.inventory.contains(event.index, event.itemId)) player.inventory.remove(event.itemId)
            return false
        }
        return true
    }
}
