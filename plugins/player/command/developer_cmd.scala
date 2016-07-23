/*
 A set of commands that allow administrators and developers to test, create, and maintain content. Not typically used in
 a production environment, but it isn't invalid to do so.

 All developer and administrator "fun" or debugging commands should be put in this plugin to minimize confusion and
 promote the organization of future plugins.
*/

import java.util.Objects

import com.google.common.primitives.{Doubles, Ints}
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.Position
import io.luna.game.model.`def`.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mobile._


/* A command that allows for attributes to be dynamically set. */
>>@[CommandEvent]("attr", RIGHTS_DEV) { (msg, plr) =>
  val args: Array[String] = msg.getArgs
  val name = args(0)

  if (args.length == 2) {
    val oldValue: Double = plr.attr(name)
    val newValue = args(1)
    if (Doubles.tryParse(newValue) != null) {
      plr.attr(name, newValue.toDouble)
    } else if (Ints.tryParse(newValue) != null) {
      plr.attr(name, newValue.toInt)
    } else if (newValue == "true" || newValue == "false") {
      plr.attr(name, newValue.toBoolean)
    } else {
      plr.attr(name, newValue)
    }
    plr.sendMessage(s"Attribute{name=$name, oldValue=$oldValue, newValue=$newValue}")
  } else {
    plr.sendMessage(s"Attribute{name=$name, value=${plr.attr(name)}}")
  }
}

/* A command that sends a message depicting the player's current position. */
>>@[CommandEvent]("mypos", RIGHTS_DEV) { (msg, plr) =>
  plr.sendMessage(s"Your current position is ${plr.position}.")
}

/* A command that moves a player to a different position. */
>>@[CommandEvent]("move", RIGHTS_DEV) { (msg, plr) =>
  val args = msg.getArgs

  val x = args(0).toInt
  val y = args(1).toInt
  val z = if (args.length == 3) args(2).toInt else plr.z

  plr.teleport(new Position(x, y, z))
}

/* A command that opens the player's bank. */
>>@[CommandEvent]("bank", RIGHTS_DEV) { (msg, plr) =>
  plr.bank.open
}

/*
 A command that sets the skill level for a player. "all" will set all skills to the
 specified level.
*/
>>@[CommandEvent]("set_skill", RIGHTS_DEV) { (msg, plr) =>
  val name = msg.getArgs()(0).capitalize
  val level = msg.getArgs()(1).toInt
  val range = if (name.equals("All")) {
    0 until 21
  } else {
    val id = Skill.getId(name)
    id until id + 1
  }

  def setSkillValues(id: Int, level: Int) = {
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

  range.foreach(setSkillValues(_, level))
}

/* A command that spawns a non-player character. */
>>@[CommandEvent]("npc", RIGHTS_DEV) { (msg, plr) =>
  val args = msg.getArgs
  world.addNpc(args(0).toInt, plr.position)
}

/* A command that will play music. */
>>@[CommandEvent]("music", RIGHTS_DEV) { (msg, plr) =>
  plr.sendMusic(msg.getArgs()(0).toInt)
}

/* A command that opens an interface. */
>>@[CommandEvent]("interface", RIGHTS_DEV) { (msg, plr) =>
  plr.sendInterface(msg.getArgs()(0).toInt)
}

/* A command that plays a sound. */
>>@[CommandEvent]("sound", RIGHTS_DEV) { (msg, plr) =>
  plr.sendSound(msg.getArgs()(0).toInt, 0, 0)
}

/* A command that plays a graphic. */
>>@[CommandEvent]("graphic", RIGHTS_DEV) { (msg, plr) =>
  plr.graphic(new Graphic(msg.getArgs()(0).toInt))
}

/* A command that plays an animation. */
>>@[CommandEvent]("animation", RIGHTS_DEV) { (msg, plr) =>
  plr.animation(new Animation(msg.getArgs()(0).toInt))
}

/* A command that turns a player into a non-player character. */
>>@[CommandEvent]("player_npc", RIGHTS_DEV) { (msg, plr) =>
  plr.transform(msg.getArgs()(0).toInt)
}

/* A command that spawns an item. */
>>@[CommandEvent]("item", RIGHTS_DEV) { (msg, plr) =>
  val id = msg.getArgs()(0).toInt
  val amount = msg.getArgs()(1).toInt

  plr.inventory.add(new Item(id, amount))
}

/* A command that spawns item(s) by name. */
>>@[CommandEvent]("item_name", RIGHTS_DEV) { (msg, plr) =>
  val name = msg.getArgs()(0).toLowerCase.replaceAll("_", " ")
  val amount = msg.getArgs()(1).toInt

  val filtered = ItemDefinition.DEFINITIONS.
    filter(Objects.nonNull _).
    filterNot(_.isNoted).
    filter(_.getName.toLowerCase.contains(name))

  filtered.foreach { it =>
    val add = new Item(it.getId, amount)

    if (plr.inventory.hasCapacityFor(add)) {
      plr.inventory.add(add)
    } else {
      plr.bank.add(add)
    }
  }

  plr.sendMessage(s"Found ${filtered.length} items during lookup, with search term [$name].")
}

// TODO: Turn this into one command using an interface, once option dialogues are done.
/* A command that clears the inventory, bank, and equipment of a player. */
>>@[CommandEvent]("empty", RIGHTS_DEV) { (msg, plr) =>
  plr.inventory.clear
  plr.bank.clear
  plr.equipment.clear

  plr.sendMessage("You have successfully emptied your inventory, bank, and equipment.")
}

/* A command that clears the inventory of a player. */
>>@[CommandEvent]("empty_inventory", RIGHTS_DEV) { (msg, plr) =>
  plr.inventory.clear
  plr.sendMessage("You have successfully emptied your inventory.")
}

/* A command that clears the bank of a player. */
>>@[CommandEvent]("empty_bank", RIGHTS_DEV) { (msg, plr) =>
  plr.bank.clear
  plr.sendMessage("You have successfully emptied your bank.")
}

/* A command that clears the equipment of a player. */
>>@[CommandEvent]("empty_equipment", RIGHTS_DEV) { (msg, plr) =>
  plr.equipment.clear
  plr.sendMessage("You have successfully emptied your equipment.")
}
