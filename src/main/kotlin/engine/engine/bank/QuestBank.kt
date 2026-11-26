package engine.bank

import game.content.quests.Quest
import game.content.quests.QuestGates
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * Banks whose native object options are unlocked by the same quest gate as their login varbits.
 *
 * @author lare96
 */
enum class QuestBank(val objectId: Int, val anchor: Position, val quest: Quest, val option: Int) {
    CULINAROMANCERS_CHEST(12308, Position(3219, 9623), Quest.RECIPE_FOR_DISASTER, 1),
    BURGH_DE_ROTT(12759, Position(3494, 3211), Quest.IN_AID_OF_THE_MYREQUE, 2);

    /**
     * The webwalker capability flag for this bank.
     */
    val flag = "bank_${name.lowercase()}"

    /**
     * Whether [player] can use the bank.
     */
    fun passes(player: Player): Boolean = QuestGates.passes(player, quest)

    companion object {
        fun forObject(id: Int): QuestBank? = entries.firstOrNull { it.objectId == id }

        fun at(anchor: Position): QuestBank? = entries.firstOrNull { it.anchor == anchor }
    }
}
