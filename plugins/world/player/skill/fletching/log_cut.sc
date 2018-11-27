import $file.arrow
import $file.bow
import $file.log
import arrow._
import bow._
import io.luna.game.action.{Action, ProducingAction}
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.mob.{Animation, Player}
import io.luna.util.StringUtils
import log._


val KNIFE = 946
val CUT_ANIMATION = new Animation(6782)

final class CutLogAction(val plr: Player,
                         val log: Int,
                         val bow: Bow,
                         var count: Int) extends ProducingAction(plr, true, 3) {


  val fletching = mob.getSkills.getSkill(SKILL_FLETCHING)

  override protected def add =
    Array(bow match {
      case ARROW_SHAFT => new Item(bow.unstrungId, SET_AMOUNT)
      case _ => new Item(bow.unstrungId)
    })

  override protected def remove = Array(new Item(log))

  override protected def canProduce = {
    if (fletching.getLevel < bow.level) {
      mob.sendMessage(s"You need a Fletching level of ${ bow.level } to cut this.")
      false
    } else if (count == 0 || !mob.inventory.containsAll(KNIFE, log)) {
      false
    } else {
      true
    }
  }

  override protected def onProduce() = {
    val bowName = nameOfItem(bow.unstrungId)
    mob.sendMessage(s"You carefully cut the wood into ${ StringUtils.computeArticle(bowName) }.")
    mob.animation(CUT_ANIMATION)
    fletching.addExperience(bow.exp)
    count -= 1
  }


  override protected def isEqual(other: Action[_]) =
    other match {
      case action: CutLogAction => log == action.log && bow == action.bow
      case _ => false
    }
}

private def openInterface(plr: Player, log: Log): Unit = {
  plr.interfaces.open(new MakeItemDialogueInterface(log.unstrungIds: _*) {
    override def makeItem(player: Player, id: Int, index: Int, forAmount: Int) = {
      val cutAction = new CutLogAction(player, log.id, log.bowArray(index), forAmount)
      player.submitAction(cutAction)
    }
  })
}


on[ItemOnItemEvent].
  condition { msg => msg.targetId == KNIFE || msg.usedId == KNIFE }.
  run { msg =>
    if (msg.targetId == KNIFE) // check used id
      ID_TO_LOG.get(msg.usedId).foreach { openInterface(msg.plr, _) }
    else // check target id
      ID_TO_LOG.get(msg.targetId).foreach { openInterface(msg.plr, _) }
  }