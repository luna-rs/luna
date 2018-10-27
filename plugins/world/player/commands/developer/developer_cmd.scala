import com.google.common.primitives.{Doubles, Ints}
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.Position
import io.luna.game.model.`def`.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob._
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.mob.inter.StandardInterface


/* A command that allows for attributes to be dynamically retrieved or set. */
onargs[CommandEvent]("attr", RIGHTS_DEV) { msg =>
  val plr = msg.plr
  val name = msg.args(0)
  val length = msg.args.length

  if (length == 1) {
    plr.sendMessage(s"attribute{name=$name, current_value=${plr.attr(name)}}")
  } else if (length == 2) {
    val oldValue = plr.attr(name)
    val newValue = msg.args(1)

    if (Doubles.tryParse(newValue) != null) {
      plr.attr(name, newValue.toDouble)
    } else if (Ints.tryParse(newValue) != null) {
      plr.attr(name, newValue.toInt)
    } else if (newValue == "true" || newValue == "false") {
      plr.attr(name, newValue.toBoolean)
    } else {
      plr.attr(name, newValue)
    }
    plr.sendMessage(s"attribute{name=$name, old_value=$oldValue, new_value=$newValue}")
  }
}

/* A command that sends a message depicting the player's current position. */
onargs[CommandEvent]("mypos", RIGHTS_DEV) { msg =>
  msg.plr.sendMessage(s"Your current position is ${msg.plr.position}.")
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
  service.scheduleSystemUpdate(100)
}

/* A command that opens the player's bank. */
onargs[CommandEvent]("bank", RIGHTS_DEV) { msg => msg.plr.bank.open }

/*
 A command that sets the skill level for a player. "all" will set all skills to the
 specified level.
*/
onargs[CommandEvent]("set_skill", RIGHTS_DEV) { msg =>
  val plr = msg.plr
  val name = msg.args(0).capitalize
  val level = msg.args(1).toInt
  val skills = if (name.equals("All")) {
    0 until 21
  } else {
    val id = Skill.getId(name)
    id until id + 1
  }

  skills.foreach { id =>
    val skill = plr.skill(id)
    val set = plr.getSkills

    set.setFiringEvents(false)
    try {
      skill.setLevel(level)
      skill.setExperience(SkillSet.experienceForLevel(level))
    } finally {
      set.setFiringEvents(true)
    }

    plr.sendSkillUpdate(id)
    plr.sendMessage(s"You successfully set your ${Skill.getName(id)} level to $level.")
  }
}

/* A command that returns details about the player's current region coordinates. */
onargs[CommandEvent]("local", RIGHTS_DEV) { msg =>
  val pos = msg.plr.position
  val coords = pos.getRegionCoordinates
  msg.plr.sendMessage(coords.toString)
  msg.plr.sendMessage(s"[local_x: ${coords.getLocalX(pos)}, local_y: ${coords.getLocalY(pos)}]")
}

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

/* A command that spawns item(s) by name. */
onargs[CommandEvent]("item_name", RIGHTS_DEV) { msg =>
  val plr = msg.plr
  val name = msg.args(0).toLowerCase.replaceAll("_", " ")
  val amount = msg.args(1).toInt

  var count = 0
  val filtered = ItemDefinition.ALL.
    lazyFilterNot(_.isNoted).
    lazyFilter(_.getName.toLowerCase.contains(name))


  filtered.foreach(definition => {
    val add = new Item(definition.getId, amount)

    if (plr.inventory.hasSpaceFor(add)) {
      plr.inventory.add(add)
    } else if (plr.bank.hasSpaceFor(add)) {
      plr.bank.add(add)
    } else {
      plr.sendMessage(s"Not enough space in bank or inventory for ${definition.getName}.")
    }
    count += 1
  })

  plr.sendMessage(s"Found $count items during lookup, with search term [$name].")
}

/* A command that clears the inventory, bank, and equipment of a player. */
onargs[CommandEvent]("empty", RIGHTS_DEV) { msg =>
  msg.plr.newDialogue.options(
    "Empty inventory.", _.inventory.clear(),
    "Empty bank.", _.bank.clear(),
    "Empty equipment.", _.equipment.clear()).open()
}