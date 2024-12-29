package world.player.skill.magic.teleportSpells

import io.luna.game.action.Action
import io.luna.game.action.RepeatingAction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import world.player.skill.magic.Magic
import world.player.skill.magic.SpellRequirement

/**
 * A [RepeatingAction] that teleports a player to another destination.
 */
abstract class TeleportAction(plr: Player,
                              val level: Int = 1,
                              val destination: Position,
                              val style: TeleportStyle,
                              val requirements: List<SpellRequirement> = emptyList()) :
    RepeatingAction<Player>(plr, true, 1) {

    override fun start(): Boolean = Magic.checkRequirements(mob, level, requirements)

    override fun repeat() {
        if (executions == 0) {
            onTeleport()
        }
        if (!style.action(this)) {
            interrupt()
        }
    }

    override fun ignoreIf(other: Action<*>?) =
        when (other) {
            is TeleportAction -> destination == other.destination && style == other.style
            else -> false
        }

    /**
     * Invoked one tick before the teleportation starts. Add experience, send teleport messages, etc. here.
     */
    abstract fun onTeleport()
}