package game.content.fishingTrawler

import com.google.common.collect.ImmutableList
import io.luna.LunaContext
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.map.MapIndex
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks the port's object identifiers and hull coordinates against the installed revision 377 cache.
 *
 * @author lare96
 */
class TrawlerMapTest {
    @Test fun cacheContainsAllHullNetLadderAndEscapeInteractions() {
        assumeTrue(Files.exists(Path.of("data/game/cache/main_file_cache.dat")), "Local revision 377 cache required")
        Cache().use { cache ->
            cache.open()
            val decoder = MapDecoder()
            val indices = ImmutableList.builder<MapIndex>()
            decoder.decode(cache, indices)
            val regions = listOf(Position(1889, 4825), Position(1953, 4825), Position(2017, 4825),
                FishingTrawler.DOCK).map { it.region }.toSet()
            decoder.handle(mock(LunaContext::class.java), cache,
                ImmutableList.copyOf(indices.build().filter { it.region in regions }))
            val objects = cache.mapIndexTable.objectSet.objects
            for (base in listOf(1856, 1984)) {
                for (x in 29..36) for (y in listOf(4823, 4826)) {
                    assertTrue(objects.any { it.position == Position(base + x, y) && it.objectId == 2177 },
                        "Missing hull at ${base + x},$y")
                }
                for (id in listOf(2164, 2165, 2174, 2175)) {
                    assertTrue(objects.any { it.objectId == id && it.position.x in base..base + 63 }, "Missing boat object $id")
                }
            }
            for (id in listOf(2159, 2160, 2166, 2178, 2179)) {
                assertTrue(objects.any { it.objectId == id }, "Missing shore or wreck object $id")
            }
        }
    }
}
