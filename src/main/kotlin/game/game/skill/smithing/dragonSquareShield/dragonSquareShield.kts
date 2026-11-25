package game.skill.smithing.dragonSquareShield

import api.predef.*
import game.player.Sound
import game.skill.smithing.Smithing
import game.skill.smithing.Smithing.DRAGON_SHIELD_LEFT_HALF as LEFT_HALF
import game.skill.smithing.Smithing.DRAGON_SHIELD_LEVEL as LEVEL
import game.skill.smithing.Smithing.DRAGON_SHIELD_RIGHT_HALF as RIGHT_HALF
import game.skill.smithing.Smithing.DRAGON_SQUARE_SHIELD
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * Repairs the two shield halves at an anvil, after six ticks of hammering.
 */
fun repair(plr: Player) {
    when {
        !plr.inventory.contains(LEFT_HALF) || !plr.inventory.contains(RIGHT_HALF) ->
            plr.newDialogue().text("You need to find the other half of this shield in order to repair it.").open()
        plr.smithing.level < LEVEL ->
            plr.newDialogue().text("You need a Smithing level of at least $LEVEL to repair this shield.").open()
        !plr.inventory.contains(Smithing.HAMMER) ->
            plr.newDialogue().text("You'll need a hammer if you want to attempt to repair this shield.").open()
        else -> {
            plr.newDialogue().text("You set to work trying to fix the ancient shield. It's seen some heavy action and " +
                                       "needs some serious work doing to it.").open()
            plr.submitAction(object : LockedAction(plr, false, 6) {
                override fun onLock() {
                    mob.walking.clear()
                    mob.animation(Animation(898))
                    mob.playSound(Sound.ANVIL_4)
                }

                override fun run(): Boolean {
                    if (mob.inventory.remove(Item(LEFT_HALF)) && mob.inventory.remove(Item(RIGHT_HALF))) {
                        mob.inventory.add(Item(DRAGON_SQUARE_SHIELD))
                        mob.smithing.addExperience(75.0)
                        mob.newDialogue().text("Even for an experienced armourer it is not an easy task, but " +
                                                   "eventually it is ready. You have restored the dragon square " +
                                                   "shield to it's former glory.").open()
                    }
                    return true
                }
            })
        }
    }
}

for (anvil in Smithing.ANVIL_OBJECTS) {
    useItem(LEFT_HALF).onObject(anvil) { repair(plr) }
    useItem(RIGHT_HALF).onObject(anvil) { repair(plr) }
}

useItem(LEFT_HALF).onItem(RIGHT_HALF) {
    plr.newDialogue().text("You'll need to repair the shield at an anvil, with a hammer.").open()
}
