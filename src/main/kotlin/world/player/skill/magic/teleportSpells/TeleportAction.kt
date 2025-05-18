package world.player.skill.magic.teleportSpells

import api.attr.Attr
import api.predef.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import world.player.skill.magic.Magic
import world.player.skill.magic.SpellRequirement

/**
 * A [LockedAction] implementation that teleports a player to another destination.
 */
open class TeleportAction(plr: Player,
                          val level: Int = 1,
                          val xp: Double = 0.0,
                          val destination: Position,
                          val style: TeleportStyle,
                          val requirements: List<SpellRequirement> = emptyList()) : LockedAction(plr) {

    companion object {

        /**
         * An attribute representing teleport throttling.
         */
        val Player.teleportDelay by Attr.timeSource()
    }

    override fun onLock() {
        if (!mob.controllers.checkTeleport(this)) {
            complete()
            return
        }
        val removeItems = Magic.checkRequirements(mob, level, requirements)
        if (removeItems == null) {
            complete()
            return
        }
        onTeleport()
        if (removeItems.isNotEmpty()) {
            mob.inventory.removeAll(removeItems)
        }
        if (xp > 0.0) {
            mob.magic.addExperience(xp)
        }
    }

    override fun run(): Boolean = !style.action(this)

    override fun onFinished() {
        mob.unlock()
        mob.teleportDelay.reset()
    }

    /**
     * Invoked one tick before the teleportation starts. Send teleport messages, etc. here.
     */
    open fun onTeleport() {

    }
}