package game.skill.magic.telekineticGrab

import api.predef.*
import game.player.Messages
import game.player.Sounds
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic

/**
 * A [LockedAction] implementation that handles the telegrab spell.
 *
 * @author lare96
 */
class TelekineticGrabAction(plr: Player, private val groundItem: GroundItem) : LockedAction(plr) {

    override fun onLock() {
        mob.walking.clear()
        mob.face(groundItem.position)
        if (!world.collisionManager.raycast(mob.position, groundItem.position)) {
            mob.sendMessage("I can't reach that!")
            complete()
            return
        }
        val item = groundItem.toItem()
        if (!mob.inventory.hasSpaceFor(item)) {
            mob.sendMessage(Messages.INVENTORY_FULL)
            complete()
            return
        }
        val removeItems = Magic.checkRequirements(mob, 33, listOf(
            RuneRequirement(Rune.AIR, 1),
            RuneRequirement(Rune.LAW, 1)
        ))
        if (removeItems == null) {
            complete()
            return
        }
        mob.inventory.removeAll(removeItems)
        mob.magic.addExperience(43.0)
    }


    override fun run(): Boolean =
        when (executions) {
            0 -> {
                mob.graphic(Graphic(142, 100))
                mob.animation(Animation(791))
                mob.playSound(Sounds.TELEGRAB)
                false
            }

            1 -> false
            2 -> {
                val projectile = LocalProjectile.followPath(ctx)
                    .setSourcePosition(mob.position)
                    .setTargetPosition(groundItem.position)
                    .setId(143)
                    .setStartHeight(35)
                    .setEndHeight(0)
                    .setTicksToStart(45)
                    .setTicksToEnd(0)
                    .setInitialSlope(10)
                    .build()
                projectile.display()
                false
            }

            3 -> false
            4 -> {
                val graphic = LocalGraphic(ctx, 144, 0, 0, groundItem.position, ChunkUpdatableView.globalView())
                graphic.display()

                val item = groundItem.toItem()
                if (groundItem.state == EntityState.INACTIVE) {
                    mob.sendMessage("You were too late!")
                } else if (!mob.inventory.hasSpaceFor(item)) {
                    mob.sendMessage(Messages.INVENTORY_FULL)
                } else if (world.items.unregister(groundItem)) {
                    mob.inventory.add(item)
                }
                true
            }

            else -> true
        }
}