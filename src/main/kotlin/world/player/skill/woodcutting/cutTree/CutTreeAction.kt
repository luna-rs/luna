package world.player.skill.woodcutting.cutTree

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.ItemContainerAction.AnimatedInventoryAction
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject
import world.player.Sounds
import world.player.skill.woodcutting.Woodcutting
import world.player.skill.woodcutting.Woodcutting.treeHealth
import world.player.skill.woodcutting.searchNest.Nest

/**
 * An [InventoryAction] that will enable the cutting of trees.
 */
class CutTreeAction(plr: Player, val axe: Axe, val tree: Tree, val treeObj: GameObject) :
    AnimatedInventoryAction(plr, axe.speed, 6, Int.MAX_VALUE) {

    /**
     * Sound delay for woodcutting.
     */
    private var soundDelay = -1

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
            if (start) {
                mob.playSound(Sounds.CUT_TREE_1, 40)
                mob.sendMessage("You swing your axe at the tree.")
            }
            true
        }
    }

    override fun execute() {
        if (currentAdd.isNotEmpty()) {
            if (rand().nextInt(256) == 0) {
                val nest = Nest.VALUES.random()
                mob.sendMessage("A bird's nest drops to the floor!")
                world.addItem(nest.id, 1, mob.position, mob)
            } else {
                val name = itemName(tree.logId)
                mob.sendMessage("You get some ${name.lowercase()}.")
                mob.woodcutting.addExperience(tree.exp)

                if (mob.inventory.isFull) {
                    complete()
                }
            }
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

    override fun animation(): Animation {
        mob.playSound(Sounds.CUT_TREE_2)
        soundDelay = 2
        return axe.animation
    }

    override fun add(): List<Item> =
        if (Woodcutting.attemptSuccess(mob.woodcutting.level, tree, axe)) listOf(Item(tree.logId)) else emptyList()

    override fun onFinished() {
        mob.animation(Animation.CANCEL)
    }

    override fun onProcessAnimation() {
        if (soundDelay-- == 0) {
            mob.playSound(Sounds.CUT_TREE_2)
        }
    }

    /**
     * Replaces [treeObj] with a stump and respawns it at a later time.
     */
    private fun deleteAndRespawnTree() {
        if (world.removeObject(treeObj)) {
            mob.playSound(Sounds.TREE_FALLEN)
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