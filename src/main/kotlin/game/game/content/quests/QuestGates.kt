package game.content.quests

import io.luna.Luna
import io.luna.game.model.mob.Player

/**
 * The one check behind every quest gate. While `quests.gated` is off in `luna.jsonc`, every gate is open.
 *
 * @author TheLining
 */
object QuestGates {

    /**
     * The items that need a quest to be worn or wielded, and the messages sent when it isn't finished.
     *
     * @author TheLining
     */
    private class EquipGate(val quest: Quest, val messages: List<String>)

    private val dragonSlayer = EquipGate(Quest.DRAGON_SLAYER, listOf("You have not earned the right to wear this yet.",
        "You need to complete the Dragon Slayer quest first."))
    private val lostCity = EquipGate(Quest.LOST_CITY, listOf("You have not earned the right to wear this yet.",
        "You need to complete the Lost City of Zanaris Quest."))
    private val heroesQuest = EquipGate(Quest.HEROES_QUEST, listOf("You have not earned the right to wield this yet.",
        "You need to complete the Heroes' Guild entry quest."))
    private val legendsQuest = EquipGate(Quest.LEGENDS_QUEST, listOf("You have not earned the right to wear this yet.",
        "You need to complete the Legends' Quest."))
    private val regicide = EquipGate(Quest.REGICIDE, listOf("You have not earned the right to wear this yet.",
        "You need to complete the Regicide quest."))
    private val fremennikTrials = EquipGate(Quest.FREMENNIK_TRIALS, listOf("Only Fremenniks are worthy of wearing this helm.",
        "You need to complete The Fremennik Trials first."))

    /**
     * Every rune platebody and the green d'hide body need Dragon Slayer, the dragon dagger and longsword Lost City,
     * the dragon mace and battleaxe Heroes' Quest, the dragon sq shield Legends' Quest, the dragon halberd Regicide,
     * and the Fremennik helms The Fremennik Trials.
     */
    private val equipment: Map<Int, EquipGate> =
        listOf(1127, 2615, 2623, 2653, 2661, 2669, 3481, 1135).associateWith { dragonSlayer } +
            listOf(1215, 1231, 5680, 5698, 1305).associateWith { lostCity } +
            listOf(1434, 1377).associateWith { heroesQuest } +
            mapOf(1187 to legendsQuest, 3204 to regicide) +
            listOf(3749, 3751, 3753, 3755).associateWith { fremennikTrials }

    /**
     * Whether quest gates are on.
     */
    @JvmStatic
    fun enabled(): Boolean = Luna.settings()?.quests()?.gated() == true

    /**
     * Whether [player] may pass a gate that needs [quest].
     */
    @JvmStatic
    fun passes(player: Player, quest: Quest): Boolean = !enabled() || quest.completed(player)

    /**
     * Whether [player] may wear or wield [itemId], telling them why not when [notify] is set.
     */
    @JvmStatic
    fun canEquip(player: Player, itemId: Int, notify: Boolean): Boolean {
        val gate = equipment[itemId] ?: return true
        if (passes(player, gate.quest)) return true
        if (notify) gate.messages.forEach(player::sendMessage)
        return false
    }
}
