package game.skill.woodcutting.cutTree

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.woodcutting.Woodcutting
import game.skill.woodcutting.searchNest.Nest
import io.luna.Luna
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * An [InventoryAction] that will enable the cutting of trees. The swing comes on the tick after the click, the first
 * attempt two ticks after the swing, then one attempt every `woodcutting_speed` ticks, each restarting the animation.
 */
class CutTreeAction(plr: Player, val axe: Axe, val tree: Tree, val treeObj: GameObject) :
    InventoryAction(plr, false, FIRST_ATTEMPT_DELAY, Int.MAX_VALUE) {

    companion object {

        /**
         * The number of ticks between the click and the first attempt to cut a log.
         */
        const val FIRST_ATTEMPT_DELAY = 3
    }

    /**
     * Sound delay for woodcutting.
     */
    private var soundDelay = -1

    /**
     * If the last free inventory slot was just filled, so that chopping stops on the next tick.
     */
    private var stopFull = false

    /**
     * If the swing at the start has happened.
     */
    private var swung = false

    /**
     * The bird's nest that falls instead of the log on this attempt, if any.
     */
    private var nest: Nest? = null

    override fun executeIf(start: Boolean) = when {
        mob.inventory.isFull -> {
            // No inventory space.
            mob.sendMessage(onInventoryFull())
            false
        }

        mob.woodcutting.level < tree.level -> {
            // Check if we have required level.
            mob.sendMessage("You need a Woodcutting level of ${tree.level} to chop down this tree.")
            false
        }

        // Check if tree isn't already cut.
        treeObj.state == EntityState.INACTIVE -> false

        !Axe.hasAxe(mob, axe) -> {
            Axe.sendNoAxeMessages(mob)
            false
        }

        else -> {
            if (!start) {
                delay = Luna.settings().skills().woodcuttingSpeed()
                swing(true)
            }
            true
        }
    }

    override fun execute() {
        val droppedNest = nest
        if (droppedNest != null) {
            nest = null
            mob.sendMessage("A bird's nest falls out of the tree.")
            world.addItem(droppedNest.id, 1, nestTile(), mob)
        } else if (currentAdd.isNotEmpty()) {
            val name = itemName(tree.logId)
            mob.sendMessage("You get some ${name.lowercase()}.")
            mob.woodcutting.addExperience(tree.exp)

            if (mob.inventory.isFull) {
                stopFull = true
            }
        } else {
            return
        }
        if (Woodcutting.fallsAfterLog(tree)) {
            deleteAndRespawnTree()
            complete()
        }
    }

    override fun add(): List<Item> {
        if (!Woodcutting.success(mob.woodcutting.level, tree, axe)) {
            return emptyList()
        }
        // Normal trees never give nests; on other trees a nest takes the place of the log.
        nest = if (tree != Tree.NORMAL && rand().nextInt(256) == 0) Nest.roll() else null
        return if (nest == null) listOf(Item(tree.logId)) else emptyList()
    }

    override fun onFinished() {
        mob.animation(Animation.CANCEL)
    }

    override fun onInventoryFull() = "Your inventory is too full to hold any more logs."

    override fun onProcess() {
        if (stopFull) {
            mob.sendMessage(onInventoryFull())
            complete()
            return
        }
        if (!swung) {
            swung = true
            mob.playSound(Sound.CUT_TREE_1)
            mob.sendMessage("You swing your axe at the tree.")
            swing(false)
        }
        if (soundDelay-- == 0) {
            mob.playSound(Sound.CUT_TREE_2)
        }
    }

    /**
     * Faces the tree and starts the chopping animation, with the chopping sound if [sound] is true.
     */
    private fun swing(sound: Boolean) {
        mob.interactingWith?.let { mob.interact(it) }
        mob.animation(axe.animation)
        if (sound) {
            mob.playSound(Sound.CUT_TREE_2)
            soundDelay = 2
        }
    }

    /**
     * Picks a random tile next to the player that they could step onto, for a bird's nest to land on, or the player's
     * own tile if there is none. Jagex describes a bias to the west but not its size, so every direction is equally
     * likely.
     */
    private fun nestTile(): Position =
        Direction.ALL_EXCEPT_NONE.filter { world.collisionManager.traversable(mob.position, EntityType.PLAYER, it) }
            .randomOrNull()?.let { mob.position.translate(1, it) } ?: mob.position

    /**
     * Replaces [treeObj] with a stump and respawns it at a later time.
     */
    private fun deleteAndRespawnTree() {
        if (world.removeObject(treeObj)) {
            mob.playSound(Sound.TREE_FALLEN)
            val stumpId = TreeStump.TREE_ID_MAP[treeObj.id]?.stumpId
            if (stumpId != null) {
                world.addObject(
                    stumpId,
                    treeObj.position,
                    treeObj.objectType,
                    treeObj.direction)
            }
            world.scheduleOnce(tree.respawnTicks.random()) {
                world.addObject(
                    treeObj.id,
                    treeObj.position,
                    treeObj.objectType,
                    treeObj.direction)
            }
        }
    }
}