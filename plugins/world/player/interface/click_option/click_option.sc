import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.OptionDialogueInterface


/* Invoked when the player clicks an option on an option dialogue. */
private def clickOption(plr: Player, action: OptionDialogueInterface => Unit) {
  plr.interfaces.getCurrentStandard.ifPresent {
    case inter: OptionDialogueInterface =>
      action(inter)
      if (inter.isOpen && !plr.getDialogues.isPresent) {
        plr.interfaces.close()
      } else {
        plr.advanceDialogues()
      }
  }
}


/* The first option dialogue (2 options). */
on[ButtonClickEvent].
  args { 14445 }.
  run { msg => clickOption(msg.plr, _.firstOption(msg.plr)) }

on[ButtonClickEvent].
  args { 14446 }.
  run { msg => clickOption(msg.plr, _.secondOption(msg.plr)) }

/* The second option dialogue (3 options). */
on[ButtonClickEvent].
  args { 2471 }.
  run { msg => clickOption(msg.plr, _.firstOption(msg.plr)) }

on[ButtonClickEvent].
  args { 2472 }.
  run { msg => clickOption(msg.plr, _.secondOption(msg.plr)) }

on[ButtonClickEvent].
  args { 2473 }.
  run { msg => clickOption(msg.plr, _.thirdOption(msg.plr)) }

/* The third option dialogue (4 options). */
on[ButtonClickEvent].
  args { 8209 }.
  run { msg => clickOption(msg.plr, _.firstOption(msg.plr)) }

on[ButtonClickEvent].
  args { 8210 }.
  run { msg => clickOption(msg.plr, _.secondOption(msg.plr)) }

on[ButtonClickEvent].
  args { 8211 }.
  run { msg => clickOption(msg.plr, _.thirdOption(msg.plr)) }

on[ButtonClickEvent].
  args { 8212 }.
  run { msg => clickOption(msg.plr, _.fourthOption(msg.plr)) }

/* The fourth option dialogue (5 options). */
on[ButtonClickEvent].
  args { 8221 }.
  run { msg => clickOption(msg.plr, _.firstOption(msg.plr)) }

on[ButtonClickEvent].
  args { 8222 }.
  run { msg => clickOption(msg.plr, _.secondOption(msg.plr)) }

on[ButtonClickEvent].
  args { 8223 }.
  run { msg => clickOption(msg.plr, _.thirdOption(msg.plr)) }

on[ButtonClickEvent].
  args { 8224 }.
  run { msg => clickOption(msg.plr, _.fourthOption(msg.plr)) }

on[ButtonClickEvent].
  args { 8225 }.
  run { msg => clickOption(msg.plr, _.fifthOption(msg.plr)) }
 