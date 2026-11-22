package game.content.spiritTrees

import api.predef.*
import game.obj.entrances.Entrances
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Sends players between the spirit trees.
 *
 * @author TheLining
 */
object SpiritTrees {

    /**
     * The old spirit tree in the Tree Gnome Stronghold.
     */
    const val STRONGHOLD_TREE = 1293

    /**
     * The old spirit tree in the Tree Gnome Village.
     */
    const val VILLAGE_TREE = 1294

    /**
     * The young spirit trees north of Varrock and on the Battlefield of Khazard.
     */
    const val YOUNG_TREE = 1317

    /**
     * The Biohazard plague sample, which doesn't survive the crossing.
     */
    private const val PLAGUE_SAMPLE = 418

    /**
     * The animation of a player placing their hands on the tree.
     */
    private const val TOUCH_TREE = 828

    /**
     * The old trees' option to see the list of destinations.
     */
    const val ASK_DESTINATIONS = "Where can I go?"
    /**
     * The young trees' option to accept their one offered trip.
     */
    const val ACCEPT_YOUNG_TREE = "Yes please."

    /**
     * The only destination the young trees know.
     */
    val youngDestination: SpiritTree
        get() = SpiritTree.TREE_GNOME_VILLAGE

    /**
     * Talks to an old spirit tree, which offers a trip to any of [destinations].
     */
    fun talkToOldTree(plr: Player, destinations: List<SpiritTree>) {
        plr.newDialogue()
            .text("The tree talks in an old tired voice...")
            .text("You friend of gnome people, you friend of mine.", "Would you like me to take you somewhere?")
            .options("No thanks, old tree.", { it.newDialogue().player("No thanks, old tree.").open() },
                     ASK_DESTINATIONS, { askWhere(it, destinations) })
            .open()
    }

    /**
     * Asks an old spirit tree where it can send [plr], then picks one of its three [destinations].
     */
    private fun askWhere(plr: Player, destinations: List<SpiritTree>) {
        val (first, second, third) = destinations
        plr.newDialogue()
            .player(Expression.QUIZZICAL, ASK_DESTINATIONS)
            .text("The tree talks again...")
            .text("You can travel to the trees", "which are related to me.")
            .options(first.option, { travel(it, first) },
                     second.option, { travel(it, second) },
                     third.option, { travel(it, third) })
            .open()
    }

    /**
     * Talks to a young spirit tree, which only knows the way to the Tree Gnome Village.
     */
    fun talkToYoungTree(plr: Player) {
        plr.newDialogue()
            .text("The young spirit tree talks...")
            .text("Hello gnome friend. Would you like to", "travel to the home of the tree gnomes?")
            .options(ACCEPT_YOUNG_TREE, {
                it.newDialogue()
                    .player(ACCEPT_YOUNG_TREE)
                    .then { player -> travel(player, youngDestination) }
                    .open()
            }, "No thank you.", { it.newDialogue().player("No thank you.").open() })
            .open()
    }

    /**
     * Sends [plr] through the tree they're talking with to [destination].
     */
    private fun travel(plr: Player, destination: SpiritTree) {
        plr.newDialogue()
            .text("You place your hands on the dry tough bark of the spirit tree,",
                  "and feel a surge of energy run through your veins.")
            .then {
                Entrances.travel(it, destination.landing, delay = 1, animation = TOUCH_TREE,
                                 onArrival = ::destroyPlagueSamples)
            }
            .open()
    }

    /**
     * Destroys any plague samples [plr] carried through a tree.
     */
    private fun destroyPlagueSamples(plr: Player) {
        val samples = plr.inventory.computeAmountForId(PLAGUE_SAMPLE)
        if (samples > 0 && plr.inventory.remove(Item(PLAGUE_SAMPLE, samples))) {
            plr.sendMessage("The plague sample is too delicate...it disintegrates in the crossing.")
        }
    }
}
