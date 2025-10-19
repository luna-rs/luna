package world.player.skill.crafting.textileCrafting

/**
 * Represents the source of the textile.
 *
 * @author lare96
 */
enum class TextileType(val objectIds: Set<Int>) {
    LOOM(objectIds = setOf(2644, 4309, 8748)),
    SPINNING_WHEEL(objectIds = setOf(787, 8717))
}