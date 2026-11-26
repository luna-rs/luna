package game.content.burghDeRott

import api.predef.*
import engine.bank.QuestBank.BURGH_DE_ROTT
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
on(LoginEvent::class) {
    if (BURGH_DE_ROTT.passes(plr)) {
        for ((varbit, value) in REPAIRED) {
            plr.sendVarbit(Varbit(varbit, value))
        }
    }
}
