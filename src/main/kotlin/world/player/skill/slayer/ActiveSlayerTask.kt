package world.player.skill.slayer

/**
 * A class representing an active slayer task.
 */
class ActiveSlayerTask(val monster: SlayerTask, val assignedBy: SlayerMaster, var remaining: Int)