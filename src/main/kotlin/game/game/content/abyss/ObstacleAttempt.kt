package game.content.abyss

import api.predef.*
import api.predef.ext.*
import game.content.abyss.Abyss.abyssLayout
import game.content.abyss.Abyss.showLayout
import game.player.Sound
import game.skill.Skills
import game.skill.mining.Pickaxe
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * An attempt to get past an obstacle with [skill]. [plr] is locked until it ends.
 */
abstract class ObstacleAttempt(val plr: Player, val passage: AbyssPassage, val obj: GameObject, val skill: Int) :
    LockedAction(plr) {

    companion object {

        /**
         * The experience for getting past an obstacle.
         */
        const val OBSTACLE_XP = 25.0

        /**
         * The tinderbox, for burning down boils.
         */
        const val TINDERBOX = 1590

        /**
         * The animations a player distracts the eyes with.
         */
        val DISTRACT_ANIMATIONS = listOf(794, 1835, 864, 865, 1130)

        /**
         * Lighting a fire, to burn down a boil.
         */
        val CREATE_FIRE = Animation(733)

        /**
         * Dropping to the knees in front of a gap.
         */
        val KNEEL = Animation(1331)

        /**
         * Getting back up after failing to squeeze through a gap.
         */
        val KNEEL_AND_STAND = Animation(1332)

        /**
         * Crawling through a gap.
         */
        val CRAWL = Animation(844)

        /**
         * The fire wave impact graphic, shown on a boil as it bursts.
         */
        const val BOIL_BURST = 157

        /**
         * The [AbyssObstacle] that [passage] shows [plr].
         */
        fun obstacleFor(plr: Player, passage: GameObject): AbyssObstacle? {
            val children = passage.def().varpDef.childIdList
            return AbyssObstacle.ID_TO_OBSTACLE[children.getOrNull(plr.abyssLayout)]
        }

        /**
         * The middle of [obj], which players face while they deal with it.
         */
        fun centreOf(obj: GameObject): Position {
            val turned = obj.direction == ObjectDirection.NORTH || obj.direction == ObjectDirection.SOUTH
            val width = if (turned) obj.sizeY() else obj.sizeX()
            val length = if (turned) obj.sizeX() else obj.sizeY()
            return obj.position.translate(width / 2, length / 2)
        }
    }

    /**
     * Runs the step for tick [tick] of the attempt. Returns `true` once the attempt is over.
     */
    abstract fun step(tick: Int): Boolean

    override fun onLock() {
        plr.face(centreOf(obj))
    }

    override fun run(): Boolean = step(executions)

    /**
     * Rolls whether [plr] gets past, from their current level in [skill].
     */
    fun passes() = Skills.success(0, 255, plr.skill(skill).level)

    /**
     * Shows every obstacle of [obstacle]'s kind giving way.
     */
    fun giveWay(obstacle: AbyssObstacle) = showLayout(plr, obstacle.giving)

    /**
     * Shows every obstacle of [obstacle]'s kind cleared, and steps [plr] up to the passage.
     */
    fun clear(obstacle: AbyssObstacle) {
        showLayout(plr, obstacle.gone)
        plr.move(passage.enter)
    }

    /**
     * Puts [plr] in the inner ring.
     */
    fun comeOut() = plr.move(passage.inner)

    /**
     * Ends the attempt in the inner ring: puts the outer ring back, gives the experience and sends [message].
     */
    fun finish(message: String? = null) {
        plr.abyssLayout = 0
        showLayout(plr, 0)
        plr.skill(skill).addExperience(OBSTACLE_XP)
        if (message != null) {
            plr.sendMessage(message)
        }
    }
}

/**
 * Mines through a rock, with the best pickaxe [plr] can use.
 */
class MineRock(plr: Player, passage: AbyssPassage, obj: GameObject, val pickaxe: Pickaxe) :
    ObstacleAttempt(plr, passage, obj, Skill.MINING) {
    override fun step(tick: Int) = when (tick) {
        0 -> {
            plr.sendMessage("You attempt to mine your way through...")
            plr.animation(pickaxe.animation)
            plr.playSound(Sound.MINING_3)
            false
        }
        8 -> if (passes()) {
            giveWay(AbyssObstacle.ROCK)
            false
        } else {
            plr.sendMessage("...but fail to break-up the rock.")
            true
        }
        10 -> {
            clear(AbyssObstacle.ROCK)
            false
        }
        13 -> {
            comeOut()
            false
        }
        14 -> {
            finish("...and manage to break through the rock.")
            true
        }
        else -> false
    }
}

/**
 * Chops through tendrils, with the best axe [plr] can use.
 */
class ChopTendrils(plr: Player, passage: AbyssPassage, obj: GameObject) :
    ObstacleAttempt(plr, passage, obj, Skill.WOODCUTTING) {
    private var axe: Axe? = null

    private fun swing() {
        axe?.let { plr.animation(it.animation) }
        plr.playSound(Sound.CUT_TREE_1)
    }

    override fun step(tick: Int) = when (tick) {
        0 -> {
            plr.sendMessage("You attempt to chop your way through...")
            false
        }
        4 -> {
            axe = Axe.computeAxeType(plr)
            if (axe == null) {
                plr.sendMessage("You need an axe to chop through the tendrils.")
                plr.sendMessage("You do not have an axe that you have the Woodcutting level to use.")
                true
            } else {
                swing()
                false
            }
        }
        6 -> if (passes()) {
            swing()
            false
        } else {
            plr.sendMessage("...but fail to cut through the tendrils.")
            true
        }
        8 -> {
            giveWay(AbyssObstacle.TENDRILS)
            swing()
            false
        }
        10 -> {
            clear(AbyssObstacle.TENDRILS)
            false
        }
        12 -> {
            plr.sendMessage("...and manage to cut a way through the tendrils.")
            false
        }
        13 -> {
            comeOut()
            false
        }
        14 -> {
            finish()
            true
        }
        else -> false
    }
}

/**
 * Burns down a boil with a tinderbox.
 */
class BurnBoil(plr: Player, passage: AbyssPassage, obj: GameObject) :
    ObstacleAttempt(plr, passage, obj, Skill.FIREMAKING) {
    override fun step(tick: Int) = when (tick) {
        0 -> {
            plr.sendMessage("You attempt to set the blockade on fire...")
            false
        }
        3 -> if (!plr.inventory.contains(TINDERBOX)) {
            plr.sendMessage("...but you don't have a tinderbox to burn it!")
            true
        } else if (passes()) {
            plr.animation(CREATE_FIRE)
            false
        } else {
            plr.sendMessage("...but fail to burn it out of your way.")
            true
        }
        9 -> {
            giveWay(AbyssObstacle.BOIL)
            plr.animation(CREATE_FIRE)
            false
        }
        15 -> {
            LocalGraphic(ctx, BOIL_BURST, 128, 0, obj.position, ChunkUpdatableView.globalView()).display()
            clear(AbyssObstacle.BOIL)
            plr.playSound(Sound.BOIL_BURST)
            false
        }
        17 -> {
            plr.sendMessage("...and manage to burn it down and get past.")
            false
        }
        18 -> {
            comeOut()
            false
        }
        19 -> {
            finish()
            true
        }
        else -> false
    }
}

/**
 * Distracts the eyes with Thieving and sneaks past.
 */
class DistractEyes(plr: Player, passage: AbyssPassage, obj: GameObject) :
    ObstacleAttempt(plr, passage, obj, Skill.THIEVING) {
    private val distraction = Animation(DISTRACT_ANIMATIONS.random())

    override fun step(tick: Int) = when (tick) {
        0 -> {
            plr.sendMessage("You use your thieving skills to misdirect the eyes...")
            false
        }
        2 -> if (passes()) {
            plr.animation(distraction)
            false
        } else {
            plr.sendMessage("...but fail to distract them enough to get past.")
            true
        }
        4 -> {
            plr.animation(distraction)
            giveWay(AbyssObstacle.EYES)
            false
        }
        6 -> {
            clear(AbyssObstacle.EYES)
            false
        }
        8 -> {
            comeOut()
            plr.sendMessage("...and sneak past while they're not looking.")
            false
        }
        9 -> {
            finish()
            true
        }
        else -> false
    }
}

/**
 * Squeezes through a gap with Agility.
 */
class SqueezeThroughGap(plr: Player, passage: AbyssPassage, obj: GameObject) :
    ObstacleAttempt(plr, passage, obj, Skill.AGILITY) {
    override fun step(tick: Int) = when (tick) {
        0 -> {
            plr.sendMessage("You attempt to squeeze through the narrow gap...")
            false
        }
        2 -> {
            plr.animation(KNEEL)
            false
        }
        6 -> if (passes()) {
            plr.animation(CRAWL)
            plr.sendMessage("...and you manage to crawl through.")
            comeOut()
            plr.playSound(Sound.ABYSSAL_SQUEEZETHROUGH)
            false
        } else {
            plr.animation(KNEEL_AND_STAND)
            plr.sendMessage("...but you are not agile enough to get through the gap.")
            true
        }
        7 -> {
            finish()
            true
        }
        else -> false
    }
}
