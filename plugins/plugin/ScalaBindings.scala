package plugin

import java.util.Objects
import java.util.concurrent.ThreadLocalRandom

import com.google.common.base.Preconditions
import io.luna.game.model.mobile.attr.AttributeValue
import io.luna.game.model.mobile.{MobileEntity, Npc, Player, PlayerRights}
import io.luna.game.model.{Position, World}
import io.luna.game.task.Task
import io.luna.net.msg.out.{SendGameInfoMessage, SendWidgetTextMessage}

/** A trait containing Scala code that interacts directly with Java code in order to allow a more idiomatic approach to writing
  * plugins. This makes plugins significantly less verbose and therefore much easier to write. For Non-`Plugin` implementation
  * Scala classes it is highly encouraged to include `import plugin.GlobalScalaBindings._` as an import statement to achieve the
  * same previously specified effects.
  * <br>
  * <br>
  * '''Please ensure that great caution is taken when modifying the contents of this class.''' Scala code is heavily reliant on this
  * trait and will not function if certain chunks of code are modified.
  *
  * @author lare96 <http://github.org/lare96>
  */
trait ScalaBindings {

  def rightsPlayer = PlayerRights.PLAYER
  def rightsMod = PlayerRights.MODERATOR
  def rightsAdmin = PlayerRights.ADMINISTRATOR
  def rightsDev = PlayerRights.DEVELOPER

  def nonNull[T](obj: T, msg: Any = "obj (is) null") = Objects.requireNonNull(obj, String.valueOf(msg))
  def checkState(cond: Boolean, msg: Any = "cond (is) false") = Preconditions.checkState(cond, msg)
  def checkArg(cond: Boolean, msg: Any = "cond (is) false") = Preconditions.checkArgument(cond, msg)

  def rand = ThreadLocalRandom.current


  implicit class PlayerImplicits(player: Player) {
    def address = player.getSession.getHostAddress
    def sendMessage(message: String) = player.queue(new SendGameInfoMessage(message))
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
}

/** An object whose contents can be imported to a plugin that doesn't extend `ScalaBindings`. */
object GlobalScalaBindings extends ScalaBindings
