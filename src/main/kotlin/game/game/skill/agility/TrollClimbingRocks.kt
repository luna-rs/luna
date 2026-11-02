package game.skill.agility

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.Skills
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Troll Stronghold's ordinary climbing rocks, sharing Lost City's Underground Pass rockslide action.
 * Both directions require current Agility15; only the southern3611 approach needs worn climbing boots.
 * A slip leaves the player on their starting side and deals three damage, with no obstacle XP.
 * TODO Restore the Troll Stronghold started check once the quest is implemented, as for the other quest entrances.
 *
 * @author lare96
 */
object TrollClimbingRocks {
    const val ROCK = 3748
    const val BOOTS = 3105
    const val LEVEL = 15

    /**
     * Actual revision377 placements and their walkable opposite-side stands. Duplicate nearby placements
     * are retained so either visible rock can be clicked; each is a native climb, never a walking bridge.
     *
     * @author lare96
     */
    data class Passage(val position: Position, val from: Position, val to: Position)

    val PASSAGES = listOf(
        Passage(Position(2821, 3635), Position(2820, 3635), Position(2822, 3635)),
        Passage(Position(2833, 3628), Position(2833, 3627), Position(2833, 3629)),
        Passage(Position(2834, 3628), Position(2834, 3627), Position(2834, 3629)),
        Passage(Position(2856, 3612), Position(2856, 3611), Position(2856, 3613)),
        Passage(Position(2857, 3612), Position(2857, 3611), Position(2857, 3613)),
        Passage(Position(2910, 3686), Position(2909, 3686), Position(2911, 3686)),
        Passage(Position(2910, 3687), Position(2909, 3687), Position(2911, 3687)))

    /**
     * Current, physically worn admission; owning boots in the bank does not satisfy the southern approach.
     */
    fun canTraverse(player: Player, from: Position) = player.agility.level >= LEVEL &&
        (from.y != 3611 || hasBoots(player))

    /**
     * Only the source climbing boots satisfy the southern rocks' equipment check.
     */
    fun hasBoots(player: Player) = player.equipment[Equipment.BOOTS]?.id == BOOTS

    /**
     * Resolves only the source-placed rocks, aligns through the navigator, then rolls the native slip chance.
     * Both admission and actor state are checked again after the weak approach completes.
     */
    fun climb(player: Player, obj: GameObject, succeeds: (Int) -> Boolean = { Skills.success(95, 305, minOf(it, 99)) }) {
        if (obj.id != ROCK || !ready(player) || player.z != 0) return
        val passage = PASSAGES.firstOrNull { it.position == obj.position } ?: return
        val from = listOf(passage.from, passage.to).minBy { player.position.computeLongestDistance(it) }
        if (player.position.computeLongestDistance(from) > AgilityApproach.RADIUS) return
        val to = if (from == passage.from) passage.to else passage.from
        val begin = begin@ {
            if (!ready(player) || player.position != from) return@begin
            if (!Agility.checkLevel(player, LEVEL)) return@begin
            if (from.y == 3611 && !hasBoots(player)) {
                player.sendMessage("You'll need some climbing boots to go that way.")
                return@begin
            }
            val failed = !succeeds(player.agility.level)
            val step = if (failed) AgilityStep(from, 1, animation = 846)
            else AgilityStep(to, 2, animation = 839,
                sounds = listOf(AgilitySound(Sound.CLIMBING_LOOP, loops = 3, delayCycles = 4)))
            player.submitAction(AgilityAction(player, listOf(step), "You climb onto the rock...",
                endMessage = if (failed) "...but you slip back down." else "...and step down the other side.",
                valid = { player.state == EntityState.ACTIVE && player.isAlive }).also {
                it.onSuccess = { if (failed) player.damage(3) }
            })
        }
        if (player.position == from) begin() else player.submitAction(AgilityApproach(player, from) { begin() })
    }

    private fun ready(player: Player) = player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked
}
