import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag

// TODO: Turn this into one command using an interface, once option dialogues are done.

>>@[CommandEvent]("empty", RIGHTS_DEV) { (msg, plr) =>
  plr.inventory.clear
  plr.bank.clear
  plr.equipment.clear

  plr.flag(UpdateFlag.APPEARANCE)

  plr.sendMessage("You have successfully emptied your inventory, bank, and equipment.")
}

>>@[CommandEvent]("empty_inventory", RIGHTS_DEV) { (msg, plr) =>
  plr.inventory.clear
  plr.sendMessage("You have successfully emptied your inventory.")
}

>>@[CommandEvent]("empty_bank", RIGHTS_DEV) { (msg, plr) =>
  plr.bank.clear
  plr.sendMessage("You have successfully emptied your bank.")
}

>>@[CommandEvent]("empty_equipment", RIGHTS_DEV) { (msg, plr) =>
  plr.equipment.clear
  plr.sendMessage("You have successfully emptied your equipment.")
}
