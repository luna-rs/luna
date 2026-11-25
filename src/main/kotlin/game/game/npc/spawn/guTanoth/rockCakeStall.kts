package game.npc.spawn.guTanoth

import api.predef.*
import game.obj.entrances.Entrances
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The ogre trader who guards the rock cake stall.
 */
val STALL_GUARD = 875

/**
 * An ogre trader who attacks anyone who talks to him.
 */
val FALSE_TRADER = 876

/**
 * The rock cake stall with cakes on it, and once emptied.
 */
val CAKE_STALL = 2793
val EMPTY_STALL = 2792

/**
 * A rock cake.
 */
val ROCK_CAKE = 2379

/**
 * Has ogre [npcId] say [lines], then attack [plr].
 */
fun ogreAttacks(plr: Player, npcId: Int, vararg lines: String) {
    val ogre = world.locator.findViewableNpcs(plr) { it.id == npcId }.firstOrNull()
    plr.newDialogue().npc(npcId, Expression.ANGRY, *lines).open()
    ogre?.combat?.attack(plr)
}

npc1(STALL_GUARD) {
    ogreAttacks(plr, STALL_GUARD, "Arr, small thing wants my food, does it?", "I'll teach you to deal with ogres!")
}
npc2(STALL_GUARD) {
    ogreAttacks(plr, STALL_GUARD, "Arr, small thing wants my food, does it?", "I'll teach you to deal with ogres!")
}
npc1(FALSE_TRADER) { ogreAttacks(plr, FALSE_TRADER, "Grrr, little animal; I shall destroy you!") }
npc2(FALSE_TRADER) { ogreAttacks(plr, FALSE_TRADER, "Trade with me? Who do you think you are?") }

object1(CAKE_STALL) {
    when {
        plr.thieving.level < 15 ->
            plr.newDialogue().text("You need a Thieving level of 15 to steal from this stall.").open()
        world.locator.findNpcs(plr.position, 3) { it.id == STALL_GUARD }.isNotEmpty() ->
            ogreAttacks(plr, STALL_GUARD, "Grr! Get your hands off those cakes!")
        plr.inventory.isFull ->
            plr.sendMessage("You don't have enough inventory space to take a rockcake.")
        Entrances.replace(gameObject, EMPTY_STALL, 12) -> {
            plr.sendMessage("You cautiously grab a cake from the stall.")
            plr.inventory.add(Item(ROCK_CAKE))
            plr.thieving.addExperience(6.5)
        }
    }
}
