package game.content.yanilleDungeon

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.skill.agility.yanille.YanilleDungeon
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueUtils

/**
 * Sigbert the Adventurer, who stands beside the rubble in Salarin's corridor.
 */
val SIGBERT = 37

/**
 * The chaos altar beside the chaos druid warriors, which drops anyone praying at it into the spider cave.
 */
val TRAPPED_ALTAR = 412

/**
 * Where the trapdoor drops a player: the spider cave's west end, below the altar.
 */
val TRAPDOOR_LANDING = Position(2571, 9566)

object1(SinisterChest.CLOSED) {
    SinisterChest.open(plr, gameObject)
}

useItem(SinisterChest.KEY).onObject(SinisterChest.CLOSED) {
    SinisterChest.open(plr, gameObject)
}

npc1(SIGBERT) {
    plr.newDialogue()
        .npc(SIGBERT, "I'd be very careful going down there friend.")
        .options("Why what's down there?", { askWhatIsDownThere(it) },
                 "Fear not I am very strong", { boast(it) })
        .open()
}

/**
 * Sigbert warns about Salarin.
 */
fun askWhatIsDownThere(plr: Player) {
    plr.newDialogue()
        .player("Why what's down there ?")
        .npc(SIGBERT, *DialogueUtils.splitChatLines("Salarin the twisted. One of Kandarin's most dangerous chaos " +
            "druids. I tried to take him on, then suddenly felt immensely weak."))
        .npc(SIGBERT, *DialogueUtils.splitChatLines("I hear he's susceptible to attacks from the mind. However I " +
            "have no idea what that means, so it's not much help to me."))
        .open()
}

/**
 * Sigbert doubts the player's strength.
 */
fun boast(plr: Player) {
    plr.newDialogue()
        .player("Fear not I am very strong")
        .npc(SIGBERT, "You might find you are not so strong shortly...")
        .open()
}

// Praying at the altar only springs the trap when the player needs prayer points, and restores none.
object1(TRAPPED_ALTAR) {
    if (plr.isLocked) return@object1
    if (plr.prayer.level >= plr.prayer.staticLevel) {
        plr.sendMessage("You already have full prayer points.")
        return@object1
    }
    plr.submitAction(object : LockedAction(plr) {
        private var ticks = 0

        override fun onLock() {
            mob.animation(Animations.PRAY)
            mob.sendMessage("Suddenly a trap door opens beneath you...")
        }

        override fun run(): Boolean {
            if (++ticks < 2) return false
            mob.move(TRAPDOOR_LANDING)
            mob.speak("Oof!")
            mob.damage(YanilleDungeon.fallDamage(mob))
            return true
        }
    })
}
