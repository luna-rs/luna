package api.plugin.dsl

/**
 * The receiver for the [plugin] closure.
 *
 * @author lare96
 */
class PluginBuilderReceiver {

    /**
     * The name of the plugin.
     */
    var name: String? = null

    /**
     * This description of the plugin.
     */
    var description: String? = null

    /**
     * The version of Luna that this plugin is compatible with.
     */
    var version: String = "Unspecified"

    /**
     * The authors of this plugin.
      */
    val authors: MutableList<String> = ArrayList(2)
}