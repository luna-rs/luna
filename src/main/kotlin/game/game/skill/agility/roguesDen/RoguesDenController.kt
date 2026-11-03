package game.skill.agility.roguesDen

import game.skill.agility.roguesDen.RoguesDen.denRun
import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.event.impl.GroundItemClickEvent
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFifthClickEvent
import io.luna.game.event.impl.UseItemEvent
import io.luna.game.event.impl.WalkingEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController

/**
 * Keeps a player in the maze until the run ends: no teleports, the jewel stays in the pack, and every step and click
 * is noted for the traps.
 *
 * @author TheLining
 */
class RoguesDenController(private val plr: Player) : PlayerController(plr) {

    override fun teleport(action: TeleportAction) = false

    override fun move() {
        plr.denRun?.walked?.add(plr.position)
    }

    override fun event(event: ControllableEvent): Boolean {
        val run = plr.denRun ?: return true
        if (event is DropItemEvent && event.itemId == RoguesDen.JEWEL) {
            plr.sendMessage("You don't want to lose that in here!")
            return false
        }
        // A trap the player walks up to search doesn't fire under them on the way; any other click or walk ends that.
        if (event is ObjectFifthClickEvent && event.gameObject.position in RoguesDenMap.trapsAt) {
            run.searching = event.gameObject.position
        } else if (event is WalkingEvent || event is ObjectClickEvent || event is NpcClickEvent ||
                   event is UseItemEvent || event is GroundItemClickEvent) {
            run.searching = null
        }
        return true
    }
}
