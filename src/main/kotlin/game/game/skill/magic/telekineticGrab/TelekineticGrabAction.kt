package game.skill.magic.telekineticGrab

import api.predef.*
import game.content.randomEvents.RandomEvents
import game.content.treasureTrails.TreasureTrails
import game.player.Messages
import game.player.Sound
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic

/**
 * Telegrabs a ground item through the native locked action. Initial reach, clue, inventory and spell
 * requirements are checked before locking; a ready general random event can interrupt before rune costs
 * or experience apply. The delayed pickup still rechecks the ground item, clue ownership and space.
 *
 * @author lare96
 */
class TelekineticGrabAction(plr: Player, private val groundItem: GroundItem) : LockedAction(plr) {

    companion object {
        /**
         * The regular-spellbook widget used for native Telekinetic Grab packets.
         */
        const val SPELL_ID = 1168

        /**
         * The minimum Magic level checked by the player action and bot cost planning.
         */
        const val LEVEL = 33

        /**
         * Native rune requirements; Magic resolves equipped staffs and combination runes for each cast.
         */
        val REQUIREMENTS = listOf(RuneRequirement(Rune.AIR, 1), RuneRequirement(Rune.LAW, 1))
    }

    /**
     * Rune costs validated immediately before this submission acquires its lock, without a delayed handoff.
     */
    private var preparedCosts: List<Item>? = null

    override fun beforeLock(): Boolean {
        preparedCosts = checkCast() ?: return false
        return !RandomEvents.offerGeneral(mob)
    }

    override fun onLock() {
        val removeItems = preparedCosts ?: checkCast()
        if (removeItems == null) {
            complete()
            return
        }
        mob.inventory.removeAll(removeItems)
        mob.magic.addExperience(43.0)
    }

    /**
     * Performs the native initial checks before either an event offer or the spell's rune consumption.
     */
    private fun checkCast(): List<Item>? {
        mob.walking.clear()
        mob.face(groundItem.position)
        if (!world.collisionManager.raycast(mob.position, groundItem.position)) {
            mob.sendMessage("I can't reach that!")
            return null
        }
        val item = groundItem.toItem()
        if (!TreasureTrails.canAcquire(mob, item.id)) {
            return null
        }
        if (!mob.inventory.hasSpaceFor(item)) {
            mob.sendMessage(Messages.inventoryFull())
            return null
        }
        return Magic.checkRequirements(mob, LEVEL, REQUIREMENTS)
    }


    override fun run(): Boolean =
        when (executions) {
            0 -> {
                mob.graphic(Graphic(142, 100))
                mob.animation(Animation(791))
                mob.playSound(Sound.TELEGRAB)
                false
            }

            1 -> false
            2 -> {
                val projectile = LocalProjectile.followPath(ctx)
                    .setSourcePosition(mob.position)
                    .setTargetPosition(groundItem.position)
                    .setId(143)
                    .setStartHeight(35)
                    .setEndHeight(10)
                    .setTicksToEnd(45)
                    .setTicksToStart(0)
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
                } else if (!TreasureTrails.canAcquire(mob, item.id)) {
                    // Another clue may have been acquired while the projectile was travelling.
                } else if (!mob.inventory.hasSpaceFor(item)) {
                    mob.sendMessage(Messages.inventoryFull())
                } else if (world.items.unregister(groundItem)) {
                    mob.inventory.add(item)
                    TreasureTrails.acquired(mob, item.id)
                }
                true
            }

            else -> true
        }
}
