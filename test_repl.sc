import io.luna.game.event.impl.LoginEvent

import scala.reflect.ClassTag

/* Normal event interception function. No matching happens here. */
def on[E](eventListener: E => Unit)
  (implicit tag: ClassTag[E]): Unit = println(tag.runtimeClass)

/*
 Event interception function, with matching on parameters. This method requires that at least one argument
 is specified, in order to avoid conflicts with the previous "on" method.
*/
def on[E](arg1: Any, argOther: Any*)
  (eventListener: E => Unit)
  (implicit tag: ClassTag[E]): Unit = {

  println(tag.runtimeClass + " onargs")
}
def dosomething = {}
on[LoginEvent] { msg => dosomething }
on[LoginEvent](222, 222) { msg => dosomething }