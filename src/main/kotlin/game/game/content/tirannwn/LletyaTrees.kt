package game.content.tirannwn

import api.predef.*
import api.predef.ext.*
import game.content.quests.Quest
import game.content.quests.QuestGates
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * The two trees across the west entrance of Lletya, which lift their branches to let a player walk through.
 *
 * @author TheLining
 */
object LletyaTrees {

    /**
     * The tree object id.
     */
    const val TREE = 8742

    /**
     * The trees lifting their branches.
     */
    private const val OPEN = 2315

    /**
     * The trees lowering their branches.
     */
    private const val CLOSE = 2313

    /**
     * The column the trees stand on, with the forest to the west and Lletya to the east.
     */
    private const val X = 2305

    /**
     * The rows a player can walk through on, between open ground on both sides.
     */
    private val ROWS = 3191..3196

    /**
     * Where the two trees stand.
     */
    private val TREES = listOf(Position(X, 3191), Position(X, 3195))

    /**
     * Walks [plr] through the trees to the other side, in their own row.
     */
    fun pass(plr: Player) {
        if (!QuestGates.passes(plr, Quest.MOURNINGS_END_PART_I)) {
            plr.sendMessage("The trees won't let you pass.")
            return
        }
        val dx = if (plr.position.x > X) -1 else 1
        val row = plr.position.y.coerceIn(ROWS)
        val start = Position(X - dx, row)
        val end = Position(X + dx, row)
        val enter = enter@{
            if (plr.isLocked || plr.position != start) return@enter
            val trees = TREES.mapNotNull { world.objects.findAll(it).filter { tree -> tree.id == TREE }.findFirst().orElse(null) }
            trees.forEach { it.animate(OPEN) }
            plr.submitAction(AgilityAction(plr, listOf(AgilityStep(end, 2)), null))
            world.scheduleOnce(3) { trees.forEach { it.animate(CLOSE) } }
        }
        if (plr.position == start) enter() else plr.submitAction(AgilityApproach(plr, start) { enter() })
    }
}
