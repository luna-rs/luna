package plugin

import java.util.LinkedList
import java.nio.file.Paths
import org.apache.logging.log4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import io.luna.Luna
import scala.collection.mutable.MutableList

final class PluginBootstrap(logger: Logger) extends Runnable {

  private final val PathToPlugins = "./target/classes/plugin/"

  override def run() = {
    val plugins = collect(Paths.get(PathToPlugins))
    plugins.foreach { x => Luna.getPlugins.register(x) }

    logger.info("Scala plugins have been initialized.")
  }

  private def collect(path: Path): MutableList[Class[_]] = {
    val plugins = MutableList[Class[_]]()
    Files.walkFileTree(path, new PluginFileVisitor(plugins))
    return plugins
  }
}