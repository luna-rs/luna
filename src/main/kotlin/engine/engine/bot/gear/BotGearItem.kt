package engine.bot.gear

class BotGearItem(val index: Int, val id: Int, val purposes: Set<BotGearPurpose>, val priority: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BotGearItem) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id
    }
}