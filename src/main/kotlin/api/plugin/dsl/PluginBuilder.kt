package api.plugin.dsl

import api.predef.*
import com.google.common.collect.ImmutableList
import io.luna.game.plugin.InfoScriptData

/**
 * A function that builds metadata for a plugin. Every plugin should use this in a 'build.plugin.kts' file.
 */
fun plugin(func: PluginBuilderReceiver.() -> Unit) {
    val receiver = PluginBuilderReceiver()
    func(receiver)
    when {
        receiver.name == null -> throw IllegalStateException("Plugin builder closure must have 'name' field.")
        receiver.description == null -> throw IllegalStateException("Plugin builder closure must have 'description' field.")
        receiver.authors.isEmpty() -> receiver.authors += "Unspecified"
    }
    val pluginName = receiver.name
    val authorList = ImmutableList.copyOf(receiver.authors)
    val compareResult = buildScriptInfo.compareAndSet(null,
                                                      InfoScriptData(
                                                          receiver.name,
                                                          receiver.description,
                                                          receiver.version,
                                                          authorList
                                                      )
    )
    if(!compareResult) {
        throw IllegalStateException("Build script for plugin [$pluginName] already processing and was not cleared.")
    }
}