package game.content.mageTrainingArena

import game.content.mageTrainingArena.MageTrainingArena.arenaRoom
import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController

/**
 * Keeps a player to the rules of the training room they are in. Leaving by anything but an exit teleport puts the
 * player back at the entrance with the room's items taken away.
 *
 * @author TheLining
 */
class ArenaController(private val plr: Player, val room: ArenaRoom) : PlayerController(plr) {

    override fun process() {
        if (plr.arenaRoom === room) room.process(plr)
    }

    override fun move() {
        if (plr.arenaRoom === room && !room.inside(plr.position)) MageTrainingArena.leave(plr, false)
    }

    override fun logout(): Boolean {
        MageTrainingArena.leave(plr, false)
        return true
    }

    override fun getPersistencePosition(): Position =
        if (plr.arenaRoom === room) MageTrainingArena.ENTRANCE else super.getPersistencePosition()

    override fun teleport(action: TeleportAction): Boolean {
        if (room === CreatureGraveyard) {
            plr.sendMessage("You can't teleport out of the Creature Graveyard.")
            return false
        }
        // Teleporting out of a room lands the player at the entrance, without what they picked up in the room.
        MageTrainingArena.leave(plr, false)
        return false
    }

    override fun event(event: ControllableEvent): Boolean = room.event(plr, event)

    override fun unregister() {
        if (plr.arenaRoom === room) {
            plr.arenaRoom = null
            room.leave(plr, false)
            MageTrainingArena.removeRoomItems(plr, room)
            plr.overlays.closeWalkable()
        }
    }
}
