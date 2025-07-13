package world.obj.resource

import world.obj.resource.fillable.MilkResource
import world.obj.resource.fillable.SandResource
import world.obj.resource.fillable.WaterResource
import world.obj.resource.harvestable.*

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
RESOURCES.forEach { it.load() }
