import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob._
import io.luna.game.model.mob.inter.StandardInterface


/* A command that allows for attribute values to be retrieved. */
on[CommandEvent].
  args("attr", RIGHTS_DEV).
  run { msg =>
    val plr = msg.plr
    val name = msg.args(0)
    if (plr.isAttr(name)) {
      plr.sendMessage(s"attribute{name=$name, current_value=${ plr.attr(name) }}")
    } else {
      plr.sendMessage(s"Attribute '$name' does not exist.")
    }
  }

/* A command that moves a player to a different position. */
on[CommandEvent].
  args("move", RIGHTS_DEV).
  run { msg =>
    val args = msg.args
    val x = args(0).toInt
    val y = args(1).toInt
    val z = if (args.length == 3) args(2).toInt else msg.plr.z
    msg.plr.teleport(new Position(x, y, z))
  }

/* A command that shuts the the server down after 60 seconds. */
on[CommandEvent].
  args("shutdown", RIGHTS_DEV).
  run { msg =>
    def shutdown(ticks: Int): Player => Unit =
      plr => service.scheduleSystemUpdate(ticks)

    msg.plr.newDialogue.options("2 Minutes", shutdown(200),
      "4 Minutes", shutdown(400),
      "8 Minutes", shutdown(800),
      "16 Minutes", shutdown(1600)).open()
  }

/* A command that opens the player's bank. */
on[CommandEvent].
  args("bank", RIGHTS_DEV).
  run { _.plr.bank.open }


/* A command that spawns a non-player character. */
on[CommandEvent].
  args("npc", RIGHTS_DEV).
  run { msg => world.add(new Npc(ctx, msg.args(0).toInt, msg.plr.position)) }

/* A command that will play music. */
on[CommandEvent].
  args("music", RIGHTS_DEV).
  run { msg => msg.plr.sendMusic(msg.args(0).toInt) }

/* A command that opens an interface. */
on[CommandEvent].
  args("interface", RIGHTS_DEV).
  run { msg => msg.plr.interfaces.open(new StandardInterface(msg.args(0).toInt)) }

/* A command that plays a souend. */
on[CommandEvent].
  args("sound", RIGHTS_DEV).
  run { msg => msg.plr.sendSound(msg.args(0).toInt, 0, 0) }

/* A command that plays a graphic. */
on[CommandEvent].
  args("graphic", RIGHTS_DEV).
  run { msg => msg.plr.graphic(new Graphic(msg.args(0).toInt)) }

/* A command that plays an animation. */
on[CommandEvent].
  args("animation", RIGHTS_DEV).
  run { msg => msg.plr.animation(new Animation(msg.args(0).toInt)) }

/* A command that turns a player into a non-player character. */
on[CommandEvent].
  args("player_npc", RIGHTS_DEV).
  run { msg => msg.plr.transform(msg.args(0).toInt) }

/* A command that spawns an item. */
on[CommandEvent].
  args("item", RIGHTS_DEV).
  run { msg =>
    val plr = msg.plr
    val id = msg.args(0).toInt
    val amount = msg.args(1).toInt
    plr.inventory.add(new Item(id, amount))
  }

/* A command that clears the inventory, bank, and equipment of a player. */
on[CommandEvent].
  args("empty", RIGHTS_DEV).
  run {
    _.plr.newDialogue.options(
      "Empty inventory.", _.inventory.clear(),
      "Empty bank.", _.bank.clear(),
      "Empty equipment.", _.equipment.clear()).open()
  }