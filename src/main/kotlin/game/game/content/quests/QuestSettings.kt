package game.content.quests

/**
 * Configuration container for quest gates.
 *
 * @author TheLining
 */
class QuestSettings(private val gated: Boolean = false) {

    /**
     * When enabled, quest-gated content needs its quest finished, as in RuneScape.
     *
     * When disabled, quest-gated items, areas and NPC services are open to everyone. Quests that Luna does not have
     * yet never gate anything.
     */
    fun gated() = gated
}
