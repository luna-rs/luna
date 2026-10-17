package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.Skills
import game.skill.woodcutting.Woodcutting
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.impl.LockedAction
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.`object`.GameObject

/**
 * Saniboch, who takes the entry fee outside the Brimhaven Dungeon.
 */
val SANIBOCH = 1595

/**
 * The entry fee.
 */
val FEE = 875

/**
 * Coins, and the pile of coins shown when paying.
 */
val COINS = 995
val COINS_PILE = 999

/**
 * The dungeon entrance, and the same entrance while it's open.
 */
val ENTRANCE = 5083
val OPEN_ENTRANCE = 5082

/**
 * The exit back out of the dungeon.
 */
val EXIT = 5084

/**
 * Where the entrance and exit lead.
 */
val INSIDE = Position(2713, 9564)
val OUTSIDE = Position(2745, 3152)

/**
 * Reaching into the entrance, and chopping vines without an axe.
 */
val REACH = Animation(832)

/**
 * Whether the player has paid Saniboch and not gone in since.
 */
var Player.paidSaniboch by Attr.boolean().persist("paid_saniboch")

/**
 * A vine that blocks a passage in the dungeon.
 *
 * @param id The vine.
 * @param level The Woodcutting level needed to chop through it from [start].
 * @param start The tile on the near side, the only side an axe and the level are needed on.
 * @param end The tile on the far side.
 */
enum class Vine(val id: Int, val level: Int, val start: Position, val end: Position) {
    FIRST(id = 5103, level = 10, start = Position(2691, 9564), end = Position(2689, 9564)),
    SECOND(id = 5104, level = 27, start = Position(2683, 9568), end = Position(2683, 9570)),
    THIRD(id = 5105, level = 22, start = Position(2672, 9499), end = Position(2674, 9499)),
    FOURTH(id = 5106, level = 34, start = Position(2674, 9479), end = Position(2676, 9479)),
    FIFTH(id = 5107, level = 34, start = Position(2693, 9482), end = Position(2695, 9482))
}

/**
 * Says something to [plr] as Saniboch.
 */
fun saniboch(plr: Player, expression: Expression, vararg text: String) =
    plr.newDialogue().npc(SANIBOCH, expression, *text)

/**
 * [plr] pays Saniboch the fee.
 */
fun pay(plr: Player) {
    plr.newDialogue()
        .player("Ok, here's $FEE coins.")
        .then {
            if (it.inventory.remove(Item(COINS, FEE))) {
                it.paidSaniboch = true
                it.sendMessage("You pay Saniboch $FEE coins.")
                Entrances.itemBox(it, COINS_PILE, "You give Saniboch $FEE coins.") { p ->
                    saniboch(p, Expression.HAPPY, "Many thanks. You may now pass the door. May your",
                             "death be a glorious one!").open()
                }
            }
        }
        .open()
}

/**
 * Tells [plr] what lies inside.
 */
fun worthIt(plr: Player) {
    plr.newDialogue()
        .player("Why is it worth the entry cost?")
        .npc(SANIBOCH, Expression.HAPPY, "It leads to a huge fearsome dungeon, populated by",
             "giants and strange dogs. Adventurers come from all", "around to explore its depths.")
        .npc(SANIBOCH, Expression.SAD, "I know not what lies deeper in myself, for my skills in",
             "agility and woodcutting are inadequate, but I hear tell", "of even greater dangers deeper in.")
        .open()
}

/**
 * [plr] asks Saniboch to let them through.
 */
fun askToEnter(plr: Player) {
    val dialogue = plr.newDialogue().player(Expression.QUIZZICAL, "Can I go through that door please?")
    when {
        plr.paidSaniboch ->
            dialogue.npc(SANIBOCH, Expression.HAPPY, "Most certainly, you have already given me lots of nice",
                         "coins.")

        plr.inventory.computeAmountForId(COINS) < FEE ->
            dialogue.npc(SANIBOCH, "Most certainly, but I must charge you the sum of $FEE", "coins first.")
                .player(Expression.SAD, "I don't have the money on me at the moment.")
                .npc(SANIBOCH, Expression.ANGRY, "I'll want $FEE coins to let you enter; this is a dungeon",
                     "for the more wealthy discerning adventurer. Begone", "with you, riff raff.")
                .player(Expression.CONFUSED, "But you don't even have clothes, how can you seriously",
                        "call anyone riff raff.")
                .npc(SANIBOCH, Expression.ANGRY, "Hummph.")

        else ->
            dialogue.npc(SANIBOCH, "Most certainly, but I must charge you the sum of $FEE", "coins first.")
                .options("Ok, here's $FEE coins.", { pay(it) },
                         "Never mind.", { it.newDialogue().player("Never mind.").open() },
                         "Why is it worth the entry cost?", { worthIt(it) })
    }
    dialogue.open()
}

npc1(SANIBOCH) {
    saniboch(plr, Expression.DEFAULT, "Good day to you bwana.")
        .options("Can I go through that door please?", { askToEnter(it) },
                 "Where does this strange entrance lead?", {
                     it.newDialogue()
                         .player(Expression.QUIZZICAL, "Where does this strange entrance lead?")
                         .npc(SANIBOCH, Expression.HAPPY, "To a huge fearsome dungeon, populated by giants and",
                              "strange dogs. Adventurers come from all around to", "explore its depths.")
                         .npc(SANIBOCH, Expression.SAD, "I know not what lies deeper in myself, for my skills in",
                              "agility and woodcutting are inadequate.")
                         .open()
                 },
                 "Good day to you too.", { it.newDialogue().player("Good day to you too.").open() },
                 "I'm impressed, that tree is growing on that shed.", {
                     it.newDialogue()
                         .player("I'm impressed, that tree is growing on that shed.")
                         .npc(SANIBOCH, Expression.HAPPY, "My employer tells me it is an uncommon sort of tree",
                              "called the Fyburglars tree.")
                         .open()
                 })
        .open()
}

// Pay, which pays straight away.
npc2(SANIBOCH) {
    when {
        plr.paidSaniboch ->
            saniboch(plr, Expression.HAPPY, "You have already given me lots of nice coins, you may",
                     "go in.").open()

        plr.inventory.computeAmountForId(COINS) < FEE ->
            plr.newDialogue().player(Expression.SAD, "I don't have the money on me at the moment.").open()

        else -> pay(plr)
    }
}

// Paying lets the player in once.
object1(ENTRANCE) {
    if (!plr.paidSaniboch) {
        if (world.locator.findNpcs(plr.position, 7) { it.id == SANIBOCH }.isNotEmpty()) {
            saniboch(plr, Expression.ANGRY, "You can't go in there without paying!").open()
        }
        return@object1
    }
    val entrance = gameObject
    plr.submitAction(object : LockedAction(plr) {
        override fun onLock() {
            mob.walking.clear()
        }

        override fun run(): Boolean {
            if (executions == 0) {
                mob.animation(REACH)
                Entrances.replace(entrance, OPEN_ENTRANCE, 2)
                return false
            }
            mob.paidSaniboch = false
            mob.move(INSIDE)
            return true
        }
    })
}

object1(EXIT) { plr.move(OUTSIDE) }

// Saved attributes are only kept if they're read during the session (luna-rs/luna#610).
on(LoginEvent::class) { plr.paidSaniboch }

/**
 * Chops through [vine] for [plr], who needs an axe and the [Vine.level] unless they're coming back the other way.
 */
fun chop(plr: Player, obj: GameObject, vine: Vine) {
    val back = plr.position.computeLongestDistance(vine.end) < plr.position.computeLongestDistance(vine.start)
    val axe = Axe.computeAxeType(plr)
    if (!back) {
        if (axe == null) {
            plr.newDialogue().text("To chop through the vines, you'll need an axe that you have the",
                                   "Woodcutting level to use.").open()
            return
        }
        if (plr.woodcutting.level < vine.level) {
            plr.newDialogue().text("You need a Woodcutting level of ${vine.level} to hack your way through the",
                                   "vines.").open()
            return
        }
    }
    plr.submitAction(object : Action<Player>(plr, ActionType.WEAK, true, 3) {
        override fun run(): Boolean {
            mob.animation(axe?.animation ?: REACH)
            if (executions == 0) {
                mob.sendMessage("You try to chop through the vines.")
                return false
            }
            // Coming back without an axe works like a bronze one.
            if (!Woodcutting.success(mob.woodcutting.level, Tree.NORMAL, axe ?: Axe.BRONZE)) {
                return false
            }
            mob.sendMessage("You hack your way through the vines.")
            mob.animation(Animation.CANCEL)
            Entrances.remove(obj, 2)
            mob.move(if (back) vine.start else vine.end)
            return true
        }
    })
}

for (vine in Vine.values()) {
    object1(vine.id) { chop(plr, gameObject, vine) }
}

/**
 * Jumping between stepping stones, squeezing through a pipe, and stumbling.
 */
val JUMP = 741
val SQUEEZE = 749
val STUMBLE = Animation(764)

/**
 * Walking across a log.
 */
val LOG_WALK = 762

/**
 * [plr] falls into the lava, takes a tenth of their hitpoints and is pulled out at [landing], facing [face].
 */
fun fallIntoLava(plr: Player, face: Direction, landing: Position, delay: Int) {
    plr.sendMessage("...You lose your footing and fall into the lava.")
    plr.face(face)
    plr.animation(STUMBLE)
    plr.playSound(Sound.STUMBLE_LOOP)
    plr.damage(plr.hitpoints.level / 10 + 1)
    world.scheduleOnce(delay) {
        plr.playSound(Sound.SIZZLE)
        plr.move(landing)
    }
}

/**
 * Jumps [plr] across the stepping stones in [hops], falling in after the first [safe] hops if they fail the roll.
 */
fun crossStones(plr: Player, hops: List<Direction>, safe: Int, fail: Position, failFace: Direction) {
    plr.sendMessage("You carefully start crossing the stepping stones...")
    val steps = ArrayList<(() -> Unit)?>()
    var fell = false
    hops.forEachIndexed { i, dir ->
        steps += {
            if (!fell) {
                if (i == safe && !Skills.success(210, 275, plr.agility.level)) {
                    fell = true
                    fallIntoLava(plr, failFace, fail, 2)
                } else {
                    plr.playSound(Sound.JUMP_NO_LAND)
                    Entrances.slide(plr, plr.position.translate(1, dir), 15, 30, JUMP)
                }
            }
        }
        if (i < hops.size - 1) steps += null
    }
    steps += {
        if (!fell) {
            plr.agility.addExperience(7.5)
            plr.sendMessage("...You safely cross to the other side.")
        }
    }
    Entrances.sequence(plr, *steps.toTypedArray())
}

// The stepping stones over the lava, which need 12 Agility from the north.
object1(5110) {
    if (plr.agility.level < 12) {
        plr.newDialogue().text("You need an Agility level of 12 to cross the rocks.").open()
        return@object1
    }
    val s = Direction.SOUTH
    val w = Direction.WEST
    crossStones(plr, listOf(s, s, w, w, s, s, s), 3, Position(2648, 9562), Direction.NORTH)
}
object1(5111) {
    val n = Direction.NORTH
    val e = Direction.EAST
    crossStones(plr, listOf(n, n, n, e, e, n, n), 4, Position(2648, 9557), Direction.SOUTH)
}

/**
 * Walks [plr] across the log over the lava, [step] tiles at a time, falling off to [fail] if they fail the roll.
 */
fun crossLog(plr: Player, log: GameObject, step: Int, fail: Position) {
    val dir = if (step > 0) Direction.EAST else Direction.WEST
    // The log is on a bridge, which the map keeps on the floor above.
    plr.move(Position(log.position.x - step, log.position.y, plr.position.z))
    plr.sendMessage("You walk carefully across the slippery log...")
    val fell = !Skills.success(210, 275, plr.agility.level)
    Entrances.sequence(plr, { Entrances.slide(plr, plr.position.translate(step, 0), 0, 30, LOG_WALK) }, {
        plr.playSound(Sound.LOG_BALANCE)
        Entrances.slide(plr, plr.position.translate(step, 0), 0, 30, LOG_WALK)
    }, {
        if (fell) {
            fallIntoLava(plr, dir, fail, 1)
        } else {
            plr.playSound(Sound.LOG_BALANCE)
            Entrances.slide(plr, plr.position.translate(step * 2, 0), 0, 60, LOG_WALK)
        }
    }, null, {
        if (!fell) {
            plr.sendMessage("...You make it safely to the other side.")
            Entrances.slide(plr, plr.position.translate(step, 0), 0, 30, LOG_WALK)
            plr.agility.addExperience(10.0)
        }
    })
}

// The log over the lava, which needs 30 Agility from the west.
object1(5088) {
    if (plr.agility.level < 30) {
        plr.newDialogue().text("You need an Agility level of 30 to cross the log.").open()
        return@object1
    }
    crossLog(plr, gameObject, 1, Position(2681, 9507))
}
object1(5090) { crossLog(plr, gameObject, -1, Position(2688, 9508)) }

/**
 * Squeezes [plr] from [start] through a pipe in two halves, [first] tiles then a jump of [gap] then [first] tiles
 * again, northwards if [north].
 */
fun squeezeThrough(plr: Player, start: Position, north: Boolean, gap: Int, xp: Double) {
    val dir = if (north) 1 else -1
    plr.move(start)
    plr.playSound(Sound.SQUEEZE_IN)
    Entrances.sequence(plr, { Entrances.slide(plr, start.translate(0, 3 * dir), 30, 126, SQUEEZE) }, null, null, {
        plr.move(plr.position.translate(0, gap * dir))
    }, {
        plr.playSound(Sound.SQUEEZE_IN)
        Entrances.slide(plr, plr.position.translate(0, 3 * dir), 30, 126, SQUEEZE)
    }, null, null, { plr.agility.addExperience(xp) })
}

// The pipes, which need 34 Agility going south through the eastern one, and 22 going north through the western one.
object1(5099) {
    if (plr.position.y > gameObject.position.y) {
        if (plr.agility.level < 34) {
            plr.newDialogue().text("You need an Agility level of 34 to squeeze through the pipe.").open()
            return@object1
        }
        squeezeThrough(plr, Position(2698, 9500), north = false, gap = 2, xp = 10.0)
    } else {
        squeezeThrough(plr, Position(2698, 9492), north = true, gap = 2, xp = 10.0)
    }
}
object1(5100) {
    if (plr.position.y <= gameObject.position.y) {
        if (plr.agility.level < 22) {
            plr.newDialogue().text("You need an Agility level of 22 to squeeze through the pipe.").open()
            return@object1
        }
        squeezeThrough(plr, Position(2655, 9566), north = true, gap = 1, xp = 8.5)
    } else {
        squeezeThrough(plr, Position(2655, 9573), north = false, gap = 1, xp = 8.5)
    }
}
