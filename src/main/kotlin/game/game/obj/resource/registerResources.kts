package game.obj.resource

import api.predef.*
import game.obj.resource.fillable.MilkResource
import game.obj.resource.fillable.SandResource
import game.obj.resource.fillable.WaterResource
import game.obj.resource.harvestable.CabbageResource
import game.obj.resource.harvestable.FlaxResource
import game.obj.resource.harvestable.OnionResource
import game.obj.resource.harvestable.PotatoResource
import game.obj.resource.harvestable.WheatResource
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * All resources that will be handled by the server.
 */
val RESOURCES = listOf(
    // Fillable resources.
    WaterResource,
    SandResource,
    MilkResource,

    // Harvestable resources.
    CabbageResource,
    OnionResource,
    PotatoResource,
    FlaxResource,
    WheatResource
)

// Load all resources.
on(ServerLaunchEvent::class) {
    RESOURCES.forEach { it.load() }
}

