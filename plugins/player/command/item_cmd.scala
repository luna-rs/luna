import java.util.Objects

import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.`def`.ItemDefinition
import io.luna.game.model.item.Item

>>@[CommandEvent]("item", RIGHTS_DEV) { (msg, plr) =>
  val id = msg.getArgs()(0).toInt
  val amount = msg.getArgs()(1).toInt

  plr.inventory.add(new Item(id, amount))
}

>>@[CommandEvent]("item_name", RIGHTS_DEV) { (msg, plr) =>
  val name = msg.getArgs()(0).toLowerCase.replaceAll("_", " ")
  val amount = msg.getArgs()(1).toInt

  val filtered = ItemDefinition.DEFINITIONS.
    filter(Objects.nonNull(_)).
    filterNot(_.isNoted).
    filter(_.getName.toLowerCase.contains(name))

  filtered.foreach { it =>
    val add = new Item(it.getId, amount)
    if (plr.inventory.hasCapacityFor(add)) {plr.inventory.add(add)} else {plr.bank.add(add)}
  }

  plr.sendMessage(s"Found ${filtered.length} items during lookup, with search term [$name].")
}
