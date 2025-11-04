package api.controller

import api.attr.Attr
import api.predef.*
import com.google.common.collect.ImmutableSet
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerLocationController
import io.luna.game.model.mob.overlay.WalkableInterface
import game.skill.magic.teleportSpells.TeleportAction

/**
 * A [PlayerLocationController] implementation for the wilderness area.
 *
 * @author lare96
 */
object WildernessAreaController : PlayerLocationController() {

    /**
     * The player's current wilderness level. Will be `0` if not in the wilderness.
     */
    var Player.wildernessLevel by Attr.int()

    override fun canEnter(plr: Player, newPos: Position): Boolean {
        setWildernessLevel(plr, newPos)
        plr.overlays.open(WalkableInterface(197))
        plr.interactions.show(INTERACTION_ATTACK)
        return true
    }

    override fun canExit(plr: Player, newPos: Position): Boolean {
        plr.interactions.hide(INTERACTION_ATTACK)
        plr.overlays.closeWalkable()
        plr.wildernessLevel = 0
        plr.clearText(199)
        return true
    }

    override fun canMove(plr: Player, newPos: Position): Boolean {
        setWildernessLevel(plr, newPos)
        return true
    }

    override fun computeLocations(): ImmutableSet<Locatable> = ImmutableSet.of(
        Area.of(2944, 3519, 3392, 3966)
    )

    override fun canTeleport(player: Player, action: TeleportAction): Boolean {
        return canTeleport(player)
    }

    override fun canAttack(player: Player, target: Mob): Boolean {
        if(target is Player) {
            val combatLevel = player.combatLevel
            val wildernessLevel = player.wildernessLevel
            val attackRange = combatLevel - wildernessLevel..combatLevel + wildernessLevel
            val attackable = attackRange.contains(target.asPlr().combatLevel)
            if(!attackable) {
                player.sendMessage("Your level difference is too great!")
                player.sendMessage("You need to move deeper into the Wilderness.")
                return false
            }
            return true
        }
        return true
    }

    /**
     * Sets and displays the wilderness level for [plr].
     */
    private fun setWildernessLevel(plr: Player, newPos: Position): Int {
        var newLevel = if (newPos.y > 6400) newPos.y - 6400 else newPos.y
        newLevel = ((newLevel - 3520) / 8) + 1
        plr.wildernessLevel = newLevel
        plr.sendText("@yel@Level: $newLevel", 199)
        return newLevel
    }

    /**
     * Determines if the player can teleport.
     */
    private fun canTeleport(plr: Player): Boolean {
        if (plr.wildernessLevel >= 20) {
            plr.sendMessage("A mysterious force blocks your teleport spell!")
            plr.sendMessage("You can't use this teleport after level 20 Wilderness.")
            return false
        }
        return true
    }
}