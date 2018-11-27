import $file.arrow
import arrow._
import io.luna.game.action.{Action, ProducingAction}
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface


final class MakeArrowAction(val plr: Player,
                            val arrow: Arrow,
                            var count: Int) extends ProducingAction(plr, true, 3) {
  // TODO make it so you don't need exactly 15 to do this
  val useWithItem = new Item(arrow.useWith, SET_AMOUNT)
  val arrowItem = new Item(arrow.arrow)
  val arrowTipItem = new Item(arrow.arrowTip, SET_AMOUNT)
  val fletching = mob.getSkills.getSkill(SKILL_FLETCHING)

  override protected def add = Array(arrowItem)

  override protected def remove = Array(useWithItem, arrowTipItem)

  override protected def canProduce = {
    if (fletching.getLevel < arrow.level) {
      mob.sendMessage(s"You need a Fletching level of ${ arrow.level } to attach this.")
      false
    } else if (count == 0 || !mob.inventory.containsAll(useWithItem, arrowTipItem)) {
      false
    } else {
      true
    }
  }

  override protected def onProduce() = {
    val useWith = nameOfItem(arrow.useWith)
    val arrowTip = nameOfItem(arrow.arrowTip)
    mob.sendMessage(s"You attach the $arrowTip to the $useWith.")
    fletching.addExperience(arrow.exp)
    count -= 1
  }

  override protected def isEqual(other: Action[_]) =
    other match {
      case action: MakeArrowAction => arrow == action.arrow
      case _ => false
    }
}


private def openInterface(msg: ItemOnItemEvent, arrow: Arrow): Unit = {
  msg.plr.interfaces.open(new MakeItemDialogueInterface(arrow.arrow) {
    override def makeItem(player: Player, id: Int, index: Int, forAmount: Int) = {
      player.submitAction(new MakeArrowAction(player, arrow, forAmount))
    }
  })
}


on[ItemOnItemEvent].
  condition { msg => msg.targetId == HEADLESS_ARROW || msg.usedId == HEADLESS_ARROW }.
  run { msg =>
    if (msg.targetId == HEADLESS_ARROW) // check used id
      ARROWTIP_TO_ARROW.get(msg.usedId).foreach(openInterface(msg, _))
    else // check target id
      ARROWTIP_TO_ARROW.get(msg.targetId).foreach(openInterface(msg, _))
  }

ARROW_MAP.get('headless_arrow).foreach { arrow =>
  on[ItemOnItemEvent].
    args(arrow.arrowTip, arrow.useWith).
    run { openInterface(_, arrow) }
}
