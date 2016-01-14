package plugin

import java.util.concurrent.ThreadLocalRandom

import io.luna.LunaContext
import io.luna.game.GameService
import io.luna.game.model.mobile.attr.AttributeValue
import io.luna.game.model.mobile.{Npc, Player, PlayerRights}
import io.luna.game.model.{Position, World}
import io.luna.game.plugin.{PluginManager, PluginPipeline}
import io.luna.game.task.Task
import io.luna.net.msg.out.{SendInfoMessage, SendWidgetTextMessage}

/** A model that acts as the base class for every single `Plugin`. The entire body of the extending class is implicitly executed
  * by the `PluginPipeline`. A collection of methods that are defined in this class are used to shortcut to commonly used
  * verbose methods, to keep all `Plugin` instances as short and as easy to write as possible.
  * <br>
  * <br>
  * The mutable, ugly looking fields suck but they're needed because Scala only supports a method of compile-time metaprogramming
  * and not run-time metaprogramming. Without them `Plugin` classes would be incredibly more verbose.
  * <br>
  * <br>
  * `DelayedInit` must be used until the Scala development team provides a workaround.
  *
  * @author lare96 <http://github.org/lare96>
  */
class Plugin[E] extends DelayedInit {

  implicit class WorldImplicits(world: World) {
    def addNpc(id: Int, position: Position) = world.getNpcs.add(new Npc(ctx, id, position))
  }

  var ctx: LunaContext = _
  var world: World = _
  var plugins: PluginManager = _
  var service: GameService = _
  var pipeline: PluginPipeline[E] = _
  var p: Player = _
  var evt: E = _
  var execute: () => Unit = _

  override def delayedInit(x: => Unit) = execute = () => x

  def rightsPlayer = PlayerRights.PLAYER
  def rightsMod = PlayerRights.MODERATOR
  def rightsAdmin = PlayerRights.ADMINISTRATOR
  def rightsDev = PlayerRights.DEVELOPER

  def rand = ThreadLocalRandom.current

  def get[T](key: String): T = {
    val attr: AttributeValue[T] = p.getAttr.get(key)
    attr.get
  }

  def set[T](key: String, value: T) = {
    val attr: AttributeValue[T] = p.getAttr.get(key)
    attr.set(value)
  }

  def schedule(instant: Boolean = false, delay: Int, action: Task => Unit) = {
    world.schedule(new Task(instant, delay) {
      override def execute() {
        action.apply(this)
      }
    })
  }

  def sendMessage(message: String) = p.queue(new SendInfoMessage(message))
  def sendWidgetText(text: String, widget: Int) = p.queue(new SendWidgetTextMessage(text, widget))
}