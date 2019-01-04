
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Graphic
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.net.msg.out.MusicMessageWriter
import io.luna.net.msg.out.SoundMessageWriter

/**
 * A command that allows for attribute values to be retrieved.
 */
cmd("attr", RIGHTS_DEV) {
    val name = args[0]
    if (plr.attributes.contains(name)) {
        val value = plr.attributes.get<String>(name).get()
        plr.sendMessage("attribute{name=$name, current_value=$value}")
    } else {
        plr.sendMessage("Attribute '$name' does not exist.")
    }
}

/**
 * A command that moves a player to a different position.
 */
cmd("move", RIGHTS_DEV) {
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
cmd("shutdown", RIGHTS_DEV) {
    plr.newDialogue().options(
            "2 Minutes", { service.scheduleSystemUpdate(200) },
            "4 Minutes", { service.scheduleSystemUpdate(400) },
            "8 Minutes", { service.scheduleSystemUpdate(800) },
            "16 Minutes", { service.scheduleSystemUpdate(1600) }).open()
}

/**
 * A command that opens the player's bank.
 */
cmd("bank", RIGHTS_DEV) { plr.bank.open() }

/**
 * A command that spawns a non-player character.
 */
cmd("npc", RIGHTS_DEV) {
    val npc = Npc(ctx, asInt(0), plr.position)
    world.addNpc(npc)
}

/**
 * A command that spawns a object.
 */
cmd("object", RIGHTS_DEV) {
    val pos = plr.position
    world.addObject(id = asInt(0),
                    x = pos.x,
                    y = pos.y,
                    z = pos.z,
                    plr = plr)
}

/**
 * A command that will play music.
 */
cmd("music", RIGHTS_DEV) {
    val id = asInt(0)
    plr.queue(MusicMessageWriter(id))
}

/**
 * A command that opens an interface.
 */
cmd("interface", RIGHTS_DEV) {
    val id = asInt(0)
    plr.interfaces.open(StandardInterface(id))
}

/**
 * A command that plays a sound.
 */
cmd("sound", RIGHTS_DEV) {
    val id = asInt(0)
    plr.queue(SoundMessageWriter(id, 0, 0))
}

/**
 * A command that plays a graphic.
 */
cmd("graphic", RIGHTS_DEV) {
    val id = asInt(0)
    plr.graphic(Graphic(id))
}

/**
 * A command that plays an animation.
 */
cmd("animation", RIGHTS_DEV) {
    val id = asInt(0)
    plr.animation(Animation(id))
}

/**
 * A command that turns the player into a non-player character.
 */
cmd("to_npc", RIGHTS_DEV) {
    val id = asInt(0)
    plr.transform(id)
}

/**
 *  A command that spawns an item.
 */
cmd("item", RIGHTS_DEV) {
    val id = asInt(0)
    val amount = if (args.size == 2) asInt(1) else 1
    plr.inventory.add(Item(id, amount))
}

/**
 * A command that clears the inventory, bank, and equipment of a player.
 */
cmd("empty", RIGHTS_DEV) {
    plr.newDialogue().options(
            "Empty inventory.", { it.inventory.clear() },
            "Empty bank.", { it.bank.clear() },
            "Empty equipment.", { it.equipment.clear() }).open()
}