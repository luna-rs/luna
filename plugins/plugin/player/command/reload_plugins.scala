import io.luna.game.event.impl.CommandEvent
import io.luna.game.plugin.PluginBootstrap

>>@[CommandEvent]("reload_plugins") { (msg, plr) =>
  async { () =>
    log("Asynchronously reloading Scala plugins...")
    plugins.clear
    new PluginBootstrap(ctx).configure.files.dependencies.evaluate
    log("All Scala plugins have been reloaded!")
  }
}