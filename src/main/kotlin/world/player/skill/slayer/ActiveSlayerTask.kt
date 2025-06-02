package world.player.skill.slayer

import api.attr.typeAdapters.ActiveSlayerTaskTypeAdapter
import com.google.gson.annotations.JsonAdapter

/**
 * A class representing an active slayer task.
 */
@JsonAdapter(value = ActiveSlayerTaskTypeAdapter::class)
class ActiveSlayerTask(val task: SlayerTaskType, val assignee: SlayerMaster, var remaining: Int)