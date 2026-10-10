package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.obj.entrances.EntranceLanding.Companion.near
import io.luna.game.event.impl.EquipmentChangeEvent
import io.luna.game.event.impl.LoginEvent
import game.player.Sound
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.varp.Varbit
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.GameObject

/**
 * The ring of visibility, which shows the Shadow Dungeon's ladder while it's worn.
 */
val RING_OF_VISIBILITY = 4657

/**
 * The varbit that shows the Shadow Dungeon's ladder.
 */
val SHADOW_LADDER_VARBIT = 393

/**
 * Shows or hides the Shadow Dungeon's ladder for [plr], depending on whether they're wearing the ring of visibility.
 */
fun showShadowLadder(plr: Player) {
    val worn = plr.equipment[Equipment.RING]?.id == RING_OF_VISIBILITY
    plr.sendVarbit(Varbit(SHADOW_LADDER_VARBIT, if (worn) 1 else 0))
}

// Some entrances only show their option once a quest has got far enough. Luna doesn't have these quests yet, so they
// are shown to everyone.
// TODO Once these quests are added, show each entrance only once the player has got that far.
on(LoginEvent::class) {
    // Enakhra's Lament: the four secret entrances into Enakhra's temple.
    for (varbit in 1598..1601) {
        plr.sendVarbit(Varbit(varbit, 1))
    }

    // Icthlarin's Little Helper: the rock back into Sophanem.
    plr.sendVarbit(Varbit(395, 1))

    // In Aid of the Myreque: the trapdoor down to the Paterdomus library, and the boards off Ivandis's tomb.
    plr.sendVarbit(Varbit(1982, 1))
    plr.sendVarbit(Varbit(1983, 1))

    // Mourning's End Part II: the stairs back down into the Temple of Light.
    plr.sendVarbit(Varbit(1330, 1))

    // Desert Treasure: the five ice trolls killed, which clears the ice from their cave.
    plr.sendVarp(Varp(442, 5 shl 11))

    // The Golem: the golem told about the demon, which opens the door to Uzer's portal room.
    plr.sendVarp(Varp(437, 5))

    // Ernest the Chicken: the rift to the Killerwatt plane in Draynor Manor.
    plr.sendVarbit(Varbit(1766, 1))

    showShadowLadder(plr)

    // Saved attributes are only kept if they're read during the session (luna-rs/luna#610).
    plr.skipKillerwattWarning
    plr.fellIntoSnakePit
}

on(EquipmentChangeEvent::class) {
    if (index == Equipment.RING && (oldItem?.id == RING_OF_VISIBILITY || newItem?.id == RING_OF_VISIBILITY)) {
        showShadowLadder(plr)
    }
}

/**
 * Professor Oddenstein, who warns players before they go through the rift to the Killerwatt plane.
 */
val ODDENSTEIN = 286

/**
 * The rift in Draynor Manor, and the portal back from the Killerwatt plane.
 */
val KILLERWATT_RIFT = 11355
val KILLERWATT_PORTAL = 11356

/**
 * The insulated boots, which protect from the killerwatts.
 */
val INSULATED_BOOTS = 7159

/**
 * Where the rift and the portal lead.
 */
val KILLERWATT_PLANE = near(2677, 5214, 2)
val DRAYNOR_MANOR_LAB = near(3110, 3363, 2)

/**
 * Whether the player has asked not to be warned about the flashing lights again.
 */
var Player.skipKillerwattWarning by Attr.boolean().persist("skip_killerwatt_warning")

/**
 * How wide each character of the dialogue font is, in pixels, from the space to the tilde.
 */
val CHARACTER_WIDTHS = intArrayOf(7, 4, 6, 8, 8, 14, 11, 3, 4, 4, 9, 7, 3, 5, 4, 7, 9, 6, 9, 9, 8, 9, 9, 7, 9, 9, 4, 4,
                                  6, 6, 6, 7, 14, 9, 11, 10, 13, 12, 10, 11, 12, 7, 8, 12, 9, 15, 14, 12, 12, 12, 12, 9,
                                  12, 11, 11, 16, 12, 11, 10, 4, 7, 4, 5, 8, 4, 7, 7, 7, 8, 7, 5, 8, 7, 3, 4, 7, 3, 12,
                                  9, 7, 7, 7, 7, 6, 5, 9, 7, 10, 8, 9, 7, 5, 3, 5, 8)

/**
 * Splits [text] into dialogue lines no wider than a chat head's text, the way the game does. Only used where the
 * text includes the player's name, whose length varies.
 */
fun wrap(text: String): List<String> {
    fun width(line: String) = line.sumOf { CHARACTER_WIDTHS.getOrElse(it.code - 32) { 3 } }
    val lines = ArrayList<String>()
    var line = ""
    for (word in text.split(' ')) {
        val longer = if (line.isEmpty()) word else "$line $word"
        if (width(longer) <= 380 || line.isEmpty()) {
            line = longer
        } else {
            lines += line
            line = word
        }
    }
    lines += line
    return lines
}

/**
 * Sends [plr] through the rift to the Killerwatt plane.
 */
fun enterKillerwattPlane(plr: Player) {
    plr.overlays.closeWindows()
    plr.move(KILLERWATT_PLANE.from(plr.position))
}

/**
 * Professor Oddenstein warns [plr] about the killerwatts, unless they're wearing insulated boots.
 */
fun warnAboutKillerwatts(plr: Player) {
    if (plr.equipment[Equipment.BOOTS]?.id == INSULATED_BOOTS) {
        enterKillerwattPlane(plr)
        return
    }
    plr.newDialogue()
        .npc(ODDENSTEIN, "Errr, just before you go through there...")
        .player("What's the problem?")
        .npc(ODDENSTEIN, "That portal opens into a plane populated with some very",
             "shocking creatures. You should wear some kind of", "insulated armour before going there.")
        .options("Where can I get insulated armour from?", {
            it.newDialogue()
                .player("Where can I get insulated armour from?")
                .npc(ODDENSTEIN, "Well there were some pretty tough people here last",
                     "week. Said they were Slayer Masters. They were",
                     "planning on making some protective boots. You should", "speak to one of them.")
                .options("Thanks, I'll do that.", { p ->
                    p.newDialogue().player("Thanks, I'll do that.").npc(ODDENSTEIN, "No problem. See you later.")
                        .open()
                }, "I don't want to run around after Slayer Masters, I'm going through.", { p ->
                    p.newDialogue()
                        .player("I don't want to run around after Slayer Masters, I'm", "going through.")
                        .npc(ODDENSTEIN, "Fair enough, just don't say I didn't warn you")
                        .then { q -> enterKillerwattPlane(q) }
                        .open()
                })
                .open()
        }, "Thanks, I think I'll stay here for a while then.", {
            it.newDialogue().player("Thanks, I think I'll stay here for a while then.").open()
        }, "Thanks for the warning, but I'm not scared of any monster.", {
            it.newDialogue()
                .player("Thanks for the warning, but I'm not scared of any", "monster.")
                .npc(ODDENSTEIN, "Ok. Just don't say I didn't warn you")
                .then { p -> enterKillerwattPlane(p) }
                .open()
        })
        .open()
}

// The rift to the Killerwatt plane, which Professor Oddenstein warns about first.
object1(KILLERWATT_RIFT) {
    if (plr.skipKillerwattWarning) {
        warnAboutKillerwatts(plr)
        return@object1
    }
    val warning = wrap("${plr.username} before you go through there I must warn you that there are flashing " +
                           "lights and strobe effects on the other side. If you are an epilepsy sufferer you must " +
                           "NOT enter! Do you still want to go through?")
    val dialogue = plr.newDialogue()
    for (box in warning.chunked(4)) {
        dialogue.npc(ODDENSTEIN, *box.toTypedArray())
    }
    dialogue
        .options("Yes I still want to go in.", {
            it.newDialogue()
                .player("Yes I still want to go in.")
                .npc(ODDENSTEIN, "All right. Good luck then.")
                .then { p -> warnAboutKillerwatts(p) }
                .open()
        }, "Yes I want to go in and don't show me this message again.", {
            it.skipKillerwattWarning = true
            it.newDialogue()
                .player("Yes I want to go in and don't show me this message", "again.")
                .then { p -> warnAboutKillerwatts(p) }
                .open()
        }, "No, I dont want to go in.", {
            it.newDialogue()
                .player("No, I dont want to go in.")
                .npc(ODDENSTEIN, "That's a good decision, if you ask me.")
                .open()
        })
        .open()
}

object1(KILLERWATT_PORTAL) { plr.move(DRAYNOR_MANOR_LAB.from(plr.position)) }

/**
 * The sparkling pools between the Mage Arena's cave and the gods' chamber, and where each one leads.
 */
val SPARKLING_POOLS = mapOf(2878 to Position(2509, 4689), 2879 to Position(2542, 4718))

/**
 * [plr] jumps into [pool], which takes them to [destination].
 */
fun jumpIntoPool(plr: Player, pool: GameObject, destination: Position) {
    val middle = pool.position.translate(1, 1)
    plr.face(middle)
    plr.sendMessage("You step into the pool.")
    Entrances.sequence(plr, null, {
        Entrances.slide(plr, middle, 20, 35, 741)
    }, null, {
        plr.animation(Animation(804))
        plr.graphic(Graphic(68, 0, 20))
        plr.playSound(Sound.POOL_PLOP)
    }, null, {
        plr.move(destination)
        plr.animation(Animation.CANCEL)
    })
}

// The sparkling pools under the Mage Arena.
// TODO Once the Mage Arena is added, players who haven't beaten Kolodion only get their boots wet ("Your boots get
//  wet.").
for ((pool, destination) in SPARKLING_POOLS) {
    object1(pool) {
        val obj = gameObject
        plr.newDialogue()
            .text("You step into the pool of sparkling water. You feel energy rush",
                  "through your veins.")
            .then { jumpIntoPool(it, obj, destination) }
            .open()
    }
}

/**
 * The ninja monkey greegrees, the only ones agile enough for the hole on the Ape Atoll Agility Course.
 */
val NINJA_GREEGREES = setOf(4024, 4025)

/**
 * The other monkey greegrees.
 */
val OTHER_GREEGREES = (4026..4031).toSet()

// The vine-choked hole on the Ape Atoll Agility Course, down to the tch'ki nut cave (Recipe for Disaster).
// TODO Players who climb down as a human should be knocked out and thrown in the Ape Atoll jail.
object1(12581) {
    when (plr.equipment[Equipment.WEAPON]?.id) {
        in NINJA_GREEGREES -> {
            plr.sendMessage("You scamper through the vine choked hole...")
            Entrances.travel(plr, Position(3023, 5457), delay = 1,
                             arrival = "...and find yourself in front of a magnificent Monkey Nut bush.")
        }

        in OTHER_GREEGREES -> plr.sendMessage("Only the stealthiest and most agile monkey can use this!")
        else -> plr.sendMessage("You slip climbing down the hole and land hard on the floor.")
    }
}

/**
 * Whether the player has been hurt by the fall into Crash Island's snake pit, which only happens the first time.
 */
var Player.fellIntoSnakePit by Attr.boolean().persist("fell_into_snake_pit")

// The pit on Crash Island, down to the snake pit (Recipe for Disaster).
object1(12602) {
    plr.newDialogue()
        .text("It looks dangerous. Are you sure you wish to enter?")
        .options("Yes, I'm as hard as nails.", {
            it.overlays.closeWindows()
            it.move(Position(3024, 5489))
            if (!it.fellIntoSnakePit) {
                it.fellIntoSnakePit = true
                it.damage(15)
            }
        }, "No, I'm going to run away and cry.", { it.overlays.closeWindows() })
        .open()
}
