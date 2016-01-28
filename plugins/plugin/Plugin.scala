package plugin

import io.luna.LunaContext
import io.luna.game.GameService
import io.luna.game.model.World
import io.luna.game.model.mobile.Player
import io.luna.game.plugin.{PluginManager, PluginPipeline}

/** A model that acts as the base class for every single `Plugin`. The entire body of the extending class is implicitly executed
  * by the `PluginPipeline`. A collection of methods and implicit classes that are defined in `ScalaBindings` are used to
  * keep all `Plugin` instances as short and as easy to write as possible.
  * <p>
  * The mutable, ugly looking fields suck but they're needed because there is no other way of injecting state into plugins. Without
  * them we would need to make all `Plugin` classes override a method with the fields as parameters instead.
  * <p>
  * `DelayedInit` will be used until either the Scala development team provides a workaround to its pitfalls or removes
  * it (in which case reflection will just be used instead).
  *
  * @author lare96 <http://github.org/lare96>
  */
class Plugin[E] extends DelayedInit with ScalaBindings {

  var ctx: LunaContext = _
  var world: World = _
  var plugins: PluginManager = _
  var service: GameService = _
  var pipeline: PluginPipeline[E] = _
  var plr: Player = _
  var evt: E = _
  var execute: () => Unit = _

  override def delayedInit(x: => Unit) = execute = () => x
}

class LoginEvent
class LogoutEvent

class AddNpcsEvent
class AddItemsEvent
class AddObjectsEvent