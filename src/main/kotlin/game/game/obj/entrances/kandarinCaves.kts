package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.firemaking.Log
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.Position
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.varp.Varbit

/**
 * Glarial's amulet, which has to be worn to open the door behind Baxtorian Falls.
 */
val GLARIALS_AMULET = 295

/**
 * Glarial's pebble, which opens her tomb.
 */
val GLARIALS_PEBBLE = 294

/**
 * Where players washed over Baxtorian Falls end up.
 */
val RIVERSIDE = Position(2527, 3413)

/**
 * The equipment slots whose items Glarial's tomb won't let in: weapons, armour and ammunition.
 */
val BANNED_SLOTS = setOf(Equipment.HEAD, Equipment.CAPE, Equipment.WEAPON, Equipment.CHEST, Equipment.SHIELD,
                         Equipment.LEGS, Equipment.HANDS, Equipment.AMMUNITION)

/**
 * The other items Glarial's tomb won't let in: runes, cannon parts, fletching materials, a knife, unstrung bows and
 * logs.
 */
val BANNED_ITEMS = (554..566) + setOf(6, 8, 10, 12) + setOf(52, 53, 946, 1777) + ((48..72 step 2) - 52) +
    Log.values().map { it.id }

/**
 * The casket fished up with a big net, which unlike clue caskets is let into Glarial's tomb.
 */
val FISHING_CASKET = 405

/**
 * The bucket of water and spade used to dig through the mud patch into the West Ardougne sewer.
 */
val BUCKET_OF_WATER = 1929
val BUCKET = 1925
val SPADE = 952

/**
 * Digging with a spade.
 */
val DIG = Animation(830)

/**
 * The varbit that shows the dug hole in the mud patch.
 */
val DUG_HOLE_VARBIT = 1785

/**
 * How many buckets of water the player has poured on the mud patch, and whether they've dug through it.
 */
var Player.mudPatchWater by Attr.int().persist("mud_patch_water")
var Player.mudPatchDug by Attr.boolean().persist("mud_patch_dug")

/**
 * Returns `true` if [id] is a clue scroll or a clue scroll's casket.
 */
fun clueItem(id: Int): Boolean {
    val name = ItemDefinition.ALL[id].map { it.name }.orElse("")
    return name == "Clue scroll" || (name == "Casket" && id != FISHING_CASKET)
}

/**
 * Returns `true` if [plr] is carrying or wearing anything Glarial's tomb won't let in.
 */
fun carryingBannedItems(plr: Player): Boolean {
    val worn = plr.equipment.filterNotNull()
    val carried = plr.inventory.filterNotNull()
    return worn.any { EquipmentDefinition.ALL[it.id].map { def -> def.index in BANNED_SLOTS }.orElse(false) } ||
        carried.any { EquipmentDefinition.ALL[it.id].map { def -> def.index in BANNED_SLOTS }.orElse(false) } ||
        (worn + carried).any { it.id in BANNED_ITEMS || clueItem(it.id) }
}

// The door on the ledge behind Baxtorian Falls, which only opens for players wearing Glarial's amulet.
for (ledge in listOf(2010, 2011, 2012)) {
    object1(ledge) {
        if (plr.equipment[Equipment.AMULET]?.id == GLARIALS_AMULET) {
            plr.sendMessage("You enter the waterfall.")
            plr.move(Position(2575, 9861))
            return@object1
        }
        plr.sendMessage("You try to open the door, but the ledge is suddenly flooded with water...")
        Entrances.sequence(plr, null, {
            plr.sendMessage("...you are pushed over the waterfall and into the river.")
            plr.playSound(Sound.WATERSPLASH)
            plr.move(RIVERSIDE)
        }, {
            plr.speak("Ouch!")
            plr.damage(8)
        })
    }
}

// Glarial's tombstone: its epitaph, and the pebble that opens it.
object1(1992) {
    Entrances.sequence(plr, { plr.sendMessage("The grave is covered in elven script.") }, null, null,
                       { plr.sendMessage("Some of the writing is in common tongue, it reads:") }, null, null,
                       { plr.sendMessage("Here lies Glarial, wife of Baxtorian,") }, null, null,
                       { plr.sendMessage("true friend of nature in life and death.") }, null, null,
                       { plr.sendMessage("May she now rest knowing") }, null, null,
                       { plr.sendMessage("only visitors with peaceful intent can enter.") })
}
useItem(GLARIALS_PEBBLE).onObject(1992) {
    plr.sendMessage("You place the pebble in the gravestone's small indent.")
    plr.sendMessage("It fits perfectly.")
    if (carryingBannedItems(plr)) {
        Entrances.sequence(plr, null, null, { plr.sendMessage("But nothing happens.") })
        return@onObject
    }
    Entrances.sequence(plr, null, null, { plr.sendMessage("You hear a loud creak.") }, null, null,
                       { plr.sendMessage("The stone slab slides back revealing a ladder down.") }, null, null, {
                           plr.move(Position(2554, 9844))
                           plr.sendMessage("You climb down to an undergound passage.")
                       })
}

/**
 * Digs [plr] through the softened mud patch into the sewer.
 */
fun digThroughMud(plr: Player) {
    plr.animation(DIG)
    plr.newDialogue()
        .text("You dig deep into the soft soil... Suddenly it crumbles away!")
        .then {
            it.mudPatchDug = true
            it.sendVarbit(Varbit(DUG_HOLE_VARBIT, 1))
            it.move(Position(2518, 9760))
            it.newDialogue().text("You fall through...", "...you land in the sewer.").open()
        }
        .open()
}

// The mud patch in Edmond's garden, softened with four buckets of water and dug through with a spade (Plague City).
// Once it's been dug, the hole stays open.
// TODO Once Plague City is added, the patch only takes water and a spade at that point in the quest, Edmond follows
//  the player down, and the hole is filled in again after the quest.
for (patch in listOf(2531, 2532)) {
    useItem(BUCKET_OF_WATER).onObject(patch) {
        if (plr.mudPatchWater >= 4) {
            plr.newDialogue().text("You don't need to pour any more water. The soil is soft enough",
                                   "already.").open()
            return@onObject
        }
        if (plr.inventory.remove(usedItemIndex, Item(BUCKET_OF_WATER))) {
            plr.inventory.add(Item(BUCKET))
            plr.mudPatchWater++
            plr.newDialogue()
                .text("You pour water onto the soil.", if (plr.mudPatchWater == 4)
                    "The soil is now soft enough to dig into." else "The soil softens slightly.")
                .open()
        }
    }
    useItem(SPADE).onObject(patch) {
        if (plr.mudPatchWater < 4) {
            plr.newDialogue().text("You dig the soil...", "The ground is rather hard.").open()
        } else {
            digThroughMud(plr)
        }
    }
}

// The dug hole back down into the sewer.
object1(2532) {
    if (plr.mudPatchDug) {
        plr.sendMessage("You climb down into the hole.")
        plr.move(Position(2518, 9760))
    }
}

on(LoginEvent::class) {
    plr.mudPatchWater
    if (plr.mudPatchDug) {
        plr.sendVarbit(Varbit(DUG_HOLE_VARBIT, 1))
    }
}
