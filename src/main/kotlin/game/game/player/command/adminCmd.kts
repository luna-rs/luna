package game.player.command

import api.predef.*
import com.google.common.primitives.Ints
import game.skill.farming.*
import game.skill.farming.Farming.herbPatches
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.chunk.Chunk
import io.luna.game.model.item.Bank.DynamicBankInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.SkillSet
import io.luna.game.model.mob.overlay.NumberInput

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
        override fun buildDisplayItems(player: Player?): ArrayList<Item> =
            viewingPlr.bank.filterNotNull().toCollection(ArrayList())
    }
    plr.overlays.open(bankInterface)
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
        if ((id != null && obj.id == id) || obj.def().name.equals(name, true)) {
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
            plr.overlays.closeWindows()
            plr.overlays.open(object : NumberInput() {
                override fun input(player: Player, value: Int) {
                    gameService.scheduleSystemUpdate(value * 100)
                    plr.overlays.closeWindows()
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

/**
 * A command that moves a player to a different location by name.
 */
cmd("tele", RIGHTS_ADMIN) {
    var x = -1
    var y = -1
    var z = 0
    var target = getInputFrom(0).lowercase()
    var foundTarget : Boolean = false

    when(target) {
        "varrock" -> {
            x = 3210
            y = 3424
            foundTarget = true
        }
        "falador" -> {
            x = 2964
            y = 3378
            foundTarget = true
        }
        "edgeville" -> {
            x = 3093
            y = 3493
            foundTarget = true
        }
        "port_sarim" -> {
            x = 3023
            y = 3208
            foundTarget = true
        }
        "al_kharid" -> {
            x = 3293
            y = 3174
            foundTarget = true
        }
        "catherby" -> {
            x = 2813
            y = 3447
            foundTarget = true;
        }
        "seers_village" -> {
            x = 2708
            y = 3492
            foundTarget = true;
        }
        "canifis" -> {
            x = 3506
            y = 3496
            foundTarget = true;
        }
        "rimmington" -> {
            x = 2957
            y = 3214
            foundTarget = true;
        }
        "tzhaar" -> {
            x = 2480
            y = 5175
            foundTarget = true;
        }
        "lumbridge" -> {
            x = 3222
            y = 3218
            foundTarget = true;
        }
        "ardy_farm" -> {
            x = 2669
            y = 3375
            foundTarget = true;
        }
        "catherby_farm" -> {
            x = 2812
            y = 3463
            foundTarget = true;
        }
        "falador_farm" -> {
            x = 3058
            y = 3310
            foundTarget = true;
        }
        "ghost_farm" -> {
            x = 3605
            y = 3528
            foundTarget = true;
        }
        else -> {
            plr.sendMessage("Unkown location $target")
        }
    }
    if (foundTarget) {
        plr.move(Position(x, y, z))
    }
}