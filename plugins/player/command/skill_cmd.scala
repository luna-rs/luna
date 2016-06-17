import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mobile.{Player, Skill, SkillSet}

def setSkillValues(id: Int, level: Int, plr: Player) = {
  val skill = plr.skill(id)
  val set = plr.getSkills

  set.setFiringEvents(false)
  try {
    skill.setLevel(level)
    skill.setExperience(SkillSet.experienceForLevel(level))
  } finally {
    set.setFiringEvents(true)
  }

  plr.sendSkillUpdate(id)
}

>>@[CommandEvent]("all_skills_99", RIGHTS_DEV) { (msg, plr) =>

  (0 until 21).foreach(setSkillValues(_, 99, plr))

  plr.sendMessage("You successfully set all your skill levels to 99.")
}

>>@[CommandEvent]("set_skill", RIGHTS_DEV) { (msg, plr) =>
  val name = msg.getArgs()(0).capitalize
  val level = msg.getArgs()(1).toInt

  setSkillValues(Skill.getId(name), level, plr)

  plr.sendMessage(s"You successfully set your $name level to $level.")
}
