package game.skill.thieving.pickpocketNpc

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Messages
import game.player.Sound
import game.skill.thieving.Thieving
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.task.Task

/**
 * An attempt to pickpocket a specific [Npc].
 *
 * @param mob The player attempting to pickpocket the NPC.
 * @param target The NPC being pickpocketed.
 * @param thievable The pickpocket definition used to determine level requirements, loot, experience, stun duration, and success chance.
 * @author lare96
 */
class PickpocketAction(val mob: Player, val target: Npc, val thievable: ThievingNpcType) {

    companion object {

        /**
         * Ticks between pickpocket attempts.
         */
        const val DELAY = 2

        /**
         * Tracks the last time a player attempted a pickpocket action, to keep attempts [DELAY] ticks apart.
         */
        val Player.lastPickpocket by Attr.timeSource()

        /**
         * Starts a pickpocket attempt from a click: on the tick the click is handled once [DELAY] ticks have passed
         * since the last attempt, otherwise as soon as they have. Only the latest click waits.
         */
        fun start(plr: Player, target: Npc, thievable: ThievingNpcType) {
            val attempt = PickpocketAction(plr, target, thievable)
            val source = plr.lastPickpocket
            if (source.ready(DELAY)) {
                source.cancelQueuedTask()
                attempt.execute()
            } else {
                val wait = (DELAY - source.durationTicks).toInt().coerceAtLeast(1)
                source.startQueuedTask(object : Task(false, wait) {
                    override fun execute() {
                        cancel()
                        if (plr.state == EntityState.ACTIVE && plr.isWithinDistance(target, 1) && source.ready(DELAY)) {
                            attempt.execute()
                        }
                    }
                })
            }
        }
    }

    /**
     * Makes the attempt: the attempt line now, the result on the next tick.
     */
    fun execute() {
        val pocket = thievable.pocket(target)
        if (mob.status.isStunned()) {
            // A click already on its way when the stun lands still reaches here.
            mob.sendMessage("You're stunned!")
            return
        } else if (mob.thieving.level < thievable.level) {
            mob.sendMessage("You need to be a level ${thievable.level} thief to pick the $pocket's pocket.")
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
        mob.sendMessage("You attempt to pick the $pocket's pocket.")

        world.scheduleOnce(1) {
            if (mob.state != EntityState.INACTIVE && target.state != EntityState.INACTIVE) {
                if (Thieving.canPickpocket(mob, thievable)) {
                    mob.sendMessage("You pick the $pocket's pocket.")
                    if (thievable == ThievingNpcType.MASTER_FARMER) {
                        loot.forEach { mob.sendMessage("You steal ${it.amount} ${seedName(it, it.amount > 1)}.") }
                    }
                    mob.thieving.addExperience(thievable.xp)
                    mob.animation(Animations.PICKPOCKET)
                    mob.inventory.addAll(loot)

                    if (loot.isNotEmpty()) {
                        mob.playSound(Sound.PICKUP_ITEM)
                    }

                    Thieving.rollRogueEquipment(mob, target)
                } else {
                    caught(pocket)
                }
            }
        }
    }

    /**
     * The name of a seed in "You steal 3 barley seeds.": hop and bush seeds are named as such, like "Yanillian hop
     * seed" and "cadavaberry bush seed".
     */
    private fun seedName(item: Item, plural: Boolean): String {
        val name = item.itemDef.name.removeSuffix(" seed")
        val full = when (name) {
            "Hammerstone", "Asgarnian", "Yanillian", "Krandorian", "Wildblood" -> "$name hop seed"
            "Redberry", "Cadavaberry", "Dwellberry", "Jangerberry", "Whiteberry", "Poison ivy" ->
                "${name.lowercase()} bush seed"
            else -> item.itemDef.name.lowercase()
        }
        return if (plural) "${full}s" else full
    }

    /**
     * Handles a failed pickpocket attempt.
     *
     * The target shouts and swings once without starting combat. On the next tick the player takes the target's
     * fixed damage and is stunned for [ThievingNpcType.stunTicks].
     */
    private fun caught(pocket: String) {
        target.speak(thievable.shout)
        mob.sendMessage("You fail to pick the $pocket's pocket.")
        target.interact(mob)
        target.animation(Animation(target.combat.getAttackAnimation(CombatDamageType.MELEE),
                                   Animation.AnimationPriority.HIGH))

        world.scheduleOnce(1) {
            if (mob.state == EntityState.INACTIVE) {
                return@scheduleOnce
            }
            val defenceAnimation = mob.combat.getDefenceAnimation(CombatDamageType.MELEE, thievable.damage)
            mob.damage(thievable.damage)
            if (defenceAnimation > 0) {
                mob.animation(Animation(defenceAnimation))
            }
            mob.playSound(Sound.STUNNED)
            mob.sendMessage("You've been stunned!")
            mob.status.add(PickpocketStun(mob, thievable.stunTicks))
        }
    }
}
