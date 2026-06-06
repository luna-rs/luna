package api.bot.skill

/**
 * Represents a tool that can be used by a bot for a skilling activity.
 *
 * Tools are ordered by their required skill level and then by item id. This allows bot scripts to store tools in a
 * sorted set and select tools consistently based on progression.
 *
 * @property id The item id of this skilling tool.
 * @property skillLevel The skill level required to use this tool.
 * @author lare96
 */
class SkillingTool(val id: Int, val skillLevel: Int) : Comparable<SkillingTool> {

    companion object {

        /**
         * Orders skilling tools by required skill level, then by item id.
         */
        private val COMPARATOR = compareBy<SkillingTool> { it.skillLevel }.thenBy { it.id }.reversed()
    }

    override fun compareTo(other: SkillingTool): Int = COMPARATOR.compare(this, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkillingTool) return false
        if (id != other.id) return false
        if (skillLevel != other.skillLevel) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + skillLevel
        return result
    }
}