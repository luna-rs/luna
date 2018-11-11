import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface

/* Destroys the item if the dialogue is open. */
on[ButtonClickEvent].
  args { 14175 }.
  run { msg =>
    val interfaces = msg.plr.interfaces
    interfaces.getCurrentStandard.ifPresent {
      case inter: DestroyItemDialogueInterface => inter.destroyItem(msg.plr)
    }
    interfaces.close()
  }

/* Closes the interface. */
on[ButtonClickEvent].
  args { 14176 }.
  run { _.plr.interfaces.close() }