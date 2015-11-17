package plugin

import java.io.File
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, Paths, SimpleFileVisitor}

import io.luna.game.model.mobile.attr.AttributeKey
import io.luna.game.plugin.PluginManager
import org.apache.logging.log4j.{Level, LogManager}

import scala.collection.mutable.MutableList

class LoginEvent

class LogoutEvent

case class PickupItemEvent(x: Int, y: Int, id: Int)

/** Provides the AttributeMap with all of the aliased attribute keys. This is
  * done eagerly within the PluginBootstrap rather
  * than lazily elsewhere to maintain the highest possible performance.
  *
  * @author lare96 <http://github.org/lare96>
  */
object AttributeKeyProvider {
  val logger = LogManager.getLogger(classOf[PluginBootstrap])

  AttributeKey.forPersistent("run_energy", 100)
  AttributeKey.forPersistent("first_login", true)

  logger.info("Attribute keys have been aliased.")
}

/** Bootstraps the plugin system by instantiating all compiled plugins
  * and submitting them to the plugin manager. It also acts as a
  * container for all plugin event classes, which should be
  * placed at the <strong>top of this file</strong> for consistency.
  *
  * @author lare96 <http://github.org/lare96>
  */
final class PluginBootstrap(pluginManager: PluginManager) extends Runnable {

  /** Bootstrap the plugin system, done asynchronously on startup through the
    * Server class. If any exceptions are caught, they are logged and the
    * bootstrapping process is aborted.
    */
  override def run() = {
    val logger = LogManager.getLogger(getClass)

    try {
      AttributeKeyProvider

      val path = Paths.get("./target/classes/plugin/")
      val plugins = MutableList[Class[_]]()

      Files.walkFileTree(path, new PluginFileVisitor(plugins))

      plugins.foreach { it => pluginManager.submit(it) }

      logger.info("Scala plugins have been initialized.")
    } catch {
      case e: Exception => {
        logger.catching(Level.FATAL, e)
        System.exit(0)
      }
    }
  }
}

/** Visit all compiled plugin classes and add the class instances to the
  * mutable list. Only classes that have Plugin as its superclass will be
  * acknowledged.
  *
  * @author lare96 <http://github.org/lare96>
  */
private class PluginFileVisitor(plugins: MutableList[Class[_]]) extends SimpleFileVisitor[Path] {

  override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
    val name = path.getFileName.toString

    if (!name.endsWith(".class")) {
      return FileVisitResult.CONTINUE
    }

    val c = Class.forName(format(path.toString).replace("target.classes.", ""))
    if (c.getSuperclass == classOf[Plugin[_]] && c != classOf[Plugin[_]]) {
      plugins.+=(c)
    }
    FileVisitResult.CONTINUE
  }

  private def format(path: String) = {
    path.replace(File.separator, ".").substring(0, path.lastIndexOf(".")).replace("bin.", "").replace("..", "")
  }
}