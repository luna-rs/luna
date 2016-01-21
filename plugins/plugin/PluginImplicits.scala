package plugin

import java.util.concurrent.ThreadLocalRandom

import io.luna.game.model.mobile.attr.AttributeValue
import io.luna.game.model.mobile.{MobileEntity, Npc, Player, PlayerRights}
import io.luna.game.model.{Position, World}
import io.luna.game.task.Task
import io.luna.net.msg.out.{SendInfoMessage, SendWidgetTextMessage}

/** A trait containing methods and implicit classes that are implicitly provided to all `Plugin` implementations. This is done
  * in order to make plugins as short and easy-to-write as possible. Non-`Plugin` implementation Scala classes/traits/objects
  * '''do not''' have to extend this trait, but still should when necessary in order to keep code concise and clean.
  *
  * @author lare96 <http://github.org/lare96>
  */
trait PluginImplicits {
  implicit class PlayerImplicits(player: Player) {
    def address = player.getSession.getHostAddress
    def sendMessage(message: String) = player.queue(new SendInfoMessage(message))
    def sendWidgetText(text: String, widget: Int) = player.queue(new SendWidgetTextMessage(text, widget))
  }

  implicit class MobileEntityImplicits(mob: MobileEntity) {
    def attr[T](key: String): T = {
      val attr: AttributeValue[T] = mob.getAttributes.get(key)
      attr.get
    }
    def attr[T](key: String, value: T) = {
      val attr: AttributeValue[T] = mob.getAttributes.get(key)
      attr.set(value)
    }
  }

  implicit class WorldImplicits(world: World) {
    def addNpc(id: Int, position: Position) = {
      val npc = new Npc(world.getContext, id, position)
      world.getNpcs.add(npc)
      npc
    }
    def run(instant: Boolean = false, delay: Int, action: Task => Unit) = {
      world.schedule(new Task(instant, delay) {
        override protected def execute() = action(this)
      })
    }
  }

  implicit class BooleanImplicits(boolean: Boolean) {
    def ?[T](primary: T, secondary: T) = if (boolean) primary else secondary
  }

  def rightsPlayer = PlayerRights.PLAYER
  def rightsMod = PlayerRights.MODERATOR
  def rightsAdmin = PlayerRights.ADMINISTRATOR
  def rightsDev = PlayerRights.DEVELOPER

  def rand = ThreadLocalRandom.current
}
