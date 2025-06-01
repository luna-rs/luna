package world.player.skill.slayer

/**
 * A class representing data for a slayer task that can be assigned by a master.
 */
class SlayerTaskData(
    val task: SlayerTask,
    val amount: IntRange,
    val weight: Int
)