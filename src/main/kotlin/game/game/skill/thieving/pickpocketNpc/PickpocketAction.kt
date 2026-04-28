package game.skill.thieving.pickpocketNpc

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import engine.combat.status.hooks.StunnedStatusEffect
import game.player.Animations
import game.player.Messages
import game.player.Sound
import game.skill.thieving.Thieving
import io.luna.game.action.impl.LockedAction
import io.luna.game.action.impl.QueuedAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import kotlin.time.Duration.Companion.seconds

/**
 * A queued player action that attempts to pickpocket a specific [Npc].
 *
 * The action validates the player's Thieving level, checks inventory space for the possible loot,
 * turns the player toward the target, then resolves the pickpocket attempt after a short delay.
 * On success, the player receives loot and experience. On failure, the target performs a single
 * retaliation swing and the player is stunned.
 *
 * @param plr The player attempting to pickpocket the NPC.
 * @param target The NPC being pickpocketed.
 * @param thievable The pickpocket definition used to determine level requirements, loot, experience, stun duration, and success chance.
 * @author lare96
 */
class PickpocketAction(plr: Player, val target: Npc, val thievable: ThievableNpc) :
    QueuedAction<Player>(plr, plr.lastPickpocket, 2) {

    companion object {

        /**
         * Tracks the last time a player attempted a pickpocket action.
         *
         * This is used by [QueuedAction] to throttle repeated pickpocket attempts and prevent the action from being
         * spammed faster than the configured queue delay.
         */
        val Player.lastPickpocket by Attr.timeSource()
    }

    override fun execute() {
        if (mob.thieving.level < thievable.level) {
            mob.sendMessage("You need a Thieving level of ${thievable.level} to pickpocket ${addArticle(target.def().name)}.")
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
            mob.sendMessage(Messages.inventoryFull())
            return
        }

        mob.interact(target)

        world.scheduleOnce(1) { // TODO@0.5.0 This should be an action? Also only calculate drop on success.
            if (mob.state != EntityState.INACTIVE && target.state != EntityState.INACTIVE) {
                if (Thieving.canPickpocket(mob, thievable)) {
                    mob.sendMessage("You pick the ${target.def().name}'s pocket.")
                    mob.thieving.addExperience(thievable.xp)
                    mob.animation(Animations.PICKPOCKET)
                    mob.inventory.addAll(loot)

                    if (loot.isNotEmpty()) {
                        mob.playSound(Sound.PICKUP_ITEM)
                    }

                    Thieving.rollRogueEquipment(mob, target)
                } else {
                    stun()
                }
            }
        }
    }

    /**
     * Handles a failed pickpocket attempt.
     *
     * The target performs a single melee retaliation swing without starting normal combat. The resolved damage is
     * applied to the player inside a short [LockedAction], then the player is stunned for the configured duration
     * from [thievable].
     */
    private fun stun() {
        // Do a single attack swing, but don't initiate combat.
        val attackAnimation = Animation(
            target.combat.getAttackAnimation(CombatDamageType.MELEE),
            Animation.AnimationPriority.HIGH
        )
        val damage = CombatDamageRequest.standard(target, mob, CombatDamageType.MELEE).resolve()

        target.speak("What do you think you're doing?!")
        target.interact(mob)
        target.animation(attackAnimation)

        mob.submitAction(object : LockedAction(mob, true, 1) {
            override fun run(): Boolean {
                val defenceAnimation = Animation(mob.combat.getDefenceAnimation(CombatDamageType.MELEE))

                mob.damage(damage.rawAmount)
                mob.animation(defenceAnimation)

                // Additionally, stun the player.
                mob.playSound(Sound.STUNNED)
                mob.status.add(StunnedStatusEffect(mob, thievable.stun.seconds))
                return true
            }
        })
    }
}