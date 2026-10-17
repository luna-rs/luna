package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player

/**
 * A rope.
 */
val ROPE = 954

/**
 * The crevice west of Nardah, down to the Genie's cave, and the root by the River Elid, down to its cave.
 */
val NARDAH_CREVICE = 10416
val ELID_ROOT = 6382

/**
 * Whether the player has tied a rope to the crevice west of Nardah.
 */
var Player.nardahCreviceRoped by Attr.boolean().persist("nardah_crevice_roped")

/**
 * The slave clothes that have to be worn to pass the guards in the desert mining camp.
 */
val SLAVE_CLOTHES = mapOf(Equipment.CHEST to 1844, Equipment.LEGS to 1845, Equipment.BOOTS to 1846)

/**
 * The mining camp's mercenaries.
 */
val MERCENARIES = setOf(828, 829)

// The crevice west of Nardah, which needs a rope tied to it once (Spirits of the Elid).
// TODO Once darkness is added, players without a light source get eaten by bugs in the Genie's cave.
object1(NARDAH_CREVICE) {
    if (plr.nardahCreviceRoped) {
        Entrances.travel(plr, Position(3374, 9305), delay = 1, animation = 827)
    } else {
        plr.sendMessage("I'm not going down there without a rope.")
    }
}
useItem(ROPE).onObject(NARDAH_CREVICE) {
    if (!plr.nardahCreviceRoped && plr.inventory.remove(ROPE)) {
        plr.nardahCreviceRoped = true
    }
    Entrances.travel(plr, Position(3374, 9305), delay = 1, animation = 827)
}

// The root by the River Elid, which a rope is tied to every time (Spirits of the Elid).
// TODO Once Spirits of the Elid is added, the root can only be used once the quest has been started.
useItem(ROPE).onObject(ELID_ROOT) { Entrances.travel(plr, Position(3349, 9536), delay = 1, animation = 827) }

/**
 * Returns `true` if [plr] is wearing the full set of slave clothes.
 */
fun dressedAsSlave(plr: Player) = SLAVE_CLOTHES.all { (slot, id) -> plr.equipment[slot]?.id == id }

/**
 * Has a mercenary near [plr] say [text], if there is one.
 */
fun mercenarySays(plr: Player, text: String) {
    world.locator.findNpcs(plr.position, 8) { it.id in MERCENARIES }.firstOrNull()?.speak(text)
}

/**
 * [plr] pushes the mine doors, which only open for slaves. [leaving] is `true` from inside the mine.
 */
fun pushMineDoors(plr: Player, leaving: Boolean) {
    plr.sendMessage("You push the door.")
    if (!dressedAsSlave(plr)) {
        // TODO Once The Tourist Trap is added, players who haven't finished it are thrown in the cell.
        Entrances.sequence(plr, null, null, { plr.speak("Ugh!") }, {
            mercenarySays(plr, "Oi you!")
            plr.sendMessage("A guard notices you and approaches...")
        }, null, null, { mercenarySays(plr, "Hey, you're no slave, where do you think you're going!") }, null, {
            mercenarySays(plr, "Guards! Guards!")
            plr.sendMessage("No other guards come to the rescue.")
        })
        return
    }
    if (leaving) {
        Entrances.sequence(plr, null, null, {
            plr.speak("Ugh!")
            plr.sendMessage("The doors open with some effort!")
        }, { plr.move(Position(3301, 3036)) })
    } else {
        Entrances.sequence(plr, null, null, {
            plr.speak("Ugh!")
            plr.sendMessage("The doors open with some effort!")
            plr.playSound(Sound.DOOR_OPEN)
            plr.move(Position(3278, 9426))
        }, { plr.walking.addStep(Direction.NORTH) })
    }
}

// The desert mining camp's doors down into the mine, and back out.
for (door in listOf(2675, 2676)) {
    object1(door) { pushMineDoors(plr, leaving = false) }
    object2(door) {
        plr.newDialogue()
            .text("You watch the doors for some time.", "You notice that only slaves seem to go down there.",
                  "You might be able to sneak down if you pass as a slave.")
            .open()
    }
}
for (door in listOf(2690, 2691)) {
    object1(door) { pushMineDoors(plr, leaving = true) }
    object2(door) { plr.sendMessage("Nothing much seems to happen.") }
}

// The mine caves between the two halves of the mine. Slaves are let through.
// TODO Once The Tourist Trap is added, the guards block the way further in ("Two guards block your way further into
//  the caves." "Hey you, move away from there!") until the pineapple has been given, and search players who aren't
//  dressed as slaves.
for (cave in listOf(2698, 2699)) {
    object1(cave) {
        if (!dressedAsSlave(plr)) {
            return@object1
        }
        if (plr.position.x < gameObject.position.x) {
            Entrances.sequence(plr, { plr.sendMessage("You walk into the dark of the cavern...") }, null, {
                plr.sendMessage("And emerge in a different part of this huge underground complex.")
                plr.move(Position(3286, 9415))
            })
        } else {
            Entrances.sequence(plr, { plr.sendMessage("You walk into the darkness of the cavern...") }, null, {
                plr.sendMessage("...and emerge in a different part of this huge underground complex.")
                plr.move(Position(3278, 9415))
            })
        }
    }
}

// Saved attributes are only kept if they're read during the session (luna-rs/luna#610).
on(LoginEvent::class) { plr.nardahCreviceRoped }
