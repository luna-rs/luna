package game.bot.scripts.combat

import engine.controllers.WildernessLocatableController.levelAt
import io.luna.game.model.Position

/**
 * Existing Wilderness patrol destinations, grouped for staffing and PvP reporting.
 *
 * @author lare96
 */
internal enum class PkPatrolArea(val share: Int) {
    EDGEVILLE(5),
    GREEN_DRAGONS(3),
    DEEP_WILDERNESS(2),
    OTHER(0);

    companion object {
        const val EDGEVILLE_REGION = 12343
        val staffed = entries.filter { it.share > 0 }
        val combatBrackets = listOf("3-49", "50-69", "70-89", "90+")

        fun combatBracket(level: Int): Int = when {
            level < 50 -> 0
            level < 70 -> 1
            level < 90 -> 2
            else -> 3
        }

        fun at(position: Position): PkPatrolArea = when {
            position.z != 0 || position.y > 6400 || levelAt(position) <= 0 -> OTHER
            position.regionId == EDGEVILLE_REGION -> EDGEVILLE
            levelAt(position) >= 30 -> DEEP_WILDERNESS
            position.regionId in PkBotScript.PkArea.GREEN_DRAGONS.regions -> GREEN_DRAGONS
            else -> OTHER
        }
    }
}
