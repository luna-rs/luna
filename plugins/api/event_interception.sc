/*
 The interception of posted events can be handled through the 'on' and builder functions. Please note that normal
 functions and fields must come before event interception.
*/

import io.luna.game.event.{Event, EventArguments, EventListener}

import scala.reflect.ClassTag


/* Base event interception method, calls first builder. */
def on[E <: Event](implicit tag: ClassTag[E]) = {
  tag.runtimeClass match {
    case eventClass: Class[E] => new EventInterceptionBuilder[E](eventClass)
    case _ => fail("Illegal class tag, must be an Event.")
  }
}

/* First builder. Determines if we're using a condition, arguments, or nothing to test the event. */
class EventInterceptionBuilder[E <: Event](private val eventClass: Class[E]) {

  /* Use a condition to test the event. */
  def condition(cond: E => Boolean) = new EventInterceptionBuilder2[E](eventClass, cond)

  /* Use arguments to test the event. */
  def args(argsVar: Any*) = new EventInterceptionBuilder2[E](eventClass, argsVar)

  /* No test, just run! */
  def run(action: E => Unit) = new EventInterceptionBuilder2[E](eventClass).run(action)
}

/* Second builder. Adds the event listener to the dynamic cache. */
class EventInterceptionBuilder2[E <: Event](private val eventClass: Class[E]) {

  private var cond: E => Boolean = _
  private var args: Seq[Any] = _

  def this(eventClass: Class[E], cond: E => Boolean) = {
    this(eventClass)
    this.cond = cond
  }

  def this(eventClass: Class[E], args: Seq[Any]) = {
    this(eventClass)
    require(args.nonEmpty, "Must have at least one argument!")
    this.args = args
  }

  def run(action: E => Unit): Unit = {
    if (cond != null) {
      runCondition(action)
    } else if (args != null) {
      runArgs(action)
    } else {
      scriptListeners.add(new EventListener[E](eventClass, EventArguments.NO_ARGS, action))
    }
  }

  private def runCondition(action: E => Unit): Unit = {
    scriptListeners.add(new EventListener[E](eventClass, EventArguments.NO_ARGS, msg => {
      if (cond(msg)) {
        action(msg)
        msg.terminate
      }
    }))
  }

  private def runArgs(action: E => Unit): Unit = {
    val eventArgs = new EventArguments(args.map(_.asInstanceOf[AnyRef]).toArray)
    scriptListeners.add(new EventListener[E](eventClass, eventArgs, action))
  }
}