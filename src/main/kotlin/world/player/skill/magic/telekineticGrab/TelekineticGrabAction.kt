package world.player.skill.magic.telekineticGrab

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.RepeatingAction
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectGroup
import world.player.Messages
import world.player.Sounds
import world.player.skill.magic.Magic
import world.player.skill.magic.Rune
import world.player.skill.magic.RuneRequirement

/**
 * A [RepeatingAction] implementation that handles the telegrab spell.
 */
class TelekineticGrabAction(plr: Player, private val groundItem: GroundItem) : RepeatingAction<Player>(plr, false, 1) {

    override fun start(): Boolean {
        mob.face(groundItem.position) // TODO do in packet?

        val pathBlockedFunc = { last: Position, _: Direction ->
            val list = world.chunks.getViewableEntities<GameObject>(last, TYPE_OBJECT).filter {
                it.objectType.group == ObjectGroup.WALL && it.position == last // todo figure out how to do properly
            }
            list.isNotEmpty()
        }
        if (!world.collisionManager.raycast(mob.position, groundItem.position, pathBlockedFunc)) {
            mob.sendMessage("I can't reach that!")
            return false
        }
        val item = groundItem.toItem()
        if (!mob.inventory.hasSpaceFor(item)) {
            mob.sendMessage(Messages.INVENTORY_FULL)
            return false
        }
        val removeItems = Magic.checkRequirements(mob, 33, listOf(
            RuneRequirement(Rune.AIR, 1),
            RuneRequirement(Rune.LAW, 1)
        ))
        if (removeItems != null) {
            mob.inventory.removeAll(removeItems)
            mob.magic.addExperience(43.0)
            return true
        }
        return false
    }

    override fun repeat() {
        when (executions) {
            0 -> {
                mob.lock(4)
                mob.graphic(Graphic(142, 100))
                mob.animation(Animation(791))
                mob.playSound(Sounds.TELEGRAB)
            }

            2 -> {
                // todo projectile doesn't work
                val projectile = LocalProjectile.followPath(ctx).setSourcePosition(mob.position)
                    .setTargetPosition(groundItem.position).setDurationTicks(2).setStartHeight(35)
                    .setId(143).build()
                projectile.display()
            }

            4 -> {
                val graphic = LocalGraphic(ctx, 144, 0, 0, groundItem.position, ChunkUpdatableView.globalView())
                graphic.display()

                val item = groundItem.toItem()
                if (groundItem.state == EntityState.INACTIVE) {
                    mob.sendMessage("You were too late!") // todo test
                } else if (!mob.inventory.hasSpaceFor(item)) {
                    mob.sendMessage(Messages.INVENTORY_FULL)
                } else if (world.items.unregister(groundItem)) {
                    mob.inventory.add(item)
                }
                interrupt()
            }
        }
    }

    override fun ignoreIf(other: Action<*>?): Boolean = true
}