import io.luna.game.event.Event
import io.luna.game.event.impl.{ButtonClickEvent, EquipmentChangeEvent}
import io.luna.game.model.item.Equipment.RING
import io.luna.game.model.mobile.Player


private val RING_OF_STONE = 6583
private val STONE_MORPH = 2626

private val EASTER_RING = 7927
private val EGGS_MORPH = Vector(3689, 3690, 3691, 3692, 3693, 3694)


private def morph(plr: Player, msg: Event, to: Int) = {
  (0 to 13).filterNot(_ == 3).foreach(plr.sendTabInterface(_, -1))
  plr.sendForceTab(3)
  plr.sendTabInterface(3, 6014)

  plr.transform(to)
  msg.terminate
}

private def unmorph(plr: Player) = {
  if (plr.inventory.computeRemainingSize > 1) {
    plr.equipment.unequip(RING)
    plr.displayTabInterfaces()
    plr.transform(-1)
  } else {
    plr.sendMessage("You do not have enough space in your inventory.")
  }
}


intercept[EquipmentChangeEvent] { (msg, plr) =>
  if (msg.getIndex == RING) {
    val optionId = msg.getNewItem.map(_.getId)
    optionId.foreach {
      case RING_OF_STONE => morph(plr, msg, STONE_MORPH)
      case EASTER_RING => morph(plr, msg, rand(EGGS_MORPH))
    }
  }
}

// unmorph (figure out proper id)
intercept_@[ButtonClickEvent](23132) { (msg, plr) =>
  unmorph(plr)
}