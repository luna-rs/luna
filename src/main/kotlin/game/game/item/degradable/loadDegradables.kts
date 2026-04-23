package game.item.degradable

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.util.GsonUtils
import java.nio.file.Paths

/**
 * The filesystem path to the degradable equipment file.
 *
 * This file is loaded during server startup and contains the degradable item chains used by [DegradableEquipmentHandler].
 */
val PATH = Paths.get("data", "game", "def", "equipment", "degradable.jsonc")

// Loads degradable equipment when the server finishes launching.
on(ServerLaunchEvent::class) {
    taskPool.execute {
        val all = GsonUtils.readAsType(PATH, Array<DegradableItemSet>::class.java)
        DegradableEquipmentHandler.load(all)
    }
}