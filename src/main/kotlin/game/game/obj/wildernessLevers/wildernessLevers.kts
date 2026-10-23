package game.obj.wildernessLevers

import api.predef.*
import api.predef.ext.*
import game.obj.wildernessLevers.WildernessLever.Companion.skipWildernessLeverWarning
import game.player.Sound
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * A wall lever after it's pulled down.
 */
val PULLED_LEVER = 161

/**
 * How many ticks a pulled wall lever stays down.
 */
val PULLED_TICKS = 7

/**
 * Swaps [lever] for a pulled-down lever for [PULLED_TICKS]. Does nothing if someone else has already pulled it.
 */
fun pullDown(lever: GameObject) {
    if (!world.removeObject(lever)) {
        return
    }
    val pulled = world.addObject(PULLED_LEVER, lever.position, lever.objectType, lever.direction)
    world.scheduleOnce(PULLED_TICKS) {
        if (world.removeObject(pulled)) {
            world.addObject(lever.id, lever.position, lever.objectType, lever.direction)
        }
    }
}

/**
 * [plr] pulls [lever] and is teleported to its destination. Levers work at any Wilderness level, but not while Tele
 * Blocked.
 */
fun pull(plr: Player, lever: GameObject, type: WildernessLever) {
    plr.submitAction(object : LockedAction(plr) {
        var pulled = false

        override fun run(): Boolean {
            if (executions == 0) {
                plr.animation(type.pullAnimation)
                plr.playSound(Sound.LEVER)
                if (type.leverAnimation != null) {
                    lever.animate(type.leverAnimation)
                } else {
                    pullDown(lever)
                }
            }
            if (executions == type.messageDelay) {
                plr.sendMessage("You pull the lever...")
                pulled = true
                return true
            }
            return false
        }

        // The teleport locks the player again, so it can only start once this action has unlocked them.
        override fun onUnlock() {
            if (pulled) {
                plr.teleport(type.destination, TeleportStyle.REGULAR, maxWildernessLevel = Int.MAX_VALUE,
                             onLand = { plr.sendMessage(type.arrivalMessage) })
            }
        }
    })
}

/**
 * Warns [plr] before the Ardougne lever teleports them into the deep Wilderness.
 */
fun warn(plr: Player, lever: GameObject) {
    plr.newDialogue()
        .text("Warning! Pulling the lever will teleport you deep into the wilderness.")
        .options("Yes I'm brave.", { pull(it, lever, WildernessLever.ARDOUGNE) },
                 "Eep! The wilderness... No thank you.", { it.overlays.closeWindows() },
                 "Yes please, don't show this message again.", {
                     it.skipWildernessLeverWarning = true
                     pull(it, lever, WildernessLever.ARDOUGNE)
                 })
        .title("Are you sure you wish to pull it?").open()
}

for (type in WildernessLever.entries) {
    object1(type.id) {
        if (type == WildernessLever.ARDOUGNE && !plr.skipWildernessLeverWarning) {
            warn(plr, gameObject)
        } else {
            // TODO Once the Mage Arena is added, only players who have started it may pull MAGE_ARENA_ENTRANCE. Others
            //  get Kolodion (905): "You're not allowed in there. Come downstairs if you want to enter my arena."
            pull(plr, gameObject, type)
        }
    }
}
