package plugin

import java.io.File

import com.google.common.io.Files
import io.luna.game.plugin.PluginManager
import org.apache.logging.log4j.{Level, LogManager}

import scala.collection.JavaConversions._


/** Bootstraps the plugin system visiting all compiled plugins and then subsequently instantiating and submitting them to
  * the `PluginManager`, where events from `PluginEvents` can be posted to them.
  *
  * @param pluginManager The `PluginManager` instance that the compiled plugins will be added to.
  * @author lare96 <http://github.org/lare96>
  */
final class PluginBootstrap(pluginManager: PluginManager) extends Runnable {

  override def run() = {
    val logger = LogManager.getLogger(getClass)
    val classFileDir = s"target${File.separator}classes${File.separator}"

    def fileToClass(file: File) = Class.forName(
      file.getPath.replace(classFileDir, "").replace(File.separator, ".").replace(".class", ""))

    try {
      val pluginDirFiles = Files.fileTreeTraverser.preOrderTraversal(
        new File(classFileDir + "plugin")).toList
      val classFiles = for (it <- pluginDirFiles if it.getName.endsWith(".class")) yield fileToClass(it)

      classFiles.filter(_.getSuperclass == classOf[Plugin[_]]).foreach(pluginManager.submit)

      logger.info("Scala plugins have been initialized.")
    } catch {
      case e: Exception =>
        logger.catching(Level.FATAL, e)
        sys.exit
    }
  }
}