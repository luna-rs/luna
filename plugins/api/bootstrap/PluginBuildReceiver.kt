package api.bootstrap

import java.time.LocalDate

class PluginBuildReceiver {
    var name: String? = null
    var description: String = "A plugin created on ${LocalDate.now()}."
    var version: String = "Unspecified"
    var corePlugin: Boolean = false
    val authors: MutableList<String> = ArrayList(2)
}