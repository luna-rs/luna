package world.minigame.partyRoom

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerShutdownEvent
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.attr.Attribute
import world.minigame.partyRoom.dropParty.DropPartyOption
import java.nio.file.Files
import java.nio.file.Paths

/**
 * The file items will be saved to.
 */
val ITEMS_FILE = Paths.get("data", "game", "minigames", "party_room", "items.json")

/**
 * Loads items from the file if available.
 */
fun loadItems() {
    if (Files.exists(ITEMS_FILE)) {
        try {
            val loadedItems = Attribute.getGsonInstance()
                .fromJson(Files.readString(ITEMS_FILE), ItemContainer::class.java)
            DropPartyOption.chest.items.addAll(loadedItems)
            logger.info("Recovered ${loadedItems.size()} previously stored drop party items")
            Files.deleteIfExists(ITEMS_FILE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Saves items on graceful shutdown if available.
 */
fun saveItems() {
    val chestItems = DropPartyOption.chest.items
    if (chestItems.size() > 0) {
        Files.createFile(ITEMS_FILE)
        Files.writeString(ITEMS_FILE,
                          Attribute.getGsonInstance().toJson(chestItems))
    }
}

// Create directories if needed.
val ITEMS_FILE_DIR = ITEMS_FILE.parent
if (Files.notExists(ITEMS_FILE_DIR)) {
    Files.createDirectories(ITEMS_FILE_DIR)
}

// Asynchronously do the work on startup and shutdown.
on(ServerLaunchEvent::class) {
    taskPool.execute { loadItems() }
}

on(ServerShutdownEvent::class) {
    taskPool.execute { saveItems() }
}

