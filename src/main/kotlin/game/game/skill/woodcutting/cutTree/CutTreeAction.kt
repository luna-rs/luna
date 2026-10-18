package game.skill.woodcutting.cutTree

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.woodcutting.Woodcutting
import game.skill.woodcutting.Woodcutting.treeHealth
import game.skill.woodcutting.searchNest.Nest
import io.luna.Luna
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.EntityState
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

    override fun executeIf(start: Boolean) = when {
        mob.inventory.isFull -> {
            // No inventory space.
            mob.sendMessage(onInventoryFull())
            false
        }

        mob.woodcutting.level < tree.level -> {
            // Check if we have required level.
            mob.sendMessage("You need a Woodcutting level of ${tree.level} to cut this.")
            false
        }

        // Check if tree isn't already cut.
        treeObj.state == EntityState.INACTIVE -> false

        !Axe.hasAxe(mob, axe) -> {
            mob.sendMessage("You need an axe to chop this tree.")
            mob.sendMessage("You do not have an axe that you have the woodcutting level to use.")
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
        if (currentAdd.isNotEmpty()) {
            if (rand(256) == 0) {
                val nest = Nest.VALUES.random()
                mob.sendMessage("A bird's nest drops to the floor!")
                world.addItem(nest.id, 1, mob.position, mob)
            } else {
                val name = itemName(tree.logId)
                mob.sendMessage("You get some ${name.lowercase()}.")
                mob.woodcutting.addExperience(tree.exp)

                if (mob.inventory.isFull) {
                    stopFull = true
                }
            }
            if (Luna.settings().skills().use317TreeStumps()) {
                if (rand().nextInt(8) == 0) {
                    deleteAndRespawnTree()
                    complete()
                }
            } else {
                if (treeObj.treeHealth == -1) {
                    // Initialize health for trees that haven't been cut.
                    treeObj.treeHealth = tree.maxHealth.random()
                }
                if (--treeObj.treeHealth <= 0) {
                    deleteAndRespawnTree()
                    complete()
                }
            }
        }
    }

    override fun add(): List<Item> =
        if (Woodcutting.success(mob.woodcutting.level, tree, axe)) listOf(Item(tree.logId)) else emptyList()

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
            world.scheduleOnce(tree.respawnTicks) {
                val newTreeObj = world.addObject(
                    treeObj.id,
                    treeObj.position,
                    treeObj.objectType,
                    treeObj.direction)
                newTreeObj.treeHealth = tree.maxHealth.random()
            }
        }
    }
}