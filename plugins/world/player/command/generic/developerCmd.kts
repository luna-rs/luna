package world.player.command.generic

import api.predef.*
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Graphic
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.net.msg.out.ConfigMessageWriter
import io.luna.net.msg.out.MusicMessageWriter
import io.luna.net.msg.out.SoundMessageWriter
import world.player.command.cmd


/**
 * A command that sends a client config.
 */
cmd("config", RIGHTS_DEV) {
    val id = asInt(0)
    val value = if (args.size == 1) 0 else asInt(1)
    plr.sendMessage("config[$id] = $value")
    plr.queue(ConfigMessageWriter(id, value))
}

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
