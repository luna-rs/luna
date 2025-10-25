package game.skill.slayer

/**
 * A class representing data for a slayer task that can be assigned by a master.
 *
 * @author lare96
 */
class SlayerTask(val type: SlayerTaskType, val amount: IntRange, val weight: Int)