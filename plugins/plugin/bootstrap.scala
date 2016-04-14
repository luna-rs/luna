import java.util.concurrent.ThreadLocalRandom

import io.luna.game.event.{Event, EventListener}
import io.luna.game.model.mobile.attr.AttributeValue
import io.luna.game.model.mobile.{MobileEntity, Npc, Player, PlayerRights}
import io.luna.game.model.{Position, World}
import io.luna.game.plugin.PluginFailureException
import io.luna.game.task.Task
import io.luna.net.msg.out.{SendForceTabMessage, SendGameInfoMessage, SendWidgetTextMessage}

import scala.reflect.ClassTag
import scala.util.Random

/** A bootstrapper acting as the "master dependency" for all other plugins. All of the complex, high level, 'dirty work' is
  * done in this plugin in order to ensure that other plugins can be written as idiomatically as possible.
  *
  * The interception of posted events can be handled through the '>>' (intercept) and '>>@' (intercept at/on) methods. '>>' for
  * generic events and '>>@' for events that override the 'matches' method in the Event class. The only difference is that
  * '>>@' takes a set of arguments that will matched against the events arguments.
  *
  * Also, because this plugin acts as a master dependency great caution needs to be taken when modifying its contents. Changing
  * and/or removing the wrong thing could result in breaking every single plugin.
  */

// context instances
@inline val plugins = ctx.getPlugins
@inline val world = ctx.getWorld
@inline val service = ctx.getService


// common constants
@inline val rightsPlayer = PlayerRights.PLAYER
@inline val rightsMod = PlayerRights.MODERATOR
@inline val rightsAdmin = PlayerRights.ADMINISTRATOR
@inline val rightsDev = PlayerRights.DEVELOPER


// logging, prefer lazy 'msg' evaluation
def log(msg: Any) = logger.info(String.valueOf(msg))
def logIf(cond: Boolean, msg: => Any) = if (cond) {log(msg)}


// preconditions and plugin failure, prefer lazy 'msg' evaluation
def fail(msg: Any = "execution failure") = throw new PluginFailureException(msg)
def failIf(cond: Boolean, msg: => Any = "cond == false") = if (cond) {fail(msg)}


// aliases for utilities
def rand = ThreadLocalRandom.current


// message handling
def >>@[T <: Event](args: Any*)
                   (func: (T, Player) => Unit)
                   (implicit tag: ClassTag[T]) =
  plugins.submit(tag.runtimeClass, new EventListener((msg: T, plr) => if (msg.matches(args)) {func(msg, plr)}))

def >>[T <: Event](func: (T, Player) => Unit)
                  (implicit tag: ClassTag[T]) =
  plugins.submit(tag.runtimeClass, new EventListener(func))


// misc. global methods
def async(func: () => Unit) = service.submit(new Runnable {
  override def run() = {
    try {
      func()
    } catch {case e: Exception => e.printStackTrace()}
  }
})

def using(resource: AutoCloseable)
         (func: AutoCloseable => Unit) = {
  try {
    func(resource)
  } finally {
    resource.close()
  }
}


// enriched classes
implicit class PlayerImplicits(player: Player) {
  def address = player.getSession.getHostAddress
  def x = player.getPosition.getX
  def y = player.getPosition.getY
  def z = player.getPosition.getZ
  def sendMessage(message: String) = player.queue(new SendGameInfoMessage(message))
  def sendWidgetText(text: String, widget: Int) = player.queue(new SendWidgetTextMessage(text, widget))
  def sendForceTab(id: Int) = player.queue(new SendForceTabMessage(id))
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
    val npc = new Npc(ctx, id, position)
    world.getNpcs.add(npc)
    npc
  }
  def schedule(instant: Boolean = false, delay: Int)(action: Task => Unit) = {
    world.schedule(new Task(instant, delay) {
      override protected def execute() = action(this)
    })
  }
}

implicit class ArrayImplicits[T](array: Array[T]) {
  def shuffle = {
    var i = array.length - 1
    while (i > 0) {
      val index = rand.nextInt(i + 1)
      val a = array(index)
      array(index) = array(i)
      array(i) = a
      i -= 1
    }
    array
  }

  def randElement = array((rand.nextDouble * array.length).toInt)
}

implicit class SeqImplicits[T](seq: Seq[T]) {
  def shuffle = Random.shuffle(seq)
  def randElement = seq((rand.nextDouble * seq.length).toInt)
}