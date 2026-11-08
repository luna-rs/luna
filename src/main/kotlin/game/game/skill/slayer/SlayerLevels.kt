package game.skill.slayer

import api.predef.*
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * The Slayer level a player needs to damage each Slayer monster, taken from [SlayerTaskType].
 *
 * @author TheLining
 */
object SlayerLevels {

    /**
     * The assignment with the highest Slayer level for each NPC id that needs one.
     */
    private val types: Map<Int, SlayerTaskType> = SlayerTaskType.VALUES
        .filter { it.level > 1 }
        .flatMap { type -> type.npcs.map { it to type } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, types) -> types.maxBy { it.level } }

    /**
     * Returns the message for [player] if their Slayer level is too low to damage [npc], otherwise `null`.
     */
    fun failureMessage(player: Player, npc: Npc): String? {
        val type = types[npc.id] ?: return null
        if (player.slayer.level >= type.level) return null
        return "You need a Slayer level of ${type.level} to fight ${type.plural}."
    }
}
