package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The skavid map, which shows the way through the skavid caves.
 */
val SKAVID_MAP = 2376

/**
 * The lit light sources: candles, a torch, lamps and lanterns.
 */
val LIT_LIGHTS = setOf(32, 33, 594, 4524, 4531, 4534, 4539, 4550, 4702, 7053)

/**
 * The dark cave players without a light end up in, and the rope out of it.
 */
val DARK_CAVE = Position(2505, 9460)
val DARK_CAVE_ROPE = Position(2536, 9425)

/**
 * A skavid cave entrance north of Gu'Tanoth.
 *
 * @param id The entrance.
 * @param inside Where it leads with a map and a light.
 * @param lost Where players without a map come out instead.
 */
enum class SkavidCave(val id: Int, val inside: Position, val lost: Position) {
    FIRST(id = 2805, inside = Position(2498, 9418), lost = Position(2523, 3070)),
    SECOND(id = 2806, inside = Position(2532, 9469), lost = Position(2540, 3055)),
    THIRD(id = 2807, inside = Position(2518, 9455), lost = Position(2553, 3055)),
    FOURTH(id = 2808, inside = Position(2498, 9451), lost = Position(2552, 3034)),
    FIFTH(id = 2809, inside = Position(2504, 9441), lost = Position(2563, 3024)),
    SIXTH(id = 2810, inside = Position(2522, 9411), lost = Position(2523, 3070))
}

/**
 * Whether the player is in the dark cave and hasn't found the rope out yet.
 */
var Player.lookingForSkavidRope by Attr.boolean()

/**
 * Leaves [plr] lost in the dark cave until they get close enough to the rope to find it.
 */
fun loseInDark(plr: Player) {
    plr.move(DARK_CAVE)
    plr.lookingForSkavidRope = true
    plr.newDialogue()
        .player(Expression.SHOCKED, "Oh my! It's dark!", "All I can see are lots of rocks on the floor.",
                "I suppose I better search them for a way out.")
        .open()
    world.schedule(1) {
        when {
            plr.state != EntityState.ACTIVE || !plr.lookingForSkavidRope -> it.cancel()
            plr.position.isWithinDistance(DARK_CAVE_ROPE, 5) -> {
                plr.lookingForSkavidRope = false
                plr.sendMessage("You find a rope!")
                it.cancel()
            }
        }
    }
}

// The skavid caves need the skavid map and a lit light. Without the map players come out of another cave, and without
// a light they end up lost in the dark.
for (cave in SkavidCave.values()) {
    object1(cave.id) {
        when {
            !plr.inventory.contains(SKAVID_MAP) -> {
                plr.sendMessage("There's no way I can find my way through without a map of some kind.")
                plr.move(cave.lost)
            }

            LIT_LIGHTS.none { plr.inventory.contains(it) } -> loseInDark(plr)

            else -> {
                plr.sendMessage("You enter the cave...")
                plr.move(cave.inside)
            }
        }
    }
}

// The rocks in the dark cave.
object1(2835) {
    plr.sendMessage("You search the rock.")
    plr.sendMessage("There's nothing here.")
}

// The rope out of the dark cave.
object1(2825) {
    plr.lookingForSkavidRope = false
    plr.move(Position(2540, 3054))
    plr.newDialogue().player(Expression.SHOCKED, "Phew! At last I'm out...", "Next time I will take some light!").open()
}

// The tunnel from the mainland onto Toban's island.
object1(2811) {
    plr.sendMessage("You enter the cave.")
    plr.move(Position(2576, 3029))
    plr.newDialogue().player(Expression.SHOCKED, "Wow! That tunnel went a long way.").open()
}
