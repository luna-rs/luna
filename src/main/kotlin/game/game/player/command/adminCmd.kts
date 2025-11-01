package game.player.command

import api.predef.*
import com.google.common.primitives.Ints
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.chunk.Chunk
import io.luna.game.model.item.Bank.DynamicBankInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.SkillSet
import io.luna.game.model.mob.inter.NumberInputInterface

/**
 * A command that makes all stats 99.
 */
cmd("master", RIGHTS_ADMIN) {
    plr.skills.forEach { it.experience = SkillSet.MAXIMUM_EXPERIENCE.toDouble() }
    plr.hitpoints.level = 99
    plr.sendMessage("You have set all your skills to level 99.")
}

/**
 * A command that clears the inventory, bank, or equipment of a player.
 */
cmd("empty", RIGHTS_ADMIN) {
    plr.newDialogue().options(
        "Empty inventory.", { it.inventory.clear() },
        "Empty bank.", { it.bank.clear() },
        "Empty equipment.", { it.equipment.clear() }).open()
}

/**
 * A command that makes the player view someone's bank.
 */
cmd("viewbank", RIGHTS_ADMIN) {
    val viewing = getInputFrom(0)
    val viewingPlr = world.getPlayer(viewing).orElseThrow()
    val bankInterface = object : DynamicBankInterface("The bank of ${viewingPlr.username}") {
        override fun buildDisplayItems(player: Player?): MutableList<Item> =
            viewingPlr.bank.filterNotNull().toMutableList()
    }
    plr.interfaces.open(bankInterface)
}

/**
 * A command that moves the player to another player.
 */
cmd("moveto", RIGHTS_ADMIN) {
    val name = getInputFrom(0)
    val target = world.getPlayer(name.lowercase())
    if(target.isPresent) {
        plr.move(target.get().position)
    } else {
        plr.sendMessage("Player '$name' not found.")
    }
}

/**
 * A command that teleports the player to an object matching the entered name.
 */
cmd("teleobj", RIGHTS_ADMIN) {
    val name = getInputFrom(0)
    val id = Ints.tryParse(name)
    val it = world.objects.iterator()
    val list = ArrayList<Position>()
    while (it.hasNext()) {
        val obj = it.next()
        if ((id != null && obj.id == id) || obj.definition.name.equals(name, true)) {
            list += obj.position
        }
    }
    if(list.isNotEmpty()) {
        plr.move(list.random())
    } else {
        plr.sendMessage("No object location found for [name:$name|id:$id].")
    }
}

/**
 * A command that adds experience.
 */
cmd("addxp", RIGHTS_ADMIN) {
    val index = asInt(0)
    val amount = asInt(1)
    plr.skill(index).addExperience(amount.toDouble())
    plr.sendMessage("You have added $amount experience to skill ${Skill.getName(index)}.")
}

/**
 * A command that teleports you to the specified region.
 */
cmd("region", RIGHTS_ADMIN) {
    val id = asInt(0)
    val pos = Region(id).absPosition
    plr.move(pos)
}

/**
 * A command that teleports you to the specified chunk.
 */
cmd("chunk", RIGHTS_ADMIN) {
    val x = asInt(0)
    val y = asInt(1)
    val pos = Chunk(x, y).absPosition
    plr.move(pos)
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
    plr.move(Position(x, y, z))
}

/**
 * A command that moves a player up.
 */
cmd("up", RIGHTS_ADMIN) {
    plr.move(plr.position.translate(0, 0, 1))
}

/**
 * A command that moves a player down.
 */
cmd("down", RIGHTS_ADMIN) {
    plr.move(plr.position.translate(0, 0, -1))
}

/**
 * A command that shuts the server down after <x> seconds.
 */
cmd("shutdown", RIGHTS_ADMIN) {
    plr.newDialogue().options(
        "Now", { gameService.scheduleSystemUpdate(8) },
        "1 Minute", { gameService.scheduleSystemUpdate(100) },
        "5 Minutes", { gameService.scheduleSystemUpdate(500) },
        "10 Minutes", { gameService.scheduleSystemUpdate(800) },
        "<x> Minutes", {
            plr.interfaces.close()
            plr.interfaces.open(object : NumberInputInterface() {
                override fun onAmountInput(player: Player, value: Int) {
                    if (value < 1 || value > 60) {
                        plr.newDialogue().empty("1-60 Minutes are the acceptable values. Please try again.").open()
                        return
                    }
                    gameService.scheduleSystemUpdate(value * 100)
                    plr.interfaces.close()
                }
            })
        }).open()
}

/**
 * A command that changes your rights.
 */
cmd("set_rights", RIGHTS_ADMIN) {
    val rights = args[0]
    for (r in PlayerRights.values()) {
        if (r.name.equals(rights, true)) {
            plr.rights = r
            plr.sendMessage("Your player rights have been set to ${plr.rights}.")
            return@cmd
        }
    }
    plr.sendMessage("No rights found for [$rights]")
    plr.sendMessage("Input should be 'player' 'administrator' 'developer' etc.")
}

/**
 * A command that opens the player's bank.
 */
cmd("engine/bank", RIGHTS_ADMIN) { plr.bank.open() }

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
cmd("game/item", RIGHTS_ADMIN) {
    val id = asInt(0)
    val amount = if (args.size == 2) asInt(1) else 1
    plr.inventory.add(Item(id, amount))
}
