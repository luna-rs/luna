import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.OptionDialogueInterface


/* Invoked when the player clicks an option on an option dialogue. */
private def clickOption(plr: Player, action: OptionDialogueInterface => Unit) {
  val newFunc = action.andThen(inter => plr.advanceDialogues())
  plr.interfaces.getCurrentStandard.ifPresent {
    case inter: OptionDialogueInterface => newFunc(inter)
  }
}

/* The first option dialogue (2 options). */
onargs[ButtonClickEvent](14445) { msg =>
  clickOption(msg.plr, _.firstOption(msg.plr))
}
onargs[ButtonClickEvent](14446) { msg =>
  clickOption(msg.plr, _.secondOption(msg.plr))
}

/* The second option dialogue (3 options). */
onargs[ButtonClickEvent](2471) { msg =>
  clickOption(msg.plr, _.firstOption(msg.plr))
}
onargs[ButtonClickEvent](2472) { msg =>
  clickOption(msg.plr, _.secondOption(msg.plr))
}
onargs[ButtonClickEvent](2473) { msg =>
  clickOption(msg.plr, _.thirdOption(msg.plr))
}

/* The third option dialogue (4 options). */
onargs[ButtonClickEvent](8209) { msg =>
  clickOption(msg.plr, _.firstOption(msg.plr))
}
onargs[ButtonClickEvent](8210) { msg =>
  clickOption(msg.plr, _.secondOption(msg.plr))
}
onargs[ButtonClickEvent](8211) { msg =>
  clickOption(msg.plr, _.thirdOption(msg.plr))
}
onargs[ButtonClickEvent](8212) { msg =>
  clickOption(msg.plr, _.fourthOption(msg.plr))
}

/* The fourth option dialogue (5 options). */
onargs[ButtonClickEvent](8221) { msg =>
  clickOption(msg.plr, _.firstOption(msg.plr))
}
onargs[ButtonClickEvent](8222) { msg =>
  clickOption(msg.plr, _.secondOption(msg.plr))
}
onargs[ButtonClickEvent](8223) { msg =>
  clickOption(msg.plr, _.thirdOption(msg.plr))
}
onargs[ButtonClickEvent](8224) { msg =>
  clickOption(msg.plr, _.fourthOption(msg.plr))
}
onargs[ButtonClickEvent](8225) { msg =>
  clickOption(msg.plr, _.fifthOption(msg.plr))
}
 