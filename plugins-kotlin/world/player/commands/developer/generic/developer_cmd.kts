import api.*
import io.luna.game.event.impl.CommandEvent
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
on(CommandEvent::class)
    .args("attr", RIGHTS_DEV)
    .run {
        val plr = it.plr
        val name = it.args[0]
        if (plr.isAttr(name)) {
            val value = plr.attr<String>(name)
            plr.sendMessage("attribute{name=$name, current_value=$value}")
        } else {
            plr.sendMessage("Attribute '$name' does not exist.")
        }
    }

/**
 * A command that moves a player to a different position.
 */
on(CommandEvent::class)
    .args("move", RIGHTS_DEV)
    .run {
        val plr = it.plr
        val args = it.args
        val x = it.asInt(0)
        val y = it.asInt(1)
        val z = when {
            args.size == 3 -> it.asInt(2)
            else -> plr.position.z
        }
        plr.teleport(Position(x, y, z))
    }

/**
 * A command that shuts the the server down after 60 seconds.
 */
on(CommandEvent::class)
    .args("shutdown", RIGHTS_DEV)
    .run {
        it.plr.newDialogue().options(
                "2 Minutes", { service.scheduleSystemUpdate(200) },
                "4 Minutes", { service.scheduleSystemUpdate(400) },
                "8 Minutes", { service.scheduleSystemUpdate(800) },
                "16 Minutes", { service.scheduleSystemUpdate(1600) }).open()
    }

/**
 * A command that opens the player's bank.
 */
on(CommandEvent::class)
    .args("bank", RIGHTS_DEV)
    .run { it.plr.bank.open() }

/**
 * A command that spawns a non-player character.
 */
on(CommandEvent::class)
    .args("npc", RIGHTS_DEV)
    .run {
        val npc = Npc(ctx, it.asInt(0), it.plr.position)
        world.add(npc)
    }

/**
 * A command that will play music.
 */
on(CommandEvent::class)
    .args("music", RIGHTS_DEV)
    .run {
        val id = it.asInt(0)
        it.plr.queue(MusicMessageWriter(id))
    }

/**
 * A command that opens an interface.
 */
on(CommandEvent::class)
    .args("interface", RIGHTS_DEV)
    .run {
        val id = it.asInt(0)
        it.plr.interfaces.open(StandardInterface(id))
    }

/**
 * A command that plays a sound.
 */
on(CommandEvent::class)
    .args("sound", RIGHTS_DEV)
    .run {
        val id = it.asInt(0)
        it.plr.queue(SoundMessageWriter(id, 0, 0))
    }

/**
 * A command that plays a graphic.
 */
on(CommandEvent::class)
    .args("graphic", RIGHTS_DEV)
    .run {
        val id = it.asInt(0)
        it.plr.graphic(Graphic(id))
    }

/**
 * A command that plays an animation.
 */
on(CommandEvent::class)
    .args("animation", RIGHTS_DEV)
    .run {
        val id = it.asInt(0)
        it.plr.animation(Animation(id))
    }

/**
 * A command that turns the player into a non-player character.
 */
on(CommandEvent::class)
    .args("to_npc", RIGHTS_DEV)
    .run {
        val id = it.asInt(0)
        it.plr.transform(id)
    }

/**
 *  A command that spawns an item.
 */
on(CommandEvent::class)
    .args("item", RIGHTS_DEV)
    .run {
        val id = it.asInt(0)
        val amount = if (it.args.size == 2) it.asInt(1) else 1
        it.plr.inventory.add(Item(id, amount))
    }

/**
 * A command that clears the inventory, bank, and equipment of a player.
 */
on(CommandEvent::class)
    .args("empty", RIGHTS_DEV)
    .run { msg ->
        msg.plr.newDialogue().options(
                "Empty inventory.", { it.inventory.clear() },
                "Empty bank.", { it.bank.clear() },
                "Empty equipment.", { it.equipment.clear() }).open()
    }