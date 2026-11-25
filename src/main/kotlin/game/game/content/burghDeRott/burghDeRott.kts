package game.content.burghDeRott

import api.predef.*
import game.content.quests.Quest
import game.content.quests.QuestGates
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.varp.Varbit

/**
 * The varbits In Aid of the Myreque sets as the town is rebuilt, each with its repaired value.
 */
val REPAIRED = mapOf(
    1974 to 1, // The roof.
    1975 to 1, // A wall.
    1977 to 1, // The bank booth.
    1978 to 1, // Another wall.
    1980 to 3, // The furnace, repaired and fuelled.
)

// The booth and the furnace work through the bank and furnace handlers, which include objects the client swaps.
// TODO In Aid of the Myreque: once the quest exists, the unrepaired booth's "Search" must not open the bank.
on(LoginEvent::class) {
    if (QuestGates.passes(plr, Quest.IN_AID_OF_THE_MYREQUE)) {
        for ((varbit, value) in REPAIRED) {
            plr.sendVarbit(Varbit(varbit, value))
        }
    }
}
