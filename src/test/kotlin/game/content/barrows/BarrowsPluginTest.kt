package game.content.barrows

import com.google.common.collect.ArrayListMultimap
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.luna.LunaContext
import io.luna.game.plugin.PluginBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Checks the compiled scripts through the same discovery and validation stages used at server startup.
 *
 * @author lare96
 */
class BarrowsPluginTest {
    @Test
    fun discoversMetadataForAllSixGameplayScripts() {
        val packageName = "game.content.barrows"
        ClassGraph().enableClassInfo().acceptPackages(packageName).scan().use { classpath ->
            val context = mock(LunaContext::class.java, RETURNS_DEEP_STUBS)
            `when`(context.server.classpath).thenReturn(classpath)
            val bindingsField = PluginBootstrap::class.java.getDeclaredField("bindings").apply {
                isAccessible = true
            }
            val previousBindings = bindingsField.get(null)
            try {
                val bootstrap = PluginBootstrap(context)
                val metadata = HashMap<String, ClassInfo>()
                val scripts = ArrayListMultimap.create<String, ClassInfo>()
                PluginBootstrap::class.java.getDeclaredMethod("loadScripts", Map::class.java,
                    ArrayListMultimap::class.java).apply {
                    isAccessible = true
                    invoke(bootstrap, metadata, scripts)
                }
                assertEquals(setOf(packageName), metadata.keys)
                assertEquals("Info_plugin", metadata.getValue(packageName).simpleName)
                PluginBootstrap::class.java.getDeclaredMethod("validatePlugins", Map::class.java,
                    ArrayListMultimap::class.java).apply {
                    isAccessible = true
                    invoke(bootstrap, metadata, scripts)
                }
                assertEquals(setOf(packageName), scripts.keySet())
                assertEquals(setOf("BarrowsCrypts", "BarrowsDoorHandler", "BarrowsKills", "BarrowsOldMan",
                    "BarrowsPuzzleHandler", "BarrowsTreasure"), scripts[packageName].map { it.simpleName }.toSet())
                assertEquals(6, scripts.size())
            } finally {
                bindingsField.set(null, previousBindings)
            }
        }
    }
}
