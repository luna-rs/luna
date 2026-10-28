package game.content.randomEvents

import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * A player-owned random encounter. Selection and ownership belong to [RandomEvents]; individual
 * encounters own their interactions, warnings, rewards and cleanup. Skill hazards need not keep
 * this ownership slot after they have changed a tree, rock or fishing spot.
 *
 * @author lare96
 */
interface RandomEventEncounter {
    /**
     * Player and temporary NPC belonging to this particular encounter instance.
     */
    val player: Player
    val npc: Npc

    /**
     * Stable save identifier, retained only for encounters that follow their owner across logout.
     */
    val resumeKey: String

    /**
     * Whether this encounter still excludes another personal event.
     */
    val blocksNewEvents: Boolean

    /**
     * Advances event-specific behavior on the game thread; false ends the encounter.
     */
    fun tick(): Boolean

    /**
     * Handles native NPC interaction, including attempts by someone other than the owner.
     */
    fun talk(visitor: Player)

    /**
     * Removes encounter-owned world state. No rewards may be granted from cleanup.
     */
    fun close()
}
