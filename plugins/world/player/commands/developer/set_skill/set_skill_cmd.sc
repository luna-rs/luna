import io.luna.game.event.impl.{ButtonClickEvent, CommandEvent}
import io.luna.game.model.mob.inter.{AmountInputInterface, StandardInterface}
import io.luna.game.model.mob.{Player, Skill, SkillSet}


/* An interface that allows for setting skill levels. */
private final class SetLevelInterface extends StandardInterface(2808) {
  override def onClose(player: Player) = {
    player.sendWidgetText("Choose the stat you wish to be advanced!", 2810)
  }

  override def onOpen(player: Player) = {
    player.sendWidgetText("Choose the stat to set!", 2810)
  }
}

/* A mapping of buttons to skill identifiers. */
private val BUTTONS = Map(
  2812 -> SKILL_ATTACK,
  2816 -> SKILL_DEFENCE,
  2813 -> SKILL_STRENGTH,
  2817 -> SKILL_HITPOINTS,
  2814 -> SKILL_RANGED,
  2818 -> SKILL_PRAYER,
  2815 -> SKILL_MAGIC,
  2827 -> SKILL_COOKING,
  2829 -> SKILL_WOODCUTTING,
  2830 -> SKILL_FLETCHING,
  2826 -> SKILL_FISHING,
  2828 -> SKILL_FIREMAKING,
  2822 -> SKILL_CRAFTING,
  2825 -> SKILL_SMITHING,
  2824 -> SKILL_MINING,
  2820 -> SKILL_HERBLORE,
  2819 -> SKILL_AGILITY,
  2821 -> SKILL_THIEVING,
  12034 -> SKILL_SLAYER,
  13914 -> SKILL_FARMING,
  2823 -> SKILL_RUNECRAFTING
)

/* Sets the level of a specific skill. */
private def setLevel(msg: ButtonClickEvent, id: Int) {
  msg.plr.interfaces.open(new AmountInputInterface {
    override def onAmountInput(player: Player, value: Int): Unit = {
      if (value < 1 || value > 99) {
        player.sendMessage("Level must be above or equal to 1 and below or equal to 99.")
      } else {
        val skill = player.skill(id)
        val set = player.getSkills

        set.setFiringEvents(false)
        try {
          skill.setLevel(value)
          skill.setExperience(SkillSet.experienceForLevel(value))
        } finally {
          set.setFiringEvents(true)
        }

        player.sendSkillUpdate(id)
        player.sendMessage(s"You set your ${ Skill.getName(id) } level to $value.")
      }
    }
  })
}

/* A command that sets skill levels. */
on[CommandEvent].
  args("set_skill", RIGHTS_DEV).
  run { _.plr.interfaces.open(new SetLevelInterface) }

/* A listener that listens for button clicks. */
on[ButtonClickEvent].run { msg =>
  val plr = msg.plr
  if (plr.rights >= RIGHTS_DEV) {
    BUTTONS.get(msg.id).foreach(setLevel(msg, _))
  }
}
