import io.luna.game.event.impl.ObjectFirstClickEvent
import io.luna.game.model.mobile.Skill.PRAYER
import io.luna.game.model.mobile.{Animation, Player}


/* A set of identifiers for altar objects. */
private val ALTARS = Set(409, 3243)

/* Recharge prayer animation. */
private val RECHARGE_ANIMATION = new Animation(645)


/* A method that attempts to recharge the player's prayer. */
private def rechargePrayer(plr: Player) = {
  val skill = plr.skill(PRAYER)

  if (skill.getLevel < skill.getStaticLevel) {
    skill.setLevel(skill.getStaticLevel)

    plr.animation(RECHARGE_ANIMATION)
    plr.sendMessage("You recharge your Prayer points.")
  } else {
    plr.sendMessage("You already have full Prayer points.")
  }
}


/* If the object being clicked is an altar, recharge prayer. */
>>[ObjectFirstClickEvent] { (msg, plr) =>
  if (ALTARS.contains(msg.getId)) {
    rechargePrayer(plr)
    msg.terminate
  }
}
