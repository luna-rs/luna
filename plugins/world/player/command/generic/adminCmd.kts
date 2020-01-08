package world.player.command.generic

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.SkillSet
import world.player.command.cmd

/**
 * A command fthat makes all stats 99.
 */
cmd("master", RIGHTS_ADMIN) {
    plr.skills.forEach { it.experience = SkillSet.MAXIMUM_EXPERIENCE.toDouble() }
    plr.hitpoints.level = 99
    plr.sendMessage("You have set all your skills to level 99.")
}

/**
 * A command that moves a player to a different position.
 */
cmd("move", RIGHTS_ADMIN) {
    val x = asInt(0)
    val y = asInt(1)
    val z = when {
        args.size == 3 -> asInt(2)
        else -> plr.position.z
    }
    plr.teleport(Position(x, y, z))
}

/**
 * A command that shuts the the server down after 60 seconds.
 */
cmd("shutdown", RIGHTS_ADMIN) {
    plr.newDialogue().options(
            "2 Minutes", { game.scheduleSystemUpdate(200) },
            "4 Minutes", { game.scheduleSystemUpdate(400) },
            "8 Minutes", { game.scheduleSystemUpdate(800) },
            "16 Minutes", { game.scheduleSystemUpdate(1600) }).open()
}

/**
 * A command that opens the player's bank.
 */
cmd("bank", RIGHTS_ADMIN) { plr.bank.open() }

/**
 * A command that turns the player into a non-player character.
 */
cmd("to_npc", RIGHTS_ADMIN) {
    val id = asInt(0)
    plr.transform(id)
}

/**
 * A command that spawns an item.
 */
cmd("item", RIGHTS_ADMIN) {
    val id = asInt(0)
    val amount = if (args.size == 2) asInt(1) else 1
    plr.inventory.add(Item(id, amount))
}