import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface

/* Destroys the item if the dialogue is open. */
onargs[ButtonClickEvent](14175) { msg =>
  val interfaces = msg.plr.interfaces
  interfaces.getCurrentStandard.ifPresent {
    case inter: DestroyItemDialogueInterface => inter.destroyItem(msg.plr)
  }
  interfaces.close()
}

/* Closes the interface. */
onargs[ButtonClickEvent](14176) {
  _.plr.interfaces.close()
}