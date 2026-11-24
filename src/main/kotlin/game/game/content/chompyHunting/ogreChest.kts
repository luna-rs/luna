package game.content.chompyHunting

import api.predef.*
import api.predef.ext.*
import game.content.chompyHunting.ChompyHunting.ALL_BELLOWS
import game.content.chompyHunting.ChompyHunting.EMPTY_BELLOWS
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * The ogre chest in Rantz's cave, held shut by a huge rock.
 */
val LOCKED_CHEST = 3377

/**
 * The ogre chest with its rock lifted off.
 */
val UNLOCKED_CHEST = 3378

/**
 * How long the chest stays open before the rock is back on it.
 */
val OPEN_TICKS = 50

val PICKUP_TABLE_ANIMATION = 832

object1(LOCKED_CHEST) {
    // TODO Big Chompy Bird Hunting: before Rantz's children have played with the toad this says "Perhaps you'd
    //  better ask permission before opening this." Until the quest exists every player has finished it.
    val chest = gameObject
    plr.speak("Humph!")
    plr.animation(Animation(PICKUP_TABLE_ANIMATION))
    plr.lock(1) {
        if (rand(2) == 0) {
            plr.sendMessage("You strain to lift the huge rock off the chest...")
            plr.lock(2) {
                plr.sendMessage("... but it's just too heavy for you.")
                plr.sendMessage("The experience has left you feeling temporarily weakened.")
                plr.speak("Arrgghhhhh!")
                val strength = plr.skill(Skill.STRENGTH)
                strength.level = (strength.level - 1).coerceAtLeast(0)
            }
            return@lock
        }
        plr.sendMessage("You manage to lift the huge rock off the chest.")
        plr.speak("I guess this is what an ogre would call a locked chest.")
        openChest(chest)
    }
}

object1(UNLOCKED_CHEST) {
    plr.animation(Animation(PICKUP_TABLE_ANIMATION))
    when {
        ALL_BELLOWS.any { plr.inventory.contains(it) || plr.bank.contains(it) } ->
            plr.sendMessage("You search but find nothing in the ogre chest.")
        plr.inventory.computeRemainingSize() < 1 ->
            plr.sendMessage("You find some ogre bellows in the chest but do not have the space to take them.")
        else -> {
            plr.inventory.add(Item(EMPTY_BELLOWS))
            plr.newDialogue()
                .give(Item(EMPTY_BELLOWS), "You search the chest and find a pair of ogre bellows.", false)
                .open()
        }
    }
}

/**
 * Swaps the chest for the open one, and puts the rock back after [OPEN_TICKS].
 */
fun openChest(chest: GameObject) {
    if (world.objects.findAll(chest.position).noneMatch { it === chest }) return
    val view = ChunkUpdatableView.globalView()
    val open = GameObject.createDynamic(ctx, UNLOCKED_CHEST, chest.position, chest.objectType, chest.direction, view)
    if (world.objects.register(open)) {
        world.scheduleOnce(OPEN_TICKS) {
            world.objects.register(GameObject.createDynamic(ctx, LOCKED_CHEST, chest.position, chest.objectType,
                                                            chest.direction, view))
        }
    }
}
