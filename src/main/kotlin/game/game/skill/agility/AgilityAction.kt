package game.skill.agility

import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.net.msg.out.SoundMessageWriter
import java.util.ArrayDeque

/**
 * One timed leg of an agility traversal. Jumps change position after the animation; legs with a [walkingAnimation]
 * are walked a tile a tick with it ([AgilityWalk]); other legs use exact movement with tick durations converted to
 * client cycles. [moveStart] and [moveEnd] place that movement inside the leg in client cycles; by default it fills
 * the leg. [animation] starts [animationTick] ticks into the leg. [message] is sent and [begin] runs when the leg
 * starts.
 *
 * @author lare96
 */
data class AgilityStep(val destination: Position, val ticks: Int,
                       val walkingAnimation: Int = -1, val animation: Int = -1,
                       val jump: Boolean = false,
                       val sounds: List<AgilitySound> = emptyList(),
                       val message: String? = null,
                       val begin: () -> Unit = {},
                       val moveStart: Int = 0, val moveEnd: Int = -1,
                       val animationTick: Int = 0) {
    init {
        require(ticks > 0)
        require(moveEnd == -1 || moveEnd > moveStart)
        require(sounds.all { it.tick in 0..ticks })
        require(animationTick in 0 until ticks)
    }
}

/**
 * Walks this player in a straight line to [destination], a tile a tick even when they have running switched on.
 */
internal fun Player.walkStraight(destination: Position) {
    walking.clear()
    walking.isRunning = false
    walking.replacePath(ArrayDeque(listOf(destination)))
    // The client turns to a face target once the walk ends; point it on along the walk, or the player would turn
    // back to the object they clicked.
    face(destination.translate(1, Direction.between(position, destination)))
}

/**
 * A sound emitted on a specific tick of an agility leg, with the client's repeat count and cycle delay.
 *
 * @author lare96
 */
data class AgilitySound(val sound: Sound, val tick: Int = 0, val loops: Int = 1, val delayCycles: Int = 0) {
    init {
        require(tick >= 0)
        require(loops in 1..255)
        require(delayCycles in 0..65535)
    }
}

/**
 * Runs all legs under one lock and reports success only after the last leg finishes.
 * The first leg starts on the tick the lock is taken, and each later leg on the tick the one before it ends.
 * [message] is sent on the first tick and [endMessage] when the last leg finishes.
 * The client walks a little behind the server, so a walk's animations last a tick into a following leg that has none,
 * and a traversal that ends with a walk keeps them until the client should have finished it before it unlocks.
 * Interrupted traversals restore animations and never award XP or course credit.
 *
 * @author lare96
 */
class AgilityAction(plr: Player, private val steps: List<AgilityStep>,
                    internal val message: String?,
                    private val start: () -> Unit = {},
                    private val endMessage: String? = null) : LockedAction(plr) {

    companion object {

        /**
         * Client cycles in one game tick. The 377 client runs a cycle every 20 ms.
         */
        const val CYCLES_PER_TICK = 30
    }

    internal var onSuccess: () -> Unit = {}
    private var previousAnimation: PlayerModelAnimation? = null
    private var leg = 0
    private var elapsed = 0
    private var finished = false

    /**
     * Animations held back for a tick, while the client finishes the previous leg's steps in that leg's.
     */
    private var heldModel: PlayerModelAnimation? = null

    /**
     * Ticks the finished traversal still holds its last walk's animations before unlocking.
     */
    private var settling = 0

    /**
     * Tiles walked in the current run of walk legs. The client ends a walk about 2 cycles a tile plus 15 behind the
     * server (32 cycles a tile against 30 a tick, a late start and the first turn).
     */
    private var walkedTiles = 0

    init {
        require(steps.isNotEmpty())
    }

    override fun onLock() {
        previousAnimation = mob.model
        mob.walking.clear()
        if (steps.first().animation == -1) mob.animation(Animation(-1))
        message?.let { mob.sendMessage(it) }
        start()
        beginLeg()
    }

    override fun run(): Boolean {
        if (settling > 0) return --settling == 0
        val step = steps[leg]
        elapsed++
        heldModel?.let {
            showModel(it)
            heldModel = null
        }
        step.sounds.filter { it.tick == elapsed }.forEach { play(it) }
        if (step.animation != -1 && step.animationTick == elapsed) mob.animation(Animation(step.animation))
        if (elapsed < step.ticks) return false
        if (step.destination != mob.position) {
            // Exact movement and jumps only move the player now, so the client never sees them jump ahead. A walk is
            // already here unless something stopped its steps.
            mob.walking.clear()
            mob.move(step.destination)
        }
        leg++
        if (leg == steps.size) {
            finished = true
            endMessage?.let { mob.sendMessage(it) }
            onSuccess()
            // The client walks a little behind the server, so it gets time to finish the last steps in this walk's
            // animations.
            if (!step.jump && step.walkingAnimation != -1 && mob.model !== previousAnimation) {
                settling = (2 * walkedTiles + 15 + CYCLES_PER_TICK - 1) / CYCLES_PER_TICK
            }
            return settling == 0
        }
        beginLeg()
        return false
    }

    override fun onUnlock() {
        previousAnimation?.let { mob.model = it }
        mob.flags.flag(UpdateFlag.APPEARANCE)
        mob.walking.isRunning = mob.isRunning
        // A finished traversal lets its last animation play out, so a climb ends on the new floor.
        if (!finished) {
            mob.walking.clear()
            mob.animation(Animation(-1))
        }
    }

    /**
     * Starts the current leg: its message, opening sounds, animations and movement.
     */
    private fun beginLeg() {
        val step = steps[leg]
        elapsed = 0
        step.message?.let { mob.sendMessage(it) }
        step.begin()
        step.sounds.filter { it.tick == 0 }.forEach { play(it) }
        val model = AgilityWalk.modelFor(step.walkingAnimation, previousAnimation)
        val walked = leg > 0 && steps[leg - 1].walkingAnimation != -1 && !steps[leg - 1].jump
        if (walked && model === previousAnimation && mob.model !== previousAnimation) {
            heldModel = model
        } else if (model != null && model !== mob.model) {
            showModel(model)
        }
        if (step.animation != -1 && step.animationTick == 0) mob.animation(Animation(step.animation))
        walkedTiles = if (!step.jump && step.walkingAnimation != -1) {
            walkedTiles + mob.position.computeLongestDistance(step.destination)
        } else 0
        when {
            step.jump -> Unit
            step.walkingAnimation != -1 -> mob.walkStraight(step.destination)
            else -> {
                val from = mob.position
                val end = if (step.moveEnd == -1) step.ticks * CYCLES_PER_TICK else step.moveEnd
                mob.exactMove(ExactMovement(mob.lastRegion, from, step.destination, step.moveStart, end,
                    Direction.between(from, step.destination)))
            }
        }
    }

    private fun showModel(model: PlayerModelAnimation) {
        mob.model = model
        mob.flags.flag(UpdateFlag.APPEARANCE)
    }

    private fun play(sound: AgilitySound) =
        mob.queue(SoundMessageWriter(sound.sound.id, sound.loops, sound.delayCycles))
}
