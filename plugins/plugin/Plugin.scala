package plugin

import java.util.concurrent.ThreadLocalRandom

import io.luna.LunaContext
import io.luna.game.GameService
import io.luna.game.model.World
import io.luna.game.model.mobile.attr.AttributeValue
import io.luna.game.model.mobile.{Player, PlayerRights}
import io.luna.game.plugin.{PluginManager, PluginPipeline}
import io.luna.game.task.Task
import io.luna.net.msg.out.{SendInfoMessage, SendWidgetTextMessage}

/** A model that acts as the base class for every single Plugin. The entire
  * body of the extending class is implicitly executed by the PluginPipeline. A
  * collection of methods that are defined in this class are used to shortcut
  * to commonly used verbose methods, to keep all Plugins as short and as easy
  * to write as possible.
  *
  * DelayedInit is deprecated, but it must be used until the Scala development
  * team provides a workaround (and to be fair, the reason why it's deprecated
  * will have no effect here anyway).
  *
  * @author lare96 <http://github.org/lare96>
  */
class Plugin[E] extends DelayedInit {

  var ctx: LunaContext = null
  var world: World = null
  var plugins: PluginManager = null
  var service: GameService = null
  var pipeline: PluginPipeline[E] = null
  var p: Player = null
  var evt: E = null.asInstanceOf[E]
  var execute: Function0[Unit] = null

  def rightsPlayer = PlayerRights.PLAYER
  def rightsMod = PlayerRights.MODERATOR
  def rightsAdmin = PlayerRights.ADMINISTRATOR
  def rightsDev = PlayerRights.DEVELOPER

  def rand = ThreadLocalRandom.current

  def sendMessage(message: String) = p.queue(new SendInfoMessage(message))
  def sendWidgetText(text: String, widget: Int) = p.queue(new SendWidgetTextMessage(text, widget))

  override def delayedInit(x: => Unit): Unit = {
    execute = () => {
      x
    }
  }

  def get[T](key: String): T = {
    val attr: AttributeValue[T] = p.getAttr.get(key)
    attr.get
  }

  def set[T](key: String, value: T) = {
    val attr: AttributeValue[T] = p.getAttr.get(key)
    attr.set(value)
  }

  def schedule(instant: Boolean, delay: Int, action: Function1[Task, Unit]) = {
    world.schedule(new Task(instant, delay) {
      override def execute() {
        action.apply(this)
      }
    })
  }

  def schedule(delay: Int, action: Function1[Task, Unit]): Unit = schedule(false, delay, action)
}