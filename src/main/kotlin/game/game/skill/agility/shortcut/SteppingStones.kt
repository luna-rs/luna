package game.skill.agility.shortcut

import api.predef.*
import api.predef.ext.*
import game.skill.agility.Agility
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import game.skill.agility.AgilityWalk
import game.skill.agility.shortcut.ShortcutMoves.STUMBLE
import game.skill.agility.shortcut.ShortcutMoves.WASHED_UP
import game.skill.agility.shortcut.ShortcutMoves.active
import game.skill.agility.shortcut.ShortcutMoves.fallInto
import game.skill.agility.shortcut.ShortcutMoves.keepsFooting
import game.skill.agility.shortcut.ShortcutMoves.ready
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The stepping stone shortcuts, crossed a stone at a time: each click jumps onto the stone next to the player, from a
 * bank or from the stone before it, either way. Each jump checks the level and rolls its own fail; a fall drops the
 * player into the river beside the stone and washes them up on the bank they were heading for.
 *
 * @author TheLining
 */
object SteppingStones {

    /**
     * A row of stones between two banks.
     *
     * @param level The Agility level each jump needs.
     * @param low The fail roll's low end.
     * @param high The fail roll's high end.
     * @param stones The stones' ids and tiles, in order from [banks]' first to its second.
     * @param banks The tiles at either end of the row.
     * @param downstream The side of each stone a falling player lands in the river.
     */
    class Row(val level: Int, val low: Int, val high: Int, val stones: List<Pair<Int, Position>>,
              val banks: Pair<Position, Position>, val downstream: Pair<Int, Int>) {

        /**
         * The row's tiles in order: a bank, each stone and the other bank.
         */
        val tiles = listOf(banks.first) + stones.map { it.second } + banks.second
    }

    /**
     * The River Lum stones between Draynor Manor and the Champions' Guild, and the stones in south-eastern Karamja.
     */
    val ROWS = listOf(
        Row(31, 45, 255, (3150..3153).map { 9315 to Position(it, 3363) },
            Position(3149, 3363) to Position(3154, 3363), 0 to -1),
        Row(30, 50, 253, listOf(2333 to Position(2925, 2950), 2334 to Position(2925, 2949),
                                2335 to Position(2925, 2948)),
            Position(2925, 2951) to Position(2925, 2947), 1 to 0))

    /**
     * Every stone's object id.
     */
    val IDS = ROWS.flatMap { row -> row.stones.map { it.first } }.toSet()

    /**
     * XP for each stone jumped onto, and for a fall.
     */
    private const val XP = 3.0
    private const val FALL_XP = 1.0

    /**
     * Jumps onto [stone] from the tile next to it in its row: the player's own tile, or the bank beside the first or
     * last stone, which a player close by walks onto first.
     */
    fun jump(plr: Player, stone: GameObject) {
        val row = ROWS.firstOrNull { row -> row.stones.any { it.first == stone.id && it.second == stone.position } }
            ?: return
        if (!ready(plr)) return
        val at = row.tiles.indexOf(stone.position)
        val here = row.tiles.indexOf(plr.position)
        val from = when {
            here == at - 1 || here == at + 1 -> plr.position
            at == 1 -> row.tiles.first()
            at == row.tiles.size - 2 -> row.tiles.last()
            else -> return
        }
        if (plr.position.computeLongestDistance(from) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(plr) || plr.position != from || stone.state != EntityState.ACTIVE) return@begin
            if (!Agility.checkLevel(plr, row.level)) return@begin
            val heading = if (row.tiles.indexOf(from) < at) row.tiles.last() else row.tiles.first()
            val fails = !keepsFooting(plr.agility.level, row.low, row.high)
            plr.submitAction(if (fails) fall(plr, row, stone.position, heading) else land(plr, stone.position))
        }
        if (plr.position == from) begin() else plr.submitAction(AgilityApproach(plr, from) { begin() })
    }

    /**
     * The jump onto [stone].
     */
    private fun jumpOnto(stone: Position) = AgilityStep(stone, Agility.STONE_TICKS,
                                                        animation = Agility.STONE_JUMP_ANIMATION,
                                                        moveStart = Agility.STONE_JUMP_START,
                                                        moveEnd = Agility.STONE_JUMP_END)

    /**
     * Jumps onto [stone] and keeps balance.
     */
    private fun land(plr: Player, stone: Position) =
        AgilityAction(plr, listOf(jumpOnto(stone)), "You attempt to balance on the stepping stone.",
                      endMessage = "You manage to make the jump.", valid = { active(plr) }).also {
            it.onSuccess = { plr.agility.addExperience(XP) }
        }

    /**
     * Jumps onto [stone], stumbles, slips into the river beside it and is washed up on [heading], the bank the player
     * was making for, 10 ticks after the slip, with 1 to 3 damage.
     */
    private fun fall(plr: Player, row: Row, stone: Position, heading: Position): AgilityAction {
        val water = stone.translate(row.downstream.first, row.downstream.second)
        val swim = AgilityWalk.SWIM.walk
        return object : AgilityAction(plr, listOf(
            jumpOnto(stone),
            AgilityStep(stone, 1, animation = STUMBLE, jump = true),
            AgilityStep(water, 1, walkingAnimation = swim, jump = true, message = "You slip and fall...",
                        begin = { fallInto(plr, water) }),
            AgilityStep(heading, 9, walkingAnimation = swim, jump = true)),
            "You attempt to balance on the stepping stone.", valid = { active(plr) }), DeathInterruptible {
            init {
                onSuccess = {
                    WASHED_UP.forEach { plr.sendMessage(it) }
                    plr.damage(rand(1, 3))
                    plr.agility.addExperience(FALL_XP)
                }
            }
        }
    }
}
