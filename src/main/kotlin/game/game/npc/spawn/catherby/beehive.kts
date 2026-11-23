package game.npc.spawn.catherby

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * The beehives west of Catherby.
 */
val BEEHIVE = 68

/**
 * Insect repellent.
 */
val INSECT_REPELLENT = 28

/**
 * An empty bucket.
 */
val BUCKET = 1925

/**
 * Whether the bees have left the hives for this player, after they poured insect repellent on one.
 */
var Player.beesGone by Attr.boolean()

/**
 * Drives the bees off with insect repellent.
 */
fun repel(plr: Player) {
    if (plr.beesGone) {
        plr.newDialogue().text("You pour insect repellent on the beehive again.", "The bees have already left.").open()
        return
    }
    plr.beesGone = true
    plr.newDialogue().text("You pour insect repellent on the beehive.", "You see bees leaving the hive.").open()
}

/**
 * Takes wax from the hive into an empty bucket, unless the bees are still there to sting.
 */
fun takeWax(plr: Player) {
    if (!plr.inventory.contains(BUCKET)) {
        plr.newDialogue().text("You'd need a suitable container to carry the wax in.").open()
        return
    }
    if (!plr.beesGone) {
        plr.sendMessage("Suddenly bees fly out of the hive and sting you.")
        plr.damage(2)
        return
    }
    if (plr.inventory.remove(Item(BUCKET))) {
        plr.inventory.add(Item(BlackCandle.BUCKET_OF_WAX))
        plr.beesGone = false
        plr.newDialogue()
            .text("You try to get some wax from the beehive.")
            .text("You get some wax from the hive.")
            .text("The bees fly back to the hive as the repellent wears off.")
            .open()
    }
}

object1(BEEHIVE) { takeWax(plr) }
useItem(BUCKET).onObject(BEEHIVE) { takeWax(plr) }
useItem(INSECT_REPELLENT).onObject(BEEHIVE) { repel(plr) }
for (fire in listOf(590, 594, 33, 32)) {
    useItem(fire).onObject(BEEHIVE) {
        plr.sendMessage("Burning the hive would be a rather extreme measure for repelling bees.")
    }
}
