package world.player.skill.thieving.pickpocketNpc

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.QueuedAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.block.Hit
import world.player.Animations
import world.player.Messages
import world.player.Sounds
import world.player.skill.thieving.Thieving
import java.time.Duration


/**
 * A [QueuedAction] implementation that enables pickpocketing [Npc] types.
 */
class PickpocketAction(plr: Player, val target: Npc, val thievable: ThievableNpc) :
    QueuedAction<Player>(plr, plr.lastPickpocket, 2) {

    companion object {

        /**
         * The time source for the last pickpocket by the player.
         */
        val Player.lastPickpocket by Attr.timeSource()
    }

    override fun execute() {
        if (mob.thieving.level < thievable.level) {
            mob.sendMessage("You need a Thieving level of ${thievable.level} to pickpocket ${addArticle(target.definition.name)}.")
            return
        } else if (target.state == EntityState.INACTIVE) {
            return
        }
        val rolls = if (Thieving.isDoubleLoot(mob)) 2 else 1
        val loot = ArrayList<Item>()
        repeat(rolls) {
            loot += thievable.drops.roll(mob, target)
        }
        if (!mob.inventory.hasSpaceForAll(loot)) {
            mob.sendMessage(Messages.INVENTORY_FULL)
            return
        }

        mob.interact(target)
        world.scheduleOnce(1) {
            if (mob.state != EntityState.INACTIVE && target.state != EntityState.INACTIVE) {
                if (Thieving.canPickpocket(mob, thievable)) {
                    mob.sendMessage("You pick the ${target.definition.name}'s pocket.")
                    mob.thieving.addExperience(thievable.xp)
                    mob.animation(Animations.PICKPOCKET)
                    mob.inventory.addAll(loot)
                    if (loot.isNotEmpty()) {
                        mob.playSound(Sounds.PICKUP_ITEM)
                    }
                    Thieving.rollRogueEquipment(mob, target)
                } else {
                    val stunDuration = Duration.ofSeconds(thievable.stun).toTicks()
                    val hit = Hit(thievable.damage.random())

                    mob.playSound(Sounds.PICKPOCKET_FAILED)
                    mob.sendMessage("You have been stunned.");
                    mob.animation(Animation(424))
                    mob.graphic(Graphic(80, 5, 60))
                    mob.damage(hit)
                    target.animation(Animation(422))
                    target.interact(mob)
                    target.forceChat("What do you think you're doing?!")
                    mob.lock(stunDuration)
                    world.scheduleOnce(stunDuration - 1) {
                        mob.graphic(Graphic.NULL)
                    }
                }
            }
        }
    }
}
