package game.skill.agility.course

import api.predef.*
import game.skill.agility.*
import game.skill.agility.Agility.course
import game.skill.agility.AgilityCourse.APE_ATOLL
import game.skill.agility.AgilityObstacle.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * The small and medium ninja monkey greegrees. Only a player holding one is monkey enough for the course.
 */
val NINJA_MONKEY_GREEGREES = setOf(4024, 4025)

/**
 * The other monkey greegrees.
 */
val OTHER_MONKEY_GREEGREES = (4026..4031).toSet()

/**
 * The ninja monkey's course animations.
 */
val STONE_JUMP = 3480
val STONE_FAIL = 3489
val CLIMB_TREE = 3487
val CLIMB_TREE_FAIL = 3492
val BARS_UP = 3482
val BARS_MOVE = 3483
val BARS_DOWN = 3484
val CLIMB_SLOPE = 3485
val SLIDE_BACK = 3486
val VINE_SWING = 3488
val FALLING = 3493
val DOWN_VINE = 3494

val STONE = Position(2754, 2742)
val ISLAND = Position(2753, 2742)
val BARS_START = Position(2752, 2741, 2)
val BARS_END = Position(2747, 2741, 2)
val SLOPE_TOP = Position(2744, 2741)
val ROPE_END = Position(2756, 2731)
val VINE_TOP = Position(2757, 2734, 1)
val VINE_END = Position(2770, 2747, 1)

/**
 * Where a fall into the river washes up: the start bank, by the signpost.
 */
val START_BANK = Position(2757, 2747)

/**
 * The island bank beside the first tree, where the rope's stream washes up.
 */
val ISLAND_BANK = Position(2752, 2741)

/**
 * Floats in the river at [from] for [wait] ticks, sending [message] if given, then swims to [to].
 */
fun swim(from: Position, to: Position, wait: Int, message: String? = null) = listOf(
    AgilityStep(from, wait, walkingAnimation = AgilityWalk.MONKEY_SWIM.walk, jump = true, message = message),
    AgilityStep(to, maxOf(1, from.computeLongestDistance(to)), walkingAnimation = AgilityWalk.MONKEY_SWIM.walk))

/**
 * The tick a monkey stands on an obstacle's start tile before taking it. In 2006–07 footage each obstacle starts
 * about two ticks after the click, a tick more than a click takes to reach the server.
 */
fun Player.ready() = AgilityStep(position, 1, jump = true)

/**
 * A fall that deals [failure]'s damage, from current hitpoints, as its last leg ends.
 */
fun Player.fall(steps: List<AgilityStep>, failure: AgilityFailure, message: String? = failure.message) =
    AgilityAction(this, steps, message).also { it.onSuccess = { damage(failure.damage(hitpoints.level)) } }

/**
 * Turns away a player who isn't a ninja monkey. Other monkeys aren't let on. A human tries and falls: [first] at
 * once, [second] a tick later at [landing], and [failure]'s damage a tick after that.
 */
fun Player.notMonkey(first: String, failure: AgilityFailure, landing: Position = position,
                     second: String = "...you're not monkey enough to try this!"): AgilityAction? {
    if (equipment.weapon?.id in OTHER_MONKEY_GREEGREES) {
        sendMessage("Only the stealthiest and most agile monkey can use this!")
        return null
    }
    // TODO Humans should then be knocked out and thrown in the Ape Atoll jail.
    return fall(listOf(AgilityStep(landing, 1, jump = true), AgilityStep(landing, 1, jump = true, message = second)),
        failure, first)
}

course(APE_ATOLL) {
    // TODO Only players who have finished Monkey Madness may use the course.
    canPass = { equipment.weapon?.id in NINJA_MONKEY_GREEGREES }

    obstacle(12568, APE_STEPPING_STONE,
        fail = {
            // The slip comes a tick after the first jump, while the monkey is still on the stone.
            val failure = AgilityFailure.APE_STEPPING_STONE
            val water = STONE.translate(0, 1)
            fall(listOf(ready(), AgilityStep(STONE, 1, animation = STONE_JUMP, moveStart = 15, moveEnd = 28),
                AgilityStep(water, 2, animation = STONE_FAIL, jump = true, message = failure.message))
                    + swim(water, START_BANK, 1), failure, null)
        },
        refuse = {
            notMonkey("The rock is covered in slime and you slip into the water...",
                AgilityFailure.APE_STEPPING_STONE, START_BANK)
        }) {
        // One click takes both jumps; the monkey waits on the stone in between.
        AgilityAction(this, listOf(ready(),
            AgilityStep(STONE, 3, animation = STONE_JUMP, moveStart = 15, moveEnd = 28),
            AgilityStep(ISLAND, 1, animation = STONE_JUMP, moveStart = 15, moveEnd = 28)), null)
    }

    obstacle(12570, APE_TROPICAL_TREE,
        fail = {
            val failure = AgilityFailure.APE_TROPICAL_TREE
            val water = ISLAND.translate(0, 1)
            fall(listOf(ready(),
                AgilityStep(water, 4, animation = CLIMB_TREE_FAIL, jump = true, message = failure.message))
                    + swim(water, START_BANK, 2, "Something in the water bites you..."), failure, null)
        },
        refuse = {
            notMonkey("You reach for the tree trunk and lose your footing...", AgilityFailure.APE_TROPICAL_TREE,
                START_BANK)
        }) {
        AgilityAction(this, listOf(ready(), AgilityStep(BARS_START, 3, animation = CLIMB_TREE, jump = true)), null)
    }

    obstacle(12573, APE_MONKEY_BARS,
        fail = {
            // In footage the fall comes about three bars along, into the river beside the island.
            val failure = AgilityFailure.APE_MONKEY_BARS
            val water = ISLAND_BANK.translate(-1, 0)
            fall(listOf(ready(),
                AgilityStep(BARS_START, 1, walkingAnimation = BARS_MOVE, animation = BARS_UP, jump = true),
                AgilityStep(BARS_START.translate(-3, 0), 3, walkingAnimation = BARS_MOVE),
                AgilityStep(water, 1, animation = FALLING, jump = true))
                    + swim(water, START_BANK, 4, failure.message), failure, null)
        },
        refuse = { notMonkey("Your hands slip from the rung...", AgilityFailure.APE_MONKEY_BARS, START_BANK) }) {
        // The monkey hangs from the first bar as soon as it's up, rather than standing until it moves off. The rise
        // gets one tick, less than its animation: footage has the monkey down eight ticks after the click arrives.
        AgilityAction(this, listOf(ready(),
            AgilityStep(BARS_START, 1, walkingAnimation = BARS_MOVE, animation = BARS_UP, jump = true),
            AgilityStep(BARS_END, 5, walkingAnimation = BARS_MOVE),
            AgilityStep(BARS_END.translate(0, 0, -2), 1, animation = BARS_DOWN, jump = true)), null)
    }

    obstacle(12576, APE_SKULL_SLOPE,
        fail = {
            val failure = AgilityFailure.APE_SKULL_SLOPE
            val bottom = position
            AgilityAction(this, listOf(ready(),
                AgilityStep(bottom.translate(-1, 0), 1, animation = CLIMB_SLOPE, moveEnd = 25),
                AgilityStep(bottom, 2, animation = SLIDE_BACK, moveEnd = 45, message = failure.message,
                    begin = {
                        sendMessage("...and slide back down the slope.")
                        damage(failure.damage(hitpoints.level))
                    })), null)
        },
        refuse = { notMonkey("The hand holds are too small to hold onto...", AgilityFailure.APE_SKULL_SLOPE) }) {
        // The climb covers the three skull tiles in about two and a half ticks, faster than a walk (footage), so it
        // moves the monkey; its looping climb is stopped once the monkey is up.
        AgilityAction(this, listOf(ready(), AgilityStep(SLOPE_TOP, 3, animation = CLIMB_SLOPE, moveEnd = 75),
            AgilityStep(SLOPE_TOP, 1, jump = true, begin = { animation(Animation(-1)) })), null)
    }

    obstacle(12578, APE_ROPE,
        fail = {
            // The monkey drops into the stream under the rope, which carries it round to the first tree's island.
            val stream = Position(2753, 2730)
            val pool = ISLAND_BANK.translate(-1, 0)
            val failure = AgilityFailure.APE_ROPE
            fall(listOf(ready(), AgilityStep(stream, 2, animation = VINE_SWING, moveStart = 17, moveEnd = 30,
                message = failure.message),
                AgilityStep(pool, 3, walkingAnimation = AgilityWalk.MONKEY_SWIM.walk, jump = true,
                    message = "...something in the water bites you."),
                AgilityStep(ISLAND_BANK, 1, walkingAnimation = AgilityWalk.MONKEY_SWIM.walk),
                AgilityStep(ISLAND_BANK, 1, jump = true)), failure, null)
        },
        refuse = {
            notMonkey(AgilityFailure.APE_ROPE.message, AgilityFailure.APE_ROPE, Position(2755, 2742))
        }) {
        AgilityAction(this, listOf(ready(),
            AgilityStep(ROPE_END, 2, animation = VINE_SWING, moveStart = 17, moveEnd = 44)), null)
    }

    obstacle(12618, APE_ZIP_VINE,
        fail = {
            val failure = AgilityFailure.APE_ZIP_VINE
            fall(listOf(AgilityStep(position, 3, animation = CLIMB_TREE_FAIL, jump = true),
                AgilityStep(failure.landing, 2, jump = true, message = "...and lose your grip!")), failure)
        },
        refuse = {
            notMonkey(AgilityFailure.APE_ZIP_VINE.message, AgilityFailure.APE_ZIP_VINE,
                second = "...and lose your grip!")
        }) {
        // Straight up onto the vine (footage shows no wait), down it to the hut, then off its end onto the ground.
        // The client keeps a stance through a forced move, so the slide's pose is set on the tick before it.
        AgilityAction(this, listOf(
            AgilityStep(VINE_TOP, 1, walkingAnimation = DOWN_VINE, jump = true, begin = { move(VINE_TOP) }),
            AgilityStep(VINE_END, 7),
            AgilityStep(VINE_END.translate(0, 0, -1), 1, jump = true)), null)
    }
}
