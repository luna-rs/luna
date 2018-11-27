import $file.bow
import $file.log
import bow._
import io.luna.game.action.{Action, ProducingAction}
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.mob.{Animation, Player}
import log._


val BOW_STRING = 1777
val STRING_ANIMATION = new Animation(713)


final class StringBowAction(val plr: Player,
                            val bow: Bow,
                            var count: Int) extends ProducingAction(plr, true, 2) {

  val fletching = mob.getSkills.getSkill(SKILL_FLETCHING)

  override protected def add = Array(new Item(bow.strungId))

  override protected def remove = Array(new Item(bow.unstrungId), new Item(BOW_STRING))

  override protected def canProduce = {
    if (fletching.getLevel < bow.level) {
      mob.sendMessage(s"You need a Fletching level of ${ bow.level } to string this bow.")
      false
    } else if (count == 0 || !mob.inventory.containsAll(BOW_STRING, bow.unstrungId)) {
      false
    } else {
      true
    }
  }

  override protected def onProduce() = {
    mob.sendMessage("You add a string to the bow.")
    mob.animation(STRING_ANIMATION)
    fletching.addExperience(bow.exp)
    count -= 1
  }

  override protected def isEqual(other: Action[_]) =
    other match {
      case action: StringBowAction => bow == action.bow
      case _ => false
    }
}

private def openInterface(plr: Player, bow: Bow): Unit = {
  plr.interfaces.open(new MakeItemDialogueInterface(bow.strungId) {
    override def makeItem(player: Player, id: Int, index: Int, forAmount: Int) = {
      plr.submitAction(new StringBowAction(player, bow, forAmount))
    }
  })
}



on[ItemOnItemEvent].
  condition { msg => msg.targetId == BOW_STRING || msg.usedId == BOW_STRING }.
  run { msg =>
    if (msg.targetId == BOW_STRING) // check used id
      UNSTRUNG_TO_BOW.get(msg.usedId).foreach(openInterface(msg.plr, _))
    else // check target id
      UNSTRUNG_TO_BOW.get(msg.targetId).foreach(openInterface(msg.plr, _))
  }