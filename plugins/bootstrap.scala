/*
 A bootstrapper acting as the "master dependency" for all other plugins. All of the complex, high level, 'dirty work' is
 done in this plugin in order to ensure that other plugins can be written as idiomatically as possible.

 The interception of posted events can be handled through the '>>' (intercept) and '>>@' (intercept at/on) methods. '>>' for
 generic events and '>>@' for events that override the 'matches' method in the Event class. The only difference is that
 '>>@' takes a set of arguments that will matched against the events arguments.

 Please note that normal methods and fields must come before event interception.

 Also, because this plugin acts as a master dependency great caution needs to be taken when modifying its contents. Changing
 and/or removing the wrong thing could result in breaking every single plugin.
*/

import java.util.concurrent.{ThreadLocalRandom, TimeUnit}
import java.util.function.BiConsumer

import io.luna.game.event.{Event, EventListener}
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mobile._
import io.luna.game.model.mobile.attr.AttributeValue
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag
import io.luna.game.model.{Entity, EntityType, Position, World}
import io.luna.game.plugin.PluginFailureException
import io.luna.game.task.Task
import io.luna.net.msg.out._

import scala.collection.JavaConversions._
import scala.reflect.ClassTag
import scala.util.Random


/* Inlined 'LunaContext' instances injected into this bootstrap. */
@inline val plugins = ctx.getPlugins
@inline val world = ctx.getWorld
@inline val service = ctx.getService


/* Inlined aliases for 'PlayerRights'. */
@inline val RIGHTS_PLAYER = PlayerRights.PLAYER
@inline val RIGHTS_MOD = PlayerRights.MODERATOR
@inline val RIGHTS_ADMIN = PlayerRights.ADMINISTRATOR
@inline val RIGHTS_DEV = PlayerRights.DEVELOPER


/* Inlined aliases for 'EntityType'. */
@inline val TYPE_PLAYER = EntityType.PLAYER
@inline val TYPE_NPC = EntityType.NPC
@inline val TYPE_OBJECT = EntityType.OBJECT
@inline val TYPE_ITEM = EntityType.ITEM


/* Inlined aliases for various utilities. */
@inline def rand = ThreadLocalRandom.current


/* Retrieves the system time in 'MILLISECONDS' using 'System.nanoTime()'. */
def currentTimeMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime, TimeUnit.NANOSECONDS)


/* Logging functions, logger instance is injected into this bootstrap. */
def log(msg: Any) = logger.info(String.valueOf(msg))
def logIf(cond: Boolean, msg: => Any) = if (cond) {log(msg)}


/* Sort of like Guava 'Preconditions', except these throw a 'PluginFailureException'. */
def fail(msg: Any = "execution failure") = throw new PluginFailureException(msg)
def failIf(cond: Boolean, msg: => Any = "cond == false") = if (cond) {fail(msg)}


/* Converts a scala '(E, Player) => Unit' to a Java 'BiConsumer'. */
private def scalaToJavaFunc[E <: Event](func: (E, Player) => Unit) = new BiConsumer[E, Player] {
  override def accept(evt: E, plr: Player) = func.apply(evt, plr)
}


/* Intercept at (>>@) method. Allows event interception to happen with matching on parameters. */
def >>@[E <: Event](args: Any*)(func: (E, Player) => Unit)(implicit tag: ClassTag[E]) = {

  /* The Scala event listener converted to a BiConsumer. This will be executed from Java. */
  def eventListener(newArgs: Seq[AnyRef]) = scalaToJavaFunc(
    (msg: E, plr: Player) => {
      if (msg.matches(newArgs: _*)) {
        func(msg, plr)
        msg.terminate()
      }
    }
  )

  /* Submits the converted event listener function to the pipelines. */
  def submit(newArgs: Seq[AnyRef]) =
    pipelines.addEventListener(tag.runtimeClass, new EventListener(eventListener(newArgs)))

  /* Calls the 'submit' method, turns all primitive parameters into their wrapper forms for matching. */
  submit(args.collect { case any: Any => any.asInstanceOf[AnyRef] })
}

/* Intercept (>>) method. Allows for events to be intercepted, with no matching. */
def >>[E <: Event](func: (E, Player) => Unit)(implicit tag: ClassTag[E]) =
  pipelines.addEventListener(tag.runtimeClass, new EventListener(scalaToJavaFunc(func)))


/* An 'async' block. Anything inside of 'async' blocks are executed asynchronously. */
def async(func: => Unit) = service.submit(new Runnable() {
  override def run() = {
    try {
      func
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }
})


/*
 All implicit (monkey patching) classes below are basically 'extending' Java classes by creating new functions for them. We
 do this to ensure that all code coming from Java is as concise and idiomatic (Scala-like) as possible.

 Feel free to add on to the existing code, but beware:
   - Removing code will (most likely) break existing plugins
   - Functions that are too ambiguous may end up doing more harm than good (their purpose
     should be relatively obvious)
*/

/* Implicit class for 'Player' instances. Mainly consists of aliases for outbound messages. */
implicit class RichPlayer(plr: Player) {

  /* Aliases for credentials. */
  def address = plr.getSession.getHostAddress
  def name = plr.getUsername.capitalize
  def rights = plr.getRights

  /* Aliases for containers. */
  def bank = plr.getBank
  def inventory = plr.getInventory
  def equipment = plr.getEquipment

  /* Aliases for outbound messages. */
  def sendMessage(message: String) = plr.queue(new GameChatboxMessageWriter(message))
  def sendWidgetText(text: String, widget: Int) = plr.queue(new WidgetTextMessageWriter(text, widget))
  def sendForceTab(id: Int) = plr.queue(new ForceTabMessageWriter(id))
  def sendChatboxInterface(id: Int) = plr.queue(new ChatboxInterfaceMessageWriter(id))
  def sendSkillUpdate(id: Int) = plr.queue(new SkillUpdateMessageWriter(id))
  def sendMusic(id: Int) = plr.queue(new MusicMessageWriter(id))
  def sendSound(id: Int, loops: Int = 0, delay: Int = 0) = plr.queue(new SoundMessageWriter(id, loops, delay))
  def sendInterface(id: Int) = plr.queue(new InterfaceMessageWriter(id))
  def sendState(id: Int, value: Int) = plr.queue(new StateMessageWriter(id, value))
  def sendColor(id: Int, color: Int) = plr.queue(new ColorChangeMessageWriter(id, color))

  /* Alias for update flags. */
  def flag(updateFlag: UpdateFlag) = plr.getUpdateFlags.flag(updateFlag)
}


/* Implicit class for 'MobileEntity' instances. */
implicit class RichMobileEntity(mob: MobileEntity) {

  /* Retrieve an attribute. */
  def attr[T](key: String): T = {
    val attr: AttributeValue[T] = mob.getAttributes.get(key)
    attr.get
  }

  /* Set a new value for an attribute. */
  def attr[T](key: String, value: T) = {
    val attr: AttributeValue[T] = mob.getAttributes.get(key)
    attr.set(value)
  }

  /* Determine if an attribute equals a value. */
  def attrEquals(key: String, equals: Any) = equals == attr(key)

  /* Returns the elapsed time of a timer attribute. */
  def elapsedTime(key: String, ms: Long) = {
    val value: Long = attr(key)
    (currentTimeMillis - value) >= ms
  }


  /* Resets the elapsed time of a timer attribute. */
  def resetTime(key: String) = attr(key, currentTimeMillis)
}


/* Implicit class for 'PlayerRights' instances. */
implicit class RichPlayerRights(rights: PlayerRights) {

  /*
   Comparison function aliases. So we can compare rights just like we compare numbers. We
   put '@' at the end of the functions so people don't get them confused with the normal comparison operators.
  */
  def <=@(other: PlayerRights) = rights.equalOrLess(other)
  def >=@(other: PlayerRights) = rights.equalOrGreater(other)
  def >@(other: PlayerRights) = rights.greater(other)
  def <@(other: PlayerRights) = rights.less(other)
  def ==@(other: PlayerRights) = rights.equal(other)
}


/* Implicit class for 'Entity' instances. */
implicit class RichEntity(entity: Entity) {

  /* Aliases related to position. */
  def position = entity.getPosition
  def x = entity.getPosition.getX
  def y = entity.getPosition.getY
  def z = entity.getPosition.getZ
}


/* Implicit class for 'World' instances. */
implicit class RichWorld(world: World) {

  /* Adds an 'Npc' to the world and returns it. */
  def addNpc(id: Int, position: Position) = {
    val npc = new Npc(ctx, id, position)
    world.getNpcs.add(npc)
    npc
  }

  /* Schedules a task, allows us to use Scala lambdas. */
  def schedule(delay: Int, instant: Boolean = false)(action: Task => Unit) =
    world.schedule(new Task(instant, delay) {
      override protected def execute() = {
        action(this)
      }
    })

  /* Schedules a task to execute once, and then stop. */
  def scheduleOnce(delay: Int)(action: => Unit) = {
    schedule(delay) { it =>
      action
      it.cancel()
    }
  }

  /* Schedules a task to run a specific amount of times. */
  def scheduleTimes(delay: Int, times: Int)(action: => Unit) = {
    var loops = 0
    schedule(delay) { it =>
      if (loops == times) {
        it.cancel()
      } else {
        action
        loops += 1
      }
    }
  }

  /*
   Schedules a task to run until a certain condition is met.

   WARNING: If the condition is never met, this task will run forever! If abused, it will lead to unintended
   increases in memory and CPU usage. Use appropriately!
  */
  def scheduleUntil(delay: Int, predicate: Boolean)(action: => Unit) = {
    schedule(delay) { it =>
      if (!predicate) {
        action
      } else {
        it.cancel()
      }
    }
  }

  /*
   Schedules a task to run forever (as long as the server is online).

   WARNING: This task will run forever! If abused, it will lead to unintended increases in memory
   and CPU usage. Use appropriately!
  */
  def scheduleForever(delay: Int, instant: Boolean = false)(action: => Unit) = {
    schedule(delay, instant) { it =>
      action
    }
  }

  /* Sends a message to every single player online. */
  def messageToAll(str: String) = world.getPlayers.foreach(_.sendMessage(str))
}


/* Implicit class for 'Array' instances. */
implicit class RichArray[T](array: Array[T]) {

  /* Shuffle the elements of an array (Fisher-Yates algorithm). */
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

  /* Selects a random element from the array. */
  def randomElement = array((rand.nextDouble * array.length).toInt)
}


/* Implicit class for 'ItemContainer' instances. */
implicit class RichItemContainer(items: ItemContainer) {

  /*
   A 'bulkOperation' block, 'ItemContainer' events won't be fired for any code within it. Once it
   completes a bulk item event will be fired.
  */
  def bulkOperation(func: => Unit) {
    items.setFiringEvents(false)
    try {
      func
    } finally {
      items.setFiringEvents(true)
    }
    items.fireBulkItemsUpdatedEvent()
  }
}


/* Implicit class for 'IndexedSeq' instances. */
implicit class RichIndexedSeq[T](indexed: IndexedSeq[T]) {

  /* Shuffle the elements of an 'IndexedSeq' (Fisher-Yates algorithm). */
  def shuffle = Random.shuffle(indexed)

  /* Selects a random element from the 'IndexedSeq'. */
  def randomElement = indexed((rand.nextDouble * indexed.length).toInt)
}


/* Implicit class for 'Traversable' instances. */
implicit class RichTraversable[T](traversable: Traversable[T]) {

  /* Lazy filter function, to distinguish from the normal filter functions. */
  def lazyFilter(pred: T => Boolean) = traversable.withFilter(pred)
  def lazyFilterNot(pred: T => Boolean) = traversable.withFilter(it => !pred(it))
}

/* Implicit class for 'java.lang.Iterable' instances. */
implicit class RichJavaIterable[T](iterable: java.lang.Iterable[T]) {
}