package game.content.draynorGuardTree

import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.EntityState
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject

/**
 * The tree with the Draynor bank guard's legs in its branches has dedicated Chop down and Talk to options.
 * These brief authored responses preserve the guard's hiding place without treating it as a resource tree.
 *
 * @author lare96
 */
object DraynorGuardTree {
    const val TREE = 10041

    /**
     * Objects to cutting the guard's hiding place without logs, experience, random events or depletion.
     */
    fun chop(player: Player, tree: GameObject) {
        if (!canInteract(player, tree)) return
        if (Axe.computeAxeType(player) == null) { Axe.sendNoAxeMessages(player); return }
        player.sendMessage("A guard in the tree objects to you chopping his hiding place.")
    }

    /**
     * Explains why the animated tree has a Talk to option without inventing a quest requirement.
     */
    fun talk(player: Player, tree: GameObject) {
        if (!canInteract(player, tree)) return
        player.newDialogue().text("A guard is keeping watch over Draynor bank from the tree.",
            "He asks you not to draw attention to his hiding place.").open()
    }

    private fun canInteract(player: Player, tree: GameObject): Boolean = tree.id == TREE &&
        tree.state == EntityState.ACTIVE && player.state == EntityState.ACTIVE && player.isAlive &&
        !player.isLocked && tree.isVisibleTo(player) &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished } &&
        player.world.collisionManager.reached(player, tree, InteractionPolicy.STANDARD_SIZE)
}
