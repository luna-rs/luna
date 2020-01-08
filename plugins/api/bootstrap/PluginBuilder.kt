package api.bootstrap

/**
 * A function that builds metadata for a plugin. Every plugin should use this in a 'build.plugin.kts' file.
 */
fun plugin(func: PluginBuilderReceiver.() -> Unit) {
    val receiver = PluginBuilderReceiver()
    func(receiver)
    when {
        receiver.name == null ->  throw IllegalStateException("Plugin builder closure must have 'name' field.")
        receiver.description == null -> throw IllegalStateException("Plugin builder closure must have 'description' field.")
        receiver.authors.isEmpty() -> receiver.authors += "Unspecified"
    }
}