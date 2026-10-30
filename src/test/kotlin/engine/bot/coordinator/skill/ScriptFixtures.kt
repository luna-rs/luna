package engine.bot.coordinator.skill

import com.google.gson.JsonParser
import io.luna.LunaContext
import io.luna.game.model.def.ItemDefinition
import io.luna.game.plugin.PluginBootstrap
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.OptionalInt

/**
 * Initializes item definitions and script bindings for core gameplay tests.
 *
 * @author lare96
 */
object ScriptFixtures {
    fun initialize() {
        if (ItemDefinition.ALL.get(0).isPresent) return
        // Factory construction initializes drop tables through the Kotlin script bindings.
        // Use a mock context without starting a server, and fixture names for drop item lookup.
        PluginBootstrap::class.java.getDeclaredMethod("setBindings", LunaContext::class.java).apply {
            isAccessible = true
            invoke(null, mock(LunaContext::class.java, RETURNS_DEEP_STUBS))
        }
        Files.newBufferedReader(Path.of("data/dumps/items.json")).use { reader ->
            for (entry in JsonParser.parseReader(reader).asJsonArray) {
                val item = entry.asJsonObject
                if (item.getAsJsonObject("unnoted_id").get("is_present").asBoolean) continue
                ItemDefinition.ALL.storeDefinition(ItemDefinition(
                    item.get("id").asInt, item.get("name").asString, "", 0, 0, 0, 0, 0, 0, 0,
                    item.get("stackable").asBoolean, 0, false, emptyArray(), emptyArray(), OptionalInt.empty(),
                    0, 0, 0, OptionalInt.empty(), 0.0, item.get("tradeable").asBoolean
                ))
            }
        }
    }
}
