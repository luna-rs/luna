package game.skill.agility

import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The intact rope bridge in the Hollows. The four planks are rendered on the ground plane but their
 * floor decorations block ordinary movement, so the tree interaction walks the deck under an agility lock.
 *
 * @author lare96
 */
object MortMyreBridge {
    const val TREE = 5005
    const val TICKS = 7

    /**
     * A tree's ground placement, the reachable bank tile, and the deck tile just inside it.
     *
     * @author lare96
     */
    enum class End(val tree: Position, val bank: Position, val deck: Position) {
        SOUTH(Position(3502, 3426), Position(3502, 3425), Position(3502, 3427)),
        NORTH(Position(3502, 3431), Position(3503, 3431), Position(3502, 3430));

        val opposite: End
            get() = if (this == SOUTH) NORTH else SOUTH
    }

    // Lost City's gnome tree climb and standard ladder descent use these sequences.
    private const val CLIMB_UP = 828
    private const val CLIMB_DOWN = 827

    /**
     * Lines the player up at the selected tree and takes them across to the opposite bank.
     */
    fun cross(player: Player, tree: GameObject) {
        val from = End.entries.firstOrNull { it.tree == tree.position } ?: return
        if (tree.id != TREE || !ready(player) || player.z != from.bank.z || onDeck(player.position) ||
            player.position.computeLongestDistance(from.bank) > AgilityApproach.RADIUS) return

        val begin = begin@ {
            if (!ready(player) || player.position != from.bank || tree.state != EntityState.ACTIVE ||
                tree.isHidden || !tree.isVisibleTo(player)) return@begin
            val to = from.opposite
            val controller = player.controllers.primary
            val steps = listOf(
                AgilityStep(from.deck, 1, animation = CLIMB_UP, jump = true, begin = { player.face(from.tree) }),
                // Send the climb's placement on the first plank before queuing walking updates.
                AgilityStep(from.deck, 1, jump = true),
                AgilityStep(to.deck, from.deck.computeLongestDistance(to.deck), walkingAnimation = AgilityWalk.NORMAL),
                descendStep(player, to))
            val action = AgilityAction(player, steps, null,
                valid = { canMove(player) && (player.position == from.bank || onDeck(player.position)) })
            action.onCancel = {
                if (player.state == EntityState.ACTIVE && player.isAlive &&
                    player.controllers.primary === controller && onDeck(player.position)) {
                    player.move(from.bank)
                }
            }
            player.submitAction(action)
        }
        if (player.position == from.bank) begin()
        else player.submitAction(AgilityApproach(player, from.bank, begin))
    }

    /**
     * Lets a player already on the deck climb back down at either end.
     */
    fun climbDown(player: Player, tree: GameObject) {
        val end = End.entries.firstOrNull { it.tree == tree.position } ?: return
        if (player.position != end.deck) {
            player.sendMessage("You must be on the bridge to climb down.")
            return
        }
        if (tree.id != TREE || !ready(player)) return
        player.submitAction(AgilityAction(player, listOf(descendStep(player, end)), null,
            valid = { canMove(player) }))
    }

    private fun descendStep(player: Player, end: End) =
        AgilityStep(end.bank, 2, animation = CLIMB_DOWN, animationTick = 1, jump = true,
            begin = { player.face(end.tree) })

    private fun onDeck(position: Position) = position.z == 0 && position.x == End.SOUTH.deck.x &&
        position.y in End.SOUTH.deck.y..End.NORTH.deck.y

    private fun canMove(player: Player) =
        player.state == EntityState.ACTIVE && player.isAlive && !player.status.isImmobilized()

    private fun ready(player: Player) = canMove(player) && !player.isLocked && !player.walking.isLocked
}
