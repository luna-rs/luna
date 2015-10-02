package plugin

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.LinkedList
import com.google.common.eventbus.Subscribe
import scala.collection.mutable.MutableList

final class PluginFileVisitor(plugins: MutableList[Class[_]]) extends SimpleFileVisitor[Path] {

  override def visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult = {
    val name = path.getFileName.toString
    val reject = List[String]("$", "PluginBootstrap", "PluginFileVisitor")

    if (!name.endsWith(".class") || reject.exists { x => name.contains(x) }) {
      return FileVisitResult.CONTINUE
    }

    val formatted = format(path.toString.replace("target.classes", ""))
    if (formatted.contains("plugin.events")) {
      return FileVisitResult.CONTINUE
    }

    val c = Class.forName(formatted)
    if (c.getAnnotation(classOf[Subscribe]) != null) {
      plugins.+=(c)
    }
    FileVisitResult.CONTINUE
  }

  private def format(path: String): String = {
    path.replace(File.separator, ".").substring(0, path.lastIndexOf(".")).replace("bin.", "").replace("..", "")
  }
}