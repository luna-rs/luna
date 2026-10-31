package game.skill.farming.tree

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.farming.*
import game.skill.woodcutting.Woodcutting
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.CutTreeAction
import game.skill.woodcutting.cutTree.Tree
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

/**
 * An [InventoryAction] that chops down a fully grown tree in a tree patch. It follows [CutTreeAction] and reuses the
 * woodcutting plugin's [Axe], [Tree] and [Woodcutting] rules, with the same timing, sounds and messages. A farmed tree
 * is drawn from the player's own patch state instead of a world object, so when it falls the patch turns into a stump
 * that grows back after the tree's [Tree.respawnTicks].
 *
 * @author hydrozoa
 */
class ChopPatchTreeAction(plr: Player, val axe: Axe, val patch: TreePatch, sapling: TreeSapling) :
    InventoryAction(plr, false, CutTreeAction.FIRST_ATTEMPT_DELAY, Int.MAX_VALUE) {

    /**
     * The tree being chopped down.
     */
    private val tree = sapling.tree

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

        // Check if the tree isn't already a stump.
        !patch.canChop() -> false

        !Axe.hasAxe(mob, axe) -> {
            Axe.sendNoAxeMessages(mob)
            false
        }

        else -> {
            if (!start) {
                delay = Luna.settings().skills().woodcuttingSpeed()
                swing()
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
            fellTree()
            complete()
        }
    }

    override fun add(): List<Item> {
        if (!Woodcutting.success(mob.woodcutting.level, tree, axe)) {
            return emptyList()
        }
        // A nest takes the place of the log.
        nest = if (rand().nextInt(256) == 0) Nest.roll() else null
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
            mob.sendMessage("You swing your axe at the tree.")
            swing()
        }
    }

    /**
     * Faces the tree and starts the chopping animation and sound.
     */
    private fun swing() {
        mob.interactingWith?.let { mob.interact(it) }
        mob.animation(axe.animation)
        mob.playSound(Sound.CUT_TREE_2)
    }

    /**
     * Picks a random tile next to the player that they could step onto, for a bird's nest to land on, or the player's
     * own tile if there is none.
     */
    private fun nestTile(): Position =
        Direction.ALL_EXCEPT_NONE.filter { world.collisionManager.traversable(mob.position, EntityType.PLAYER, it) }
            .randomOrNull()?.let { mob.position.translate(1, it) } ?: mob.position

    /**
     * Turns the patch into a stump, and brings the player's patches up to date once it has grown back so the tree is
     * drawn again without waiting for [UpdateFarmsTask].
     */
    private fun fellTree() {
        val plr = mob
        val regrowTicks = tree.respawnTicks.random()
        plr.playSound(Sound.TREE_FALL)
        patch.fell(System.currentTimeMillis(), regrowTicks)
        Farming.commit(plr)
        world.scheduleOnce(regrowTicks + 1) {
            if (plr.state == EntityState.ACTIVE) {
                Farming.update(plr)
            }
        }
    }
}
