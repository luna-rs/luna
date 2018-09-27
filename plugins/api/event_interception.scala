/*
 The interception of posted events can be handled through the 'on' functions. One to intercept events with no matching
 and another to intercept with matching (if the arguments given are in agreement with the 'matches' method in the Event
 class).

 Please note that normal functions and fields must come before event interception.
*/

import io.luna.game.event.{Event, EventArguments, EventListener}

import scala.reflect.ClassTag


/* Normal event interception function. No matching happens here. */
def on[E <: Event](eventListener: E => Unit)
                  (implicit tag: ClassTag[E]): Unit =
scriptListeners.add(new EventListener(tag.runtimeClass, EventArguments.NO_ARGS, eventListener))

/*
 Event interception function, with matching on parameters. This method requires that at least one argument
 is specified, in order to avoid conflicts with the previous "on" method.
*/
def onargs[E <: Event](arg1: Any, argOther: Any*)
                      (eventListener: E => Unit)
                      (implicit tag: ClassTag[E]): Unit = {

  val eventArgs = new EventArguments(
    (List(arg1) ++ List(argOther).flatten).map(_.asInstanceOf[AnyRef]).toArray)

  val eventClass = tag.runtimeClass
  scriptListeners.add(new EventListener(eventClass, eventArgs, eventListener))
}