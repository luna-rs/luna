package game.npc.spawn

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Behaviour shared by the general stores.
 *
 * Stock, dialogue, and which NPCs run each store come from the 377 cache era. Most general stores are run by a shop
 * keeper and an assistant who both greet players with [greet].
 */
object GeneralStore {

    /**
     * Where the keeper of Draynor's "General Store" is spawned. That store isn't from the 377 era, and its keeper
     * shares the Lumbridge shop keeper's NPC id, so the two stores tell their keepers apart by where they were spawned.
     */
    val DRAYNOR_KEEPER_SPAWN = Position(3091, 3250)

    /**
     * Determines if [npc] is the keeper of Draynor's "General Store".
     */
    fun isDraynorKeeper(npc: Npc) = npc.basePosition == DRAYNOR_KEEPER_SPAWN

    /**
     * Opens the shop named [shop] for [plr].
     */
    fun open(plr: Player, shop: String) {
        plr.overlays.open(ShopInterface(world, shop))
    }

    /**
     * The greeting shared by most general store keepers and their assistants. Most of them greet players with a
     * smile, but a few (Port Khazard and the Dwarven Mine) keep a straight face, so [mood] picks between the two.
     *
     * @param plr The player talking to the keeper.
     * @param npcId The keeper's NPC id.
     * @param shop The name of the keeper's shop.
     * @param mood The keeper's expression.
     */
    fun greet(plr: Player, npcId: Int, shop: String, mood: Expression = Expression.HAPPY) {
        plr.newDialogue()
            .npc(npcId, mood, "Can I help you at all?")
            .options("Yes please. What are you selling?", {
                it.newDialogue()
                    .player(Expression.QUIZZICAL, "Yes please. What are you selling?")
                    .npc(npcId, mood, "Take a look.")
                    .then { open(it, shop) }
                    .open()
            }, "No thanks.", {
                it.newDialogue().player("No thanks.").open()
            })
            .open()
    }
}
