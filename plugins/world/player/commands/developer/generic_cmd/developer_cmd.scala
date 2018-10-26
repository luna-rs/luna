import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob._
import io.luna.game.model.mob.inter.StandardInterface


/* A command that allows for attribute values to be retrieved. */
onargs[CommandEvent]("attr", RIGHTS_DEV) { msg =>
  val plr = msg.plr
  val name = msg.args(0)
  if (plr.isAttr(name)) {
    plr.sendMessage(s"attribute{name=$name, current_value=${plr.attr(name)}}")
  } else {
    plr.sendMessage(s"Attribute '$name' does not exist.")
  }
}

/* A command that moves a player to a different position. */
onargs[CommandEvent]("move", RIGHTS_DEV) { msg =>
  val args = msg.args

  val x = args(0).toInt
  val y = args(1).toInt
  val z = if (args.length == 3) args(2).toInt else msg.plr.z

  msg.plr.teleport(new Position(x, y, z))
}

/* A command that shuts the the server down after 60 seconds. */
onargs[CommandEvent]("shutdown", RIGHTS_DEV) { msg =>
  def shutdown(ticks: Int) = service.scheduleSystemUpdate(ticks)

  msg.plr.newDialogue.options("2 Minutes", plr => shutdown(200),
    "4 Minutes", plr => shutdown(400),
    "8 Minutes", plr => shutdown(800),
    "16 Minutes", plr => shutdown(1600)).open()
}

/* A command that opens the player's bank. */
onargs[CommandEvent]("bank", RIGHTS_DEV) { msg => msg.plr.bank.open }


/* A command that spawns a non-player character. */
onargs[CommandEvent]("npc", RIGHTS_DEV) { msg =>
  world.add(new Npc(ctx, msg.args(0).toInt, msg.plr.position))
}

/* A command that will play music. */
onargs[CommandEvent]("music", RIGHTS_DEV) { msg =>
  msg.plr.sendMusic(msg.args(0).toInt)
}

/* A command that opens an interface. */
onargs[CommandEvent]("interface", RIGHTS_DEV) { msg =>
  msg.plr.interfaces.open(new StandardInterface(msg.args(0).toInt))
}

/* A command that plays a souend. */
onargs[CommandEvent]("sound", RIGHTS_DEV) { msg =>
  msg.plr.sendSound(msg.args(0).toInt, 0, 0)
}

/* A command that plays a graphic. */
onargs[CommandEvent]("graphic", RIGHTS_DEV) { msg =>
  msg.plr.graphic(new Graphic(msg.args(0).toInt))
}

/* A command that plays an animation. */
onargs[CommandEvent]("animation", RIGHTS_DEV) { msg =>
  msg.plr.animation(new Animation(msg.args(0).toInt))
}

/* A command that turns a player into a non-player character. */
onargs[CommandEvent]("player_npc", RIGHTS_DEV) { msg =>
  msg.plr.transform(msg.args(0).toInt)
}

/* A command that spawns an item. */
onargs[CommandEvent]("item", RIGHTS_DEV) { msg =>
  val plr = msg.plr
  val id = msg.args(0).toInt
  val amount = msg.args(1).toInt

  plr.inventory.add(new Item(id, amount))
}

/* A command that clears the inventory, bank, and equipment of a player. */
onargs[CommandEvent]("empty", RIGHTS_DEV) { msg =>
  msg.plr.newDialogue.options(
    "Empty inventory.", _.inventory.clear(),
    "Empty bank.", _.bank.clear(),
    "Empty equipment.", _.equipment.clear()).open()
}