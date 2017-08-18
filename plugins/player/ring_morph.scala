import io.luna.game.event.Event
import io.luna.game.event.impl.{ButtonClickEvent, EquipmentChangeEvent}
import io.luna.game.model.item.Equipment.RING
import io.luna.game.model.mob.Player


private val RING_OF_STONE = 6583
private val STONE_MORPH = 2626

private val EASTER_RING = 7927
private val EGGS_MORPH = Vector(3689, 3690, 3691, 3692, 3693, 3694)


private def morph(plr: Player, msg: Event, to: Int) = {
  (0 to 13).filterNot(_ == 3).foreach(plr.sendTabInterface(_, -1))
  plr.sendForceTab(3)
  plr.sendTabInterface(3, 6014)

  plr.lockMovement
  plr.transform(to)
  msg.terminate
}

private def unmorph(plr: Player) = {
  if (plr.inventory.computeRemainingSize > 1) {
    plr.equipment.unequip(RING)

    plr.displayTabInterfaces()

    plr.unlockMovement
    plr.untransform
  } else {
    plr.sendMessage("You do not have enough space in your inventory.")
  }
}


on[EquipmentChangeEvent] { msg =>
  if (msg.index == RING) {

    msg.newId.foreach {
      case RING_OF_STONE => morph(msg.plr, msg, STONE_MORPH)
      case EASTER_RING => morph(msg.plr, msg, rand(EGGS_MORPH))
      case _ =>
    }
  }
}

onargs[ButtonClickEvent](6020) { msg => unmorph(msg.plr) }