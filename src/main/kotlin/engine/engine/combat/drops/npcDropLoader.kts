package engine.combat.drops

import api.drops.DropTableHandler
import api.drops.GenericDropTables
import api.predef.*
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.util.GsonUtils
import io.luna.util.Rational
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

/**
 * Loads all parsed NPC drop definitions into the drop-table DSL.
 *
 * Each [StaticNpcDropTable] is first grouped by its sub-table identifier so that every unique logical table can be
 * emitted as its own DSL `table { ... }` block.
 *
 * @param loadedDrops The parsed NPC drop definitions read from JSON.
 */
fun load(loadedDrops: Array<StaticNpcDropTable>) {
    for (table in loadedDrops) {
        val subTables = ArrayListMultimap.create<Int, StaticNpcDrop>()
        for (drop in table.drops) {
            // Map all sub-tables by identifier.
            subTables.put(drop.table, drop)
        }
        loadTable(table, subTables)
    }
    logger.debug("Loaded {} NPC drop tables!", loadedDrops.size)
}

/**
 * Builds and registers a single NPC drop table definition in the DSL.
 *
 * This applies any configured generic rare table first, then emits one DSL sub-table for every unique grouped table ID
 * found in [subTables]. Noted drops are emitted inside a dedicated `noted { ... }` block.
 *
 * @param table The top-level NPC drop definition being loaded.
 * @param subTables All drops grouped by their table identifier.
 */
fun loadTable(table: StaticNpcDropTable, subTables: ListMultimap<Int, StaticNpcDrop>) {
    DropTableHandler.createNpc(table.id) {
        // Load generic Ring of Wealth-based drop tables.
        when (table.rare) {
            "GEM" -> tables += GenericDropTables.gemDropTable()
            "MEGA_RARE" -> tables += GenericDropTables.megaRareDropTable()
            "RARE" -> tables += GenericDropTables.rareDropTable()
        }

        for (entry in subTables.asMap().entries) {
            // Create a new DSL table for each unique table ID.
            table {
                val noted = ArrayList<StaticNpcDrop>()

                for (drop in entry.value) {
                    if (drop.noted) {
                        noted += drop
                        continue
                    }
                    // Add regular, non-noted items to this sub-table.
                    drop.id.x(getAmountRange(drop.quantity)).chance(Rational.fromDouble(drop.rarity))
                }

                noted {
                    for (drop in noted) {
                        // Add noted items to the noted section of this sub-table.
                        drop.id.x(getAmountRange(drop.quantity)).chance(Rational.fromDouble(drop.rarity))
                    }
                }
            }
        }
    }
}

/**
 * Parses a JSON quantity string into an inclusive [IntRange].
 *
 * Supported formats are either a single integer such as `"5"` or a hyphenated range such as `"1-3"`.
 *
 * @param amount The serialized quantity string.
 * @return The parsed inclusive quantity range.
 * @throws NumberFormatException If the quantity format is invalid.
 */
fun getAmountRange(amount: String): IntRange {
    return if (amount.contains("-")) {
        /**
         * Convert a hyphenated quantity range to an [IntRange].
         */
        val split = amount.split("-").map { it.toInt() }
        split[0]..split[1]
    } else {
        /**
         * Convert a single fixed quantity to a one-value [IntRange].
         */
        val intAmount = amount.toInt()
        intAmount..intAmount
    }
}

/*
   Loads all NPC drop tables from the configured JSON file during server launch.

   The JSON is read asynchronously on the task pool, then the parsed data is applied on the game executor so that
   registration occurs on the game thread.
 */
on(ServerLaunchEvent::class) {
    val dropsPath = Paths.get("data", "game", "def", "npcs", "drops.json")
    CompletableFuture
        .supplyAsync({ GsonUtils.readAsType(dropsPath, Array<StaticNpcDropTable>::class.java) }, taskPool)
        .thenAcceptAsync({ load(it) }, gameService.gameExecutor)
        .exceptionally { logger.error("Error while parsing NPC drops.", it); null }
}