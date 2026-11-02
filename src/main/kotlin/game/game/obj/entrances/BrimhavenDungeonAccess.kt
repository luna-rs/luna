package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * Canonical entry credit and directional obstacles shared by the native Brimhaven dungeon and its webwalker.
 * The persisted key is unchanged. The native script initializes this object through its position aliases before
 * login attributes are restored, and still reads the credit at login to retain old saved admission payments.
 * Native interaction handlers continue to own payment, chopping, agility failures, damage and experience.
 *
 * @author lare96
 */
object BrimhavenDungeonAccess {

    const val SANIBOCH = 1595
    const val FEE = 875
    const val COINS = 995
    const val COINS_PILE = 999
    const val ENTRANCE = 5083
    const val OPEN_ENTRANCE = 5082
    const val EXIT = 5084
    val INSIDE = Position(2713, 9564)
    val OUTSIDE = Position(2745, 3152)
    val ENTRANCE_POSITION = Position(2743, 3153)
    val EXIT_POSITION = Position(2714, 9564)

    private var Player.admissionCredit by Attr.boolean().persist("paid_saniboch")

    /**
     * Reads the same unused admission that paying Saniboch grants and entering the dungeon consumes.
     */
    fun hasPaid(player: Player): Boolean = player.admissionCredit

    /**
     * Updates the existing native credit; callers do not create coins or alter its persisted representation.
     */
    fun setPaid(player: Player, paid: Boolean) {
        player.admissionCredit = paid
    }

    /**
     * Source vine thresholds and exact near/far stands. Returning from [end] to [start] needs neither axe nor level.
     *
     * @author lare96
     */
    enum class Vine(val id: Int, val level: Int, val start: Position, val end: Position) {
        FIRST(5103, 10, Position(2691, 9564), Position(2689, 9564)),
        SECOND(5104, 27, Position(2683, 9568), Position(2683, 9570)),
        THIRD(5105, 22, Position(2672, 9499), Position(2674, 9499)),
        FOURTH(5106, 34, Position(2674, 9479), Position(2676, 9479)),
        FIFTH(5107, 34, Position(2693, 9482), Position(2695, 9482));

        val position = Position((start.x + end.x) / 2, (start.y + end.y) / 2)
    }

    /**
     * One directed interaction with an actual native object. Object positions retain raw map planes; player stands
     * and landings use effective planes. Pipes retain their distinct halves in each direction.
     * A [vine] or positive [agility] applies only to this direction, not to the independently listed return route.
     *
     * @author lare96
     */
    data class Passage(val key: String, val id: Int, val position: Position, val from: Position, val to: Position,
                       val agility: Int = 0, val vine: Vine? = null) {
        val needsAbility: Boolean get() = agility > 0 || vine != null
    }

    val passages: List<Passage> = Vine.entries.flatMap { vine ->
        listOf(Passage("vine_${vine.name.lowercase()}_forward", vine.id, vine.position, vine.start, vine.end, vine = vine),
            Passage("vine_${vine.name.lowercase()}_return", vine.id, vine.position, vine.end, vine.start))
    } + listOf(
        Passage("log_east", 5088, Position(2683, 9506, 1), Position(2682, 9506), Position(2687, 9506), agility = 30),
        Passage("log_west", 5090, Position(2686, 9506, 1), Position(2687, 9506), Position(2682, 9506)),
        Passage("stones_south", 5110, Position(2649, 9561), Position(2649, 9562), Position(2647, 9557), agility = 12),
        Passage("stones_north", 5111, Position(2647, 9558), Position(2647, 9557), Position(2649, 9562)),
        Passage("east_pipe_south", 5099, Position(2698, 9498), Position(2698, 9500), Position(2698, 9492), agility = 34),
        Passage("east_pipe_north", 5099, Position(2698, 9493), Position(2698, 9492), Position(2698, 9500)),
        Passage("west_pipe_north", 5100, Position(2655, 9567), Position(2655, 9566), Position(2655, 9573), agility = 22),
        Passage("west_pipe_south", 5100, Position(2655, 9571), Position(2655, 9573), Position(2655, 9566)))

    /**
     * Reuses native current-level and physically carried/worn axe rules, including boosts and drains.
     * Banked tools do not satisfy an obstacle and the free return routes remain free.
     */
    fun canTraverse(player: Player, passage: Passage): Boolean =
        player.agility.level >= passage.agility && passage.vine?.let {
            player.woodcutting.level >= it.level && Axe.computeAxeType(player) != null
        } != false
}
