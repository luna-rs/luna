/*
 All implicit (monkey patching) classes below are 'extending' Java classes by creating new functions for them. We
 do this to ensure that all code coming from Java is as concise and idiomatic (Scala-like) as possible.
*/

import io.luna.game.action.Action
import io.luna.game.model.{Entity, EntityType, Position, World}
import io.luna.game.model.item.{Item, ItemContainer}
import io.luna.game.model.mob.attr.AttributeValue
import io.luna.game.model.mob.{Mob, Npc, Player, PlayerRights}
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag
import io.luna.game.task.Task
import io.luna.net.msg.out._
import io.luna.util.Rational

import scala.collection.JavaConverters._
import scala.collection.generic.FilterMonadic
import scala.util.Random


implicit class RichPlayer(plr: Player) {

  def address = plr.getSession.getHostAddress

  def name = plr.getUsername

  def rights = plr.getRights

  def bank = plr.getBank

  def inventory = plr.getInventory

  def equipment = plr.getEquipment

  def sendMessage(message: String) = plr.queue(new GameChatboxMessageWriter(message))

  def sendWidgetText(text: String, widget: Int) = plr.queue(new WidgetTextMessageWriter(text, widget))

  def sendForceTab(id: Int) = plr.queue(new ForceTabMessageWriter(id))

  def sendChatboxInterface(id: Int) = plr.queue(new DialogueInterfaceMessageWriter(id))

  def sendSkillUpdate(id: Int) = plr.queue(new SkillUpdateMessageWriter(id))

  def sendMusic(id: Int) = plr.queue(new MusicMessageWriter(id))

  def sendSound(id: Int, loops: Int = 0, delay: Int = 0) = plr.queue(new SoundMessageWriter(id, loops, delay))

  def sendInterface(id: Int) = plr.queue(new InterfaceMessageWriter(id))

  def sendConfig(id: Int, value: Int) = plr.queue(new ConfigMessageWriter(id, value))

  def sendColor(id: Int, color: Int) = plr.queue(new ColorChangeMessageWriter(id, color))

  def sendItemModel(id: Int, scale: Int, item: Int) = plr.queue(new WidgetItemModelMessageWriter(id, scale, item))

  def sendTabInterface(tab: Int, interface: Int) = plr.queue(new TabInterfaceMessageWriter(tab, interface))

  def flag(updateFlag: UpdateFlag) = plr.getUpdateFlags.flag(updateFlag)

  def stopWalking() = plr.getWalkingQueue.clear()

  def lockMovement() = {
    stopWalking()
    plr.getWalkingQueue.setLocked(true)
  }

  def unlockMovement() = plr.getWalkingQueue.setLocked(false)
}

implicit class RichMobileEntity(mob: Mob) {

  def attr[T](key: String): T = {
    val attr: AttributeValue[T] = mob.getAttributes.get(key)
    attr.get
  }

  def attr[T](key: String, value: T) = {
    val attr: AttributeValue[T] = mob.getAttributes.get(key)
    attr.set(value)
  }

  def attr[T](key: String, value: T => T) = {
    val attr: AttributeValue[T] = mob.getAttributes.get(key)
    val applyValue: T = value.apply(attr.get())
    attr.set(applyValue)
  }

  def attrEquals(key: String, equals: Any) = equals == attr(key)

  def elapsedTime(key: String, ms: Long) = {
    val value: Long = attr(key)
    (currentTimeMillis - value) >= ms
  }

  def resetTime(key: String) = attr(key, currentTimeMillis)

  def walking = mob.getWalkingQueue

  def skills = mob.getSkills
}


implicit class RichPlayerRights(rights: PlayerRights) {

  def <=(other: PlayerRights) = rights.equalOrLess(other)

  def >=(other: PlayerRights) = rights.equalOrGreater(other)

  def >(other: PlayerRights) = rights.greater(other)

  def <(other: PlayerRights) = rights.less(other)

  def ==(other: PlayerRights) = rights.equal(other)
}


implicit class RichEntity(entity: Entity) {

  def position = entity.getPosition

  def x = position.getX

  def y = position.getY

  def z = position.getZ
}


implicit class RichWorld(world: World) {

  def add[E <: Mob](mob: E) = {
    if (mob.getType == TYPE_PLAYER) {
      val plr = mob.asInstanceOf[Player]
      world.getPlayers.add(plr)
      plr
    } else if (mob.getType == TYPE_NPC) {
      val npc = mob.asInstanceOf[Npc]
      world.getNpcs.add(npc)
      npc
    } else {
      throw new IllegalArgumentException
    }
  }

  def submit[E <: Mob](action: Action[E]) = {
    val mob = action.getMob
    if (mob == null) {
      fail("cannot submit Action with <null> mob")
    } else {
      mob.submitAction(action)
    }
  }

  def schedule(delay: Int, instant: Boolean = false)(action: Task => Unit) =
    world.schedule(new Task(instant, delay) {
      override protected def execute() = {
        action(this)
      }
    })

  def scheduleOnce(delay: Int)(action: => Unit) = {
    schedule(delay) { task =>
      action
      task.cancel()
    }
  }

  def scheduleTimes(delay: Int, times: Int)(action: => Unit) = {
    var loops = 0
    schedule(delay) { task =>
      if (loops == times) {
        task.cancel()
      } else {
        action
        loops += 1
      }
    }
  }

  def scheduleInterval(range: Range)(action: Task => Unit) = {
    schedule(pick(range)) { task =>
      action(task)
      task.setDelay(pick(range))
    }
  }

  def scheduleUntil(delay: Int, predicate: Boolean)(action: => Unit) = {
    schedule(delay) { task =>
      if (!predicate) {
        action
      } else {
        task.cancel()
      }
    }
  }

  def scheduleForever(delay: Int, instant: Boolean = false)(action: => Unit) = {
    schedule(delay, instant) { task =>
      action
    }
  }

  def players = iterableAsScalaIterable(world.getPlayers)

  def npcs = iterableAsScalaIterable(world.getNpcs)

  def messageToAll(str: String) = players.foreach(_.sendMessage(str))

  def getRegion(pos: Position) = world.getRegions.getRegion(pos.getRegionCoordinates)
}

implicit class RichPosition(position: Position) {

  def x = position.getX
  def y = position.getY
  def regionCoordinates = position.getRegionCoordinates
}

implicit class RichArray[T](array: Array[T]) {

  def shuffle = {
    var i = array.length - 1
    while (i > 0) {
      val index = rand(i)
      val a = array(index)
      array(index) = array(i)
      array(i) = a
      i -= 1
    }
    array
  }
}


implicit class RichItemContainer(items: ItemContainer) {

  def getIdForIndex(index: Int) = items.computeIdForIndex(index).orElse(-1)
  def addAll(traversable: Iterable[Item]) = items.addAll(asJavaIterable(traversable))

}


implicit class RichSeq[T](seq: Seq[T]) {

  def shuffle = Random.shuffle(seq)
}


implicit class RichTraversable[T](traversable: Traversable[T]) {

  def lazyFilter(pred: T => Boolean) = traversable.withFilter(pred)

  def lazyFilterNot(pred: T => Boolean) = traversable.withFilter(!pred(_))
}

implicit class RichIterable[T](traversable: java.lang.Iterable[T]) {

  def lazyFilter(pred: T => Boolean) =
    iterableAsScalaIterable(traversable).withFilter(pred)

  def lazyFilterNot(pred: T => Boolean) =
    iterableAsScalaIterable(traversable).withFilter(!pred(_))
}

implicit class RichFilterMonadic[A, R](filter: FilterMonadic[A, R]) {

  def lazyFilter(pred: A => Boolean) = filter.withFilter(pred)

  def lazyFilterNot(pred: A => Boolean) = filter.withFilter(it => !pred(it))
}

implicit class RichMap[K, V](map: Map[K, V]) {

  def tryKeys(keys: K*): Option[Option[V]] = {
    for (k <- keys) {
      val v = map.get(k)
      if (v.isDefined) {
        return Some(v)
      }
    }
    None
  }
}

implicit class RichInt(int: Int) {

  def times(action: => Unit) = {
    failIf(int < 0, "int value must be >= 0")

    var times = 0
    while (times < int) {
      times += 1
      action
    }
  }

  def of(denominator: Int) = new Rational(int, denominator)
}

/* Fixes file not being recognized as script in Scala IDE. Can be removed. */
@Deprecated
def $dummy$(nothing: Nothing) = ???