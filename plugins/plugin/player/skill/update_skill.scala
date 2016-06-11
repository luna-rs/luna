import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag
import io.luna.game.model.mobile.{Graphic, Player, Skill}
import io.luna.util.StringUtils

val LEVEL_UP_GRAPHIC = new Graphic(199)

val LEVEL_UP_TABLE = Vector(
  Vector(6248, 6249, 6247),
  Vector(6254, 6255, 6253),
  Vector(6207, 6208, 6206),
  Vector(6217, 6218, 6216),
  Vector(5453, 6114, 4443),
  Vector(6243, 6244, 6242),
  Vector(6212, 6213, 6211),
  Vector(6227, 6228, 6226),
  Vector(4273, 4274, 4272),
  Vector(6232, 6233, 6231),
  Vector(6259, 6260, 6258),
  Vector(4283, 4284, 4282),
  Vector(6264, 6265, 6263),
  Vector(6222, 6223, 6221),
  Vector(4417, 4438, 4416),
  Vector(6238, 6239, 6237),
  Vector(4278, 4279, 4277),
  Vector(4263, 4264, 4261),
  Vector(12123, 12124, 12122),
  Vector(4889, 4890, 4887),
  Vector(4268, 4269, 4267)
)

>>@[SkillChangeEvent](playerInstance) { (msg, plr) =>
  plr.sendSkillUpdate(msg.getId)

  if (msg.getOldStaticLevel < 99) {
    checkForLevel(msg.getId, msg.getOldStaticLevel, plr)
  }
}

private def checkForLevel(id: Int, oldLevel: Int, plr: Player) = {
  val set = plr.getSkills
  val skill = plr.skill(id)
  val newLevel = skill.getStaticLevel

  if (oldLevel < newLevel) {
    skill.setLevel(if (id != Skill.HITPOINTS) newLevel else skill.getLevel + 1)

    val data = LEVEL_UP_TABLE(id)
    val name = Skill.getName(id)
    val message = s"Congratulations, you just advanced ${StringUtils.computeIndefiniteArticle(name)} $name level!"

    plr.sendMessage(message)
    plr.sendWidgetText(message, data(0))
    plr.sendWidgetText(s"Your $name level is now $newLevel.", data(1))
    plr.sendChatboxInterface(data(2))

    plr.graphic(LEVEL_UP_GRAPHIC)

    if (Skill.isCombatSkill(id)) {
      set.resetCombatLevel
      plr.flag(UpdateFlag.APPEARANCE)
    }
  }
}