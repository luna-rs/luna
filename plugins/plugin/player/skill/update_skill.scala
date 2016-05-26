import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag
import io.luna.game.model.mobile.{Graphic, Player, Skill}
import io.luna.net.msg.out.SendSkillUpdateMessage
import io.luna.util.StringUtils

val LEVEL_UP_GRAPHIC = 199

val LEVEL_UP_TABLE = Array(
  Array(6248, 6249, 6247),
  Array(6254, 6255, 6253),
  Array(6207, 6208, 6206),
  Array(6217, 6218, 6216),
  Array(5453, 6114, 4443),
  Array(6243, 6244, 6242),
  Array(6212, 6213, 6211),
  Array(6227, 6228, 6226),
  Array(4273, 4274, 4272),
  Array(6232, 6233, 6231),
  Array(6259, 6260, 6258),
  Array(4283, 4284, 4282),
  Array(6264, 6265, 6263),
  Array(6222, 6223, 6221),
  Array(4417, 4438, 4416),
  Array(6238, 6239, 6237),
  Array(4278, 4279, 4277),
  Array(4263, 4264, 4261),
  Array(12123, 12124, 12122),
  Array(4889, 4890, 4887),
  Array(4268, 4269, 4267)
)

>>@[SkillChangeEvent](playerInstance) { (msg, plr) =>
  plr.queue(new SendSkillUpdateMessage(msg.getId))

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

    plr.graphic(new Graphic(LEVEL_UP_GRAPHIC))

    if (Skill.isCombatSkill(id)) {
      plr.flag(UpdateFlag.APPEARANCE)
    }
  }
}