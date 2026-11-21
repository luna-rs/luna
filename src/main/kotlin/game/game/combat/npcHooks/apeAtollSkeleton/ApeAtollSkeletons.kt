package game.combat.npcHooks.apeAtollSkeleton

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import game.item.greegree.Greegree
import game.skill.firemaking.Firemaking
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.ObjectType

/**
 * The level 142 skeletons of the Ape Atoll Dungeon. The bones they leave shake on the floor: a while after a player's
 * last kill, a skeleton climbs back out of each pile lying around that player and goes for them. With enough skeletons
 * around already, the bones catch fire instead. The bones crumble to dust when carried out of the dungeon.
 *
 * @author TheLining
 */
object ApeAtollSkeletons {

    /**
     * The skeleton.
     */
    const val SKELETON = 1471

    /**
     * The shaking bones a skeleton leaves.
     */
    const val BONES = 3187

    /**
     * The pile of burning bones that a pile of bones turns into when it can't rise.
     */
    private const val BURNING_BONES = 3862

    /**
     * A skeleton's death played backwards.
     */
    private const val RISE_ANIMATION = 1417

    /**
     * How long [RISE_ANIMATION] takes.
     */
    private const val RISE_TICKS = 1

    /**
     * How long after a player's last kill the bones around them rise.
     */
    private const val RISE_DELAY = 10

    /**
     * How long a risen skeleton lasts, just under two minutes.
     */
    private const val RISEN_TICKS = 195

    /**
     * How far around the player skeletons are counted as a crowd.
     */
    private const val CROWD_RADIUS = 4

    /**
     * How many skeletons around the player stop more from rising.
     */
    private const val CROWD_SIZE = 9

    /**
     * How long a pile of bones burns.
     */
    private const val BURN_TICKS = 16

    /**
     * The Ape Atoll Dungeon.
     */
    private val DUNGEON = Area.of(2688, 9088, 2815, 9151, -1)

    /**
     * The tick of the player's last skeleton kill.
     */
    private var Player.lastSkeletonKill by Attr.long { -1L }

    /**
     * Notes that [plr] killed a skeleton. Each kill puts back the moment the bones around them rise.
     */
    fun killed(plr: Player) {
        val tick = world.currentTick
        if (plr.lastSkeletonKill == tick) {
            return
        }
        plr.lastSkeletonKill = tick
        world.scheduleOnce(RISE_DELAY) { if (plr.lastSkeletonKill == tick) raise(plr) }
    }

    /**
     * Turns the bones [plr] is carrying to ashes once they're out of the dungeon.
     */
    fun crumble(plr: Player) {
        if (!DUNGEON.contains(plr.position) && plr.inventory.replaceAll(BONES, Firemaking.ASHES) > 0) {
            plr.sendMessage("The skeleton bones you are carrying crumble to dust.")
        }
    }

    /**
     * Raises a skeleton from each pile of bones [plr] can see on their tile and the tiles around it.
     */
    private fun raise(plr: Player) {
        if (plr.state != EntityState.ACTIVE || !plr.isAlive || !DUNGEON.contains(plr.position)) {
            return
        }
        val piles = world.locator.findItems(plr, 1) { it.id == BONES && it.isVisibleTo(plr) }
        for (pile in piles) {
            if (crowded(plr)) burn(pile) else rise(pile, plr)
        }
    }

    /**
     * Whether enough skeletons stand around [plr] already.
     */
    private fun crowded(plr: Player): Boolean =
        world.locator.findNpcs(plr, CROWD_RADIUS) { it.id == SKELETON && it.isAlive }.size >= CROWD_SIZE

    /**
     * Puts a skeleton where [pile] lies. It attacks [plr], and falls apart for good when its time is up.
     */
    private fun rise(pile: GroundItem, plr: Player) {
        val skeleton = Npc(plr.context, SKELETON, pile.position)
        skeleton.respawnTicks = -1
        if (!world.npcs.add(skeleton)) {
            return
        }
        world.removeItem(pile)
        skeleton.animation(Animation(RISE_ANIMATION))
        // Held while it climbs out, or its first swing would replace the animation.
        skeleton.lock(RISE_TICKS) {
            if (skeleton.isAlive && plr.state == EntityState.ACTIVE && plr.isAlive && !Greegree.disguises(plr)) {
                skeleton.combat.attack(plr)
            }
        }
        world.scheduleOnce(RISEN_TICKS) {
            if (skeleton.state == EntityState.ACTIVE && skeleton.isAlive) {
                world.npcs.remove(skeleton)
            }
        }
    }

    /**
     * Sets [pile] alight. Nothing is left of it.
     */
    private fun burn(pile: GroundItem) {
        val position = pile.position
        world.removeItem(pile)
        if (blocked(position)) {
            return
        }
        val fire = world.addObject(BURNING_BONES, position)
        world.scheduleOnce(BURN_TICKS) { world.removeObject(fire) }
    }

    /**
     * Whether something already stands on [position].
     */
    private fun blocked(position: Position): Boolean =
        world.objects.findAll(position).anyMatch { it.objectType == ObjectType.DEFAULT }
}
