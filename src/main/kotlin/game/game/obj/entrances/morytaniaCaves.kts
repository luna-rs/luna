package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.agility.Agility
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.mob.varp.Varbit
import io.luna.game.model.`object`.GameObject

/**
 * A rope.
 */
val ROPE = 954

/**
 * Reaching out to a door or a lift.
 */
val REACH = Animation(832)

/**
 * The varbit that shows the rope tied to the edge of the rift under Mort Myre.
 */
val RIFT_ROPE_VARBIT = 2032

/**
 * The tiles of the rift under Mort Myre, which can all be climbed into once a rope is tied to the edge.
 */
val RIFT = listOf(13967, 13968, 13969, 13971, 13973, 13974, 13975, 13976, 13978)

/**
 * Whether the player has tied a rope to the edge of the rift (A Soul's Bane).
 */
var Player.riftRoped by Attr.boolean().persist("rift_roped")

/**
 * The shade keys, any of which opens the door into the Shade catacombs.
 */
val SHADE_KEYS = 3450..3469

/**
 * Climbing over the mine cart at the Haunted Mine.
 */
val CLIMB_OVER = 840

// The rift under Mort Myre, which needs a rope tied to its edge once (A Soul's Bane).
// TODO Once A Soul's Bane is added, only players who have talked to Launa can use the rift.
for (rift in RIFT) {
    object1(rift) {
        if (plr.riftRoped) {
            Entrances.travel(plr, Position(3297, 9824), delay = 1, animation = 827)
        } else {
            plr.sendMessage("Nothing interesting happens.")
        }
    }
    useItem(ROPE).onObject(rift) {
        if (!plr.riftRoped && plr.inventory.remove(ROPE)) {
            plr.riftRoped = true
            plr.sendVarbit(Varbit(RIFT_ROPE_VARBIT, 1))
            plr.animation(Animation(827))
        }
    }
}

/**
 * Opens the double door [leaf] (with [openId]) and its partner [partnerId] beside it (with [partnerOpenId]) for
 * [ticks].
 */
fun openDoubleDoor(leaf: GameObject, openId: Int, partnerId: Int, partnerOpenId: Int, ticks: Int) {
    Entrances.replace(leaf, openId, ticks)
    world.locator.findObjects(leaf.position, 1) { it.id == partnerId }.firstOrNull()?.let {
        Entrances.replace(it, partnerOpenId, ticks)
    }
}

// The doors from Mort Myre into the Hollows, and back out (In Search of the Myreque).
// TODO Once In Search of the Myreque is added, the doors only open once Curpile's questions have been answered
//  ("There seems to be a strange combination on the door. It would take ages to work it out.").
for ((door, open) in mapOf(5060 to 5062, 5061 to 5063, 5056 to 5058, 5057 to 5059)) {
    object1(door) {
        val leaf = gameObject
        val inside = leaf.position.y > 6400
        val partner = if (door % 2 == 0) door + 1 else door - 1
        val partnerOpen = if (open % 2 == 0) open + 1 else open - 1
        Entrances.sequence(plr, {
            plr.animation(REACH)
            plr.playSound(Sound.LOCKED)
        }, {
            plr.playSound(Sound.IRON_DOOR_OPEN)
            openDoubleDoor(leaf, open, partner, partnerOpen, 3)
        }, null, {
            if (inside) {
                plr.move(Position(3509, 3449))
                plr.sendMessage("You open the large wooden doors and step into the muddy marsh of Mort Myre.")
            } else {
                plr.move(Position(3500, 9811))
            }
        })
    }
}

// The false wall in the Rising Sun Inn's basement, out into the Hollows (In Search of the Myreque).
// TODO Once In Search of the Myreque is added, the wall only gives way once Veliaf has told the player about it ("You
//  search the wall but don't find anything of note.").
object1(5052) {
    plr.sendMessage("You search the wall and find the door which Veliaf told you about.")
    plr.sendMessage("You walk through.")
    plr.playSound(Sound.DOOR_OPEN)
    Entrances.replace(gameObject, 5053, 2)
    val wall = gameObject.position
    plr.move(Position(wall.x, if (plr.position.y >= wall.y) wall.y - 1 else wall.y))
}

// The stalagmite in front of the Myreque's hideout (In Search of the Myreque).
// TODO Once In Search of the Myreque is added, players who haven't been ambushed yet go into the copy of the hideout
//  upstairs, and the skeletal hellhound attacks those who have just been.
object1(5050) {
    plr.newDialogue().text("It looks like an ordinary stalagmite, however, beyond it you can see",
                           "that there is a small cave entrance.").open()
}
object2(5050) {
    if (plr.agility.level < 25) {
        plr.newDialogue().text("You need an Agility level of 25 to squeeze through here.").open()
        return@object2
    }
    Entrances.travel(plr, Position(3505, 9832), arriveDelay = true)
}

/**
 * [plr] unlocks the door into the Shade catacombs with a shade key.
 */
fun enterCatacombs(plr: Player, leaf: GameObject) {
    if (SHADE_KEYS.none { plr.inventory.contains(it) }) {
        plr.sendMessage("The door seems locked. Perhaps you need a key.")
        plr.playSound(Sound.LOCKED)
        return
    }
    plr.sendMessage("You use your Shade key to unlock the door.")
    val left = leaf.id == 4132
    Entrances.sequence(plr, { plr.animation(REACH) }, {
        plr.playSound(Sound.IRON_DOOR_OPEN)
        openDoubleDoor(leaf, if (left) 4134 else 4135, if (left) 4133 else 4132, if (left) 4135 else 4134, 2)
    }, { plr.move(Position(3493, 9725)) })
}

// The doors into the Shade catacombs under Mort'ton.
// TODO Once Shades of Mort'ton is added, the doors only open once Razmire has told the player where the shades' lair
//  is.
for (door in listOf(4132, 4133)) {
    object1(door) { enterCatacombs(plr, gameObject) }
    for (key in SHADE_KEYS) {
        useItem(key).onObject(door) { enterCatacombs(plr, gameObject) }
    }
}

// The mine cart in front of the Haunted Mine.
object1(4918) {
    if (!Agility.checkLevel(plr, 15)) {
        return@object1
    }
    val direction = Direction.between(plr.position, gameObject.position)
    val destination = plr.position.translate(direction.translateX * 2, direction.translateY * 2)
    plr.playSound(Sound.CLIMB_WALL)
    Entrances.sequence(plr, { Entrances.slide(plr, destination, 30, 100, CLIMB_OVER) }, null, null)
}

// The Haunted Mine's lift down into the flooded chamber, which soaks any tinderbox.
// TODO Once Haunted Mine is added, the lift needs the valve open while the quest is at that point ("The lift doesn't
//  respond, it needs to be powered in some way.").
// TODO Once lit light sources can be put out, the water puts them out.
for (lift in listOf(4937, 4938, 4940)) {
    object1(lift) {
        plr.sendMessage("The lift descends further into the mines...")
        plr.move(Position(2725, 4456))
        plr.playSound(Sound.LIFT_FALL)
        Entrances.sequence(plr, null, {
            plr.sendMessage("...plunging you straight into the middle of a chamber flooded with water.")
            plr.move(Position(2725, 4452))
            val tinderboxes = plr.inventory.computeAmountForId(590)
            if (tinderboxes > 0 && plr.inventory.remove(Item(590, tinderboxes))) {
                plr.inventory.add(Item(4073, tinderboxes))
                plr.sendMessage("The water soaks your tinderbox!")
            }
        })
    }
}

/**
 * How close players have to wade to the flooded lift to get back in.
 */
val LIFT_REACH = InteractionPolicy(InteractionType.LINE_OF_SIGHT, 4)

// The flooded lift back up, which can be got into from a few tiles away.
object1(4942, { _, _ -> LIFT_REACH }) {
    plr.playSound(Sound.LIFT_FALL)
    plr.move(Position(2807, 4493))
}

/**
 * The star amulet, which unlocks the memorial over Fenkenstrain's experiment cave.
 */
val STAR_AMULET = 4183

/**
 * The memorial in the graveyard south of Fenkenstrain's castle, over the experiment cave.
 */
val GRAVEYARD_MEMORIAL = Position(3578, 3527)

/**
 * Whether the player has put the star amulet into the graveyard memorial (Creature of Fenkenstrain).
 */
var Player.starAmuletPlaced by Attr.boolean().persist("star_amulet_placed")

// The memorials over Fenkenstrain's experiment cave, which are pushed aside to climb down: the one in the graveyard,
// once the star amulet is in it, and the one in the mausoleum.
object1(5167) {
    if (gameObject.position == GRAVEYARD_MEMORIAL) {
        if (!plr.starAmuletPlaced) {
            plr.sendMessage("The coffin is incredibly heavy, and does not budge.")
            return@object1
        }
        Entrances.travel(plr, Position(3577, 9927), delay = 1)
    } else {
        Entrances.travel(plr, Position(3504, 9969), delay = 1)
    }
}
useItem(STAR_AMULET).onObject(5167) {
    if (gameObject.position == GRAVEYARD_MEMORIAL && !plr.starAmuletPlaced &&
        plr.inventory.remove(usedItemIndex, Item(STAR_AMULET))) {
        plr.starAmuletPlaced = true
        plr.sendMessage("The star amulet fits exactly into the depression on the coffin lid.")
    }
}

// Saved attributes are only kept if they're read during the session (luna-rs/luna#610).
on(LoginEvent::class) {
    plr.starAmuletPlaced
    if (plr.riftRoped) {
        plr.sendVarbit(Varbit(RIFT_ROPE_VARBIT, 1))
    }
}
