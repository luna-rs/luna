package world.player.skill.slayer

/**
 * A class representing data for a slayer task that can be assigned by a master.
 */
class SlayerTask(val type: SlayerTaskType, val amount: IntRange, val weight: Int)