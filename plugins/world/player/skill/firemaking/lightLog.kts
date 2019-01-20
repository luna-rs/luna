
import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.DestructionAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.event.impl.PlayerEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
//import world.player.skills.firemaking.Log.Companion.ID_TO_LOG

class LightLogAction(plr: Player) : DestructionAction(plr, true, 1) {
    override fun remove(): Array<Item> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isEqual(other: Action<*>?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}


val tinderbox = -1

fun lightLog(id: Int, event: PlayerEvent) {


}

on(ItemOnItemEvent::class)
    .condition { matches(tinderbox) }
    .then {
        //  val log = ID_TO_LOG[id]
        //  if (log != null) {


        //    }
    }

/*

  }



   TODO:
    -> Placing object after lighting fire
    -> Dropping logs before lighting them
    -> Right clicking log on ground and using "Light" option
    -> Using tinderbox with a log on the ground
    -> Collision detection for stepping after a light (W -> E -> S -> N)
    -> Not being able to light fires on top of existing objects


import io.luna.game.event.Event
import io.luna.game.event.impl .ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Skill.FIREMAKING
import io.luna.game.model.mobile.{ Animation, Player }


private case class Log(id: Int, level: Int, exp: Double, lightTime: Int)


private val TINDERBOX = 590
private val ASHES = 592
private val LIGHT_ANIMATION = new Animation (733)

private val BURN_TIME = 30 to 90

private val LOG_TABLE = Map(

)

private val ID_TO_LOG = LOG_TABLE.values.map { it => it.id -> it }.toMap


private def burnLog(log: Log) = {
  val burnTime = BURN_TIME.randomElement

  world.scheduleOnce(burnTime) {

  }
}

private def lightLog(plr: Player, log: Log) = {
  val skill = plr.skill(FIREMAKING)

  val levelRequired = log.level
  if (skill.getLevel < levelRequired) {
      plr.sendMessage(s"You need a Firemaking level of $levelRequired to light these logs.")
      return
  }

  plr.inventory.remove(new Item (log.id))
  plr.stopWalking
  plr.interruptAction

  var loopCount: Int = ???
  world.scheduleTimes(3, loopCount) {
      if (loopCount == 0) {
          skill.addExperience(log.exp)
          burnLog(log)
      } else {
          plr.animation(LIGHT_ANIMATION)
          loopCount -= 1
      }
  }
}

private def lookupLog(id: Int, evt: Event) = {
  ID_TO_LOG.get(id).foreach {
      it =>
      burnLog(plr, it)
      evt.terminate
  }
}


intercept[ItemOnItemEvent] {
  (msg, plr) =>
  if (msg.getUsedId == TINDERBOX) {
      lookupLog(msg.getTargetId)
  } else if (msg.getTargetId == TINDERBOX) {
      lookupLog(msg.getUsedId)
  }
}
*/