package plugin

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.mutable.MutableList
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import io.luna.game.model.mobile.Player
import io.luna.game.plugin.Plugin
import io.luna.game.plugin.PluginManager
import io.luna.game.plugin.PluginPipeline
import java.util.concurrent.ThreadLocalRandom
import io.luna.game.model.mobile.attr.AttributeKey

class LoginEvent
class LogoutEvent

/**
 * Provides the AttributeMap with all of the aliased attribute keys. This is
 * done eagerly within the PluginBootstrap rather
 * than lazily elsewhere to maintain the highest possible performance.
 */
object AttributeKeyProvider {
  lazy val logger = LogManager.getLogger(classOf[PluginBootstrap])

  AttributeKey.forPersistent("run_energy", 100)
  
  logger.info("Attribute keys have been aliased.")
}

/**
 * Bootstraps the plugin system by instantiating all compiled plugins
 * and submitting them to the plugin manager. It also acts as a
 * container for all plugin event classes, which should be
 * placed at the <strong>top of this file</strong> for consistency.
 */
final class PluginBootstrap(pluginManager: PluginManager) extends Runnable {

  /**
   * Bootstrap the plugin system, done asynchronously on startup through the
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

/**
 * Visit all compiled plugin classes and add the class instances to the
 * mutable list. Only classes that have Plugin as its superclass will be
 * acknowledged.
 */
private class PluginFileVisitor(plugins: MutableList[Class[_]]) extends SimpleFileVisitor[Path] {

  override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
    val name = path.getFileName.toString

    if (!name.endsWith(".class")) {
      return FileVisitResult.CONTINUE
    }

    val c = Class.forName(format(path.toString).replace("target.classes.", ""))
    if (c.getSuperclass == classOf[Plugin[_]]) {
      plugins.+=(c)
    }
    FileVisitResult.CONTINUE
  }

  private def format(path: String) = {
    path.replace(File.separator, ".").substring(0, path.lastIndexOf(".")).replace("bin.", "").replace("..", "")
  }
}