import io.luna.game.event.Event
import io.luna.game.event.impl.{ButtonClickEvent, EquipmentChangeEvent}
import io.luna.game.model.item.Equipment.RING
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.GameTabSet.TabIndex


/* The ring of stone identifier. */
private val RING_OF_STONE = 6583

/* The stone morph identifier. */
private val STONE_MORPH = 2626

/* The easter ring identifier. */
private val EASTER_RING = 7927

/* All possible easter egg morph identifiers. */
private val EGGS_MORPH = Vector(3689, 3690, 3691, 3692, 3693, 3694)


/* Morphs the player into something. */
private def morph(plr: Player, msg: Event, to: Int) = {
  plr.tabs.clearAll()
  plr.tabs.set(TabIndex.INVENTORY, 6014)
  plr.sendForceTab(3)

  plr.lockMovement
  plr.transform(to)
  msg.terminate
}

/* Unmorphs the player. */
private def unmorph(plr: Player) = {
  if (plr.inventory.computeRemainingSize > 1) {
    plr.equipment.unequip(RING)

    plr.tabs.resetAll()

    plr.unlockMovement
    plr.resetTransform()
  } else {
    plr.sendMessage("You do not have enough space in your inventory.")
  }
}

/* Listens for equipment changes. */
on[EquipmentChangeEvent].run { msg =>
  if (msg.index == RING) {

    msg.newId.foreach {
      case EASTER_RING => morph(msg.plr, msg, pick(EGGS_MORPH))
      case RING_OF_STONE => morph(msg.plr, msg, STONE_MORPH)
      case _ =>
    }
  }
}

/* Unmorph button. */
on[ButtonClickEvent].
  args { 6020 }.
  run { msg => unmorph(msg.plr) }