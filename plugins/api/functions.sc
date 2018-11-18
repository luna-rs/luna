/*
 A collection of implicit conversions and miscellaneous functions available to every plugin. Take caution when
 writing new implicit conversions, as they can end up producing negative side effects if they aren't carefully
 written.
*/
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}
import java.util.function._
import java.util.{Optional, OptionalInt}

import com.google.common.collect.BoundType
import io.luna.game.model.`def`.ItemDefinition
import io.luna.util.StringUtils


/* Implicit conversions. */
implicit def javaToScalaRange(range: com.google.common.collect.Range[java.lang.Integer]): Range = {
  var lower = range.lowerEndpoint.toInt
  var upper = range.upperEndpoint.toInt

  if (range.lowerBoundType == BoundType.OPEN) {
    lower += 1
  }
  if (range.upperBoundType == BoundType.OPEN) {
    upper -= 1
  }

  lower to upper
}

implicit def scalaToJavaRange(range: Range): com.google.common.collect.Range[java.lang.Integer] = {
  if (range.isInclusive) {
    com.google.common.collect.Range.closed(range.start, range.end)
  } else {
    com.google.common.collect.Range.closedOpen(range.start, range.end)
  }
}

implicit def javaToScalaOptional[T](optional: Optional[T]): Option[T] = {
  if (optional.isPresent) {
    Some(optional.get)
  } else {
    None
  }
}
implicit def javaToScalaOptionalInt(optional: OptionalInt): Option[Int] = {
  if (optional.isPresent) {
    Some(optional.getAsInt)
  } else {
    None
  }
}

implicit def scalaToJavaConsumer[T](func: T => Unit): Consumer[T] = new Consumer[T] {
  override def accept(t: T) = func(t)
}

implicit def scalaToJavaBiConsumer[T, U](func: (T, U) => Unit): BiConsumer[T, U] = new BiConsumer[T, U] {
  override def accept(t: T, u: U) = func(t, u)
}

implicit def scalaToJavaFunction[T, R](func: T => R): Function[T, R] = new Function[T, R] {
  override def apply(t: T) = func(t)
}

implicit def scalaToJavaBiFunction[T, U, R](func: (T, U) => R): BiFunction[T, U, R] = new BiFunction[T, U, R] {
  override def apply(t: T, u: U) = func(t, u)
}

implicit def scalaToJavaPredicate[T](func: T => Boolean): Predicate[T] = new Predicate[T] {
  override def test(t: T) = func(t)
}

implicit def scalaToJavaBiPredicate[T, U](func: (T, U) => Boolean): BiPredicate[T, U] = new BiPredicate[T, U] {
  override def test(t: T, u: U) = func(t, u)
}

implicit def scalaToJavaSupplier[T](func: () => T): Supplier[T] = new Supplier[T] {
  override def get = func()
}


/* Random generation functions. */
def rand = ThreadLocalRandom.current
def pick[E](seq: Seq[E]): E = seq((rand.nextDouble * seq.length).toInt)
def rand(from: Int, to: Int): Int = rand.nextInt((to - from) + 1) + from
def rand(to: Int): Int = rand.nextInt(to + 1)


/* Retrieves the system time in 'MILLISECONDS'. */
def currentTimeMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime, TimeUnit.NANOSECONDS)


/* Retrieves the name of an item. */
def nameOfItem(id: Int) = ItemDefinition.ALL.retrieve(id).getName


/* Appends an article between a prefix and suffix. */
def joinArticle(prefix: String, thing: String, suffix: String) = {
  val sb = new StringBuilder
  val article = StringUtils.computeArticle(thing)
  val space = ' '

  sb ++= prefix += space ++= article += space ++= thing += space ++= suffix

  sb.toString
}

/* Asynchronous block functions. */
def async(func: => Unit) = service.submit(new Runnable {
  override def run() = {
    try {
      func
    } catch {
      case e: Exception => e.printStackTrace()
    }
  }
})


/* Logging and printing functions. */
def log(msg: Any) = logger.info(s"$msg")
def logIf(cond: Boolean, msg: => Any) = if (cond) { log(msg) }


/* Failure and assertion functions. */
def fail(msg: Any = "[failure]: no reason specified") = throw new RuntimeException(s"$msg")
def failIf(cond: Boolean, msg: => Any = "[failure]: no reason specified") = if (cond) { fail(msg) }