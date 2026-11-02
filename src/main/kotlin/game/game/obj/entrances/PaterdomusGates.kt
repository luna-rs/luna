package game.obj.entrances

import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.player.Sound
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The two metal gates on the basement path beneath Paterdomus, using Lost City's next-stage model and
 * open-and-close passage. The western ladder, eastern trapdoor and Holy barrier retain their native routes.
 * TODO Restore the separate Priest in Peril progress checks once that quest is implemented, in keeping with
 * the entrance catalogue's existing deferred-quest policy.
 *
 * @author lare96
 */
object PaterdomusGates {

    val IDS = setOf(3444, 3445)

    /**
     * Uses the normal weak passage action so another click or walking away cancels the approach.
     */
    fun open(player: Player, gate: GameObject) {
        if (gate.id !in IDS) return
        val type = DoorType(gate.id, 3446, null, Sound.GRATE_OPEN, Sound.GRATE_CLOSE, 3)
        player.submitAction(object : DoorPassageAction(player, gate, type) {
            override fun checkAccess(entering: Boolean) = true
        })
    }
}
