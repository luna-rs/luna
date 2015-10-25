package plugin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.collection.mutable.MutableList
import org.apache.logging.log4j.Logger
import io.luna.LunaContext
import io.luna.game.model.mobile.Player
import io.luna.game.plugin.PluginManager
import org.apache.logging.log4j.LogManager
import java.nio.file.SimpleFileVisitor
import com.google.common.eventbus.Subscribe
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileVisitResult
import java.io.File
import org.apache.logging.log4j.Level
import io.luna.game.plugin.Plugin
import io.luna.game.plugin.PluginPipeline
import io.luna.game.task.Task

/**
 * Bootstraps the plugin system by instantiating all compiled plugins
 * and submitting them to the plugin manager. It also acts as a
 * container for all plugin event case classes, which should be
 * placed at the <strong>bottom of this file</strong> for consistency.
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

      val c = Class.forName(format(path.toString))
      if (c.getSuperclass == Plugin.getClass) {
        plugins.+=(c)
      }
      FileVisitResult.CONTINUE
    }

    private def format(path: String): String = {
      path.replace(File.separator, ".").substring(0, path.lastIndexOf(".")).replace("bin.", "").replace("..", "")
    }
  }
}

// Plugin events should go here, case classes should also be used for virtually
// all plugins unless more logic than just arguments are needed.

case class LoginEvent(player: Player)
case class LogoutEvent(player: Player)
