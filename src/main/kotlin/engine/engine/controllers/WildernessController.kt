package engine.controllers

import api.attr.Attr
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.item.degradable.DegradableItems.ringOfLifeActive
import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController

/**
 * A [PlayerController] that enforces wilderness-specific gameplay rules.
 *
 * This controller is registered while a player is inside the wilderness.
 *
 * @author lare96
 */
class WildernessController(private val plr: Player) : PlayerController(plr) {

    override fun teleport(action: TeleportAction): Boolean {
        if(plr.ringOfLifeActive) {
            return plr.wildernessLevel <= 30
        }
        if (plr.wildernessLevel >= 20) {
            plr.sendMessage("A mysterious force blocks your teleport spell!")
            plr.sendMessage("You can't use this teleport after level 20 Wilderness.")
            return false
        }
        return true
    }

    override fun combat(victim: Mob): Boolean {
        // Combat level checks not needed for NPCs.
        if (victim is Npc) {
            return true
        }

        // Do combat level checks for players.
        val combatLevel = plr.combatLevel
        val wildernessLevel = plr.wildernessLevel
        val attackRange = combatLevel - wildernessLevel..combatLevel + wildernessLevel
        val attackable = attackRange.contains(victim.combatLevel)
        if (!attackable) {
            plr.sendMessage("Your level difference is too great!")
            plr.sendMessage("You need to move deeper into the Wilderness.")
            return false
        }
        return true
    }

    override fun move() {
        val position = plr.position
        var newLevel = if (position.y > 6400) position.y - 6400 else position.y
        newLevel = ((newLevel - 3520) / 8) + 1
        if (plr.wildernessLevel != newLevel) {
            plr.wildernessLevel = newLevel
            plr.sendText("@yel@Level: $newLevel", 199)
        }
    }
}