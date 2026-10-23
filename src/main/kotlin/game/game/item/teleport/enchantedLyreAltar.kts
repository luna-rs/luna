package game.item.teleport

import api.predef.*
import api.predef.ext.*
import game.item.teleport.EnchantedLyre.LYRE
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import java.util.function.BiFunction

/**
 * The Strange altar south-west of Rellekka, where fish are offered to Fossegrimen.
 */
val STRANGE_ALTAR = 4141

/**
 * Fossegrimen, the lake spirit who enchants lyres.
 */
val FOSSEGRIMEN = 1273

/**
 * The tile Fossegrimen appears on, south of the altar.
 */
val FOSSEGRIMEN_SPAWN = Position(2626, 3596)

/**
 * The ticks Fossegrimen stays before she leaves.
 */
val FOSSEGRIMEN_TICKS = 80

/**
 * Fossegrimen's happy chathead animation for a one-line message.
 */
val FOSSEGRIMEN_HAPPY = 567

/**
 * The worthy offerings, mapped to the enchanted lyre each one gives: raw shark (2), raw manta ray (3) and raw sea
 * turtle (4).
 */
val OFFERINGS = mapOf(383 to 6125, 389 to 6126, 395 to 6127)

/**
 * The raw bass, which Fossegrimen only takes from a player wearing a [RING_OF_CHAROS_A].
 */
val RAW_BASS = 363

/**
 * The enchanted lyre a raw bass gives.
 */
val BASS_LYRE = 6125

/**
 * The activated Ring of charos.
 */
val RING_OF_CHAROS_A = 6465

/**
 * The raw fish Fossegrimen turns down: shrimps, anchovies, sardine, salmon, trout, giant carp, cod, herring, pike,
 * mackerel, tuna, swordfish, lobster, karambwan and karambwanji.
 */
val LESSER_OFFERINGS = setOf(317, 321, 327, 331, 335, 338, 341, 345, 349, 353, 359, 371, 377, 3142, 3150)

/**
 * The Fossegrimen currently at the altar, if any.
 */
var fossegrimen: Npc? = null

/**
 * Walks the player to the altar whatever item they use on it, and leaves the reach of every other object to its own
 * listeners.
 */
val altarReach = BiFunction<Player, Entity, InteractionPolicy> { _, target ->
    if (target is GameObject && target.id == STRANGE_ALTAR) InteractionPolicy.STANDARD_SIZE
    else InteractionPolicy.UNSPECIFIED
}

/**
 * Handles [fishId] from inventory [fishSlot] being offered at the altar.
 */
fun offer(plr: Player, fishId: Int, fishSlot: Int) {
    if (plr.inventory[fishSlot]?.id != fishId) {
        return
    }
    // TODO The Fremennik Trials: only players who have finished the quest can make an offering.
    if (!plr.inventory.contains(LYRE)) {
        plr.sendMessage("You need a strung lyre before you can make an offering to Fossegrimen.")
        return
    }
    val enchantedId = OFFERINGS[fishId]
    when {
        enchantedId != null -> enchant(plr, fishId, fishSlot, enchantedId)
        fishId == RAW_BASS -> offerBass(plr, fishSlot)
        fishId in LESSER_OFFERINGS -> plr.sendMessage("Fossegrimen requires a greater offering to enchant your lyre.")
        else -> plr.sendMessage("Nothing interesting happens.")
    }
}

/**
 * Takes the offering in [fishSlot] and enchants a plain lyre into [enchantedId], which goes where the fish was.
 */
fun enchant(plr: Player, fishId: Int, fishSlot: Int, enchantedId: Int) {
    if (plr.inventory[fishSlot]?.id != fishId || !plr.inventory.contains(LYRE)) {
        return
    }
    summonFossegrimen(plr)
    plr.sendMessage("Your lyre is now enchanted. You can use it to teleport to Rellekka.")
    plr.sendMessage("When the enchantment wears off you will need to re-enchant it.")
    plr.inventory.remove(Item(LYRE))
    plr.inventory[fishSlot] = Item(enchantedId)
    plr.newDialogue()
        .npc(FOSSEGRIMEN, FOSSEGRIMEN_HAPPY, "I offer you this enchantment for your worthy offering.")
        .open()
}

/**
 * Fossegrimen turns down a raw bass, unless the player's Ring of charos lets them talk her into it.
 */
fun offerBass(plr: Player, fishSlot: Int) {
    summonFossegrimen(plr)
    val dialogue = plr.newDialogue()
        .npc(FOSSEGRIMEN, "A raw bass? You should know that is not a worthy", "offering, outerlander.")
    if (plr.equipment.ring?.id == RING_OF_CHAROS_A) {
        dialogue.options(listOf(
            DialogueOption("Yes, you're right - I'll find something more worthy of you.") { declineBass(it) },
            DialogueOption("That's not a bass, it's just a very small shark.") { charmBass(it, fishSlot) }
        ))
    } else {
        dialogue.player("Yes, you're right - I'll find something more worthy of", "you.")
    }
    dialogue.open()
}

/**
 * The player takes the raw bass back.
 */
fun declineBass(plr: Player) {
    plr.newDialogue()
        .player("Yes, you're right - I'll find something more worthy of", "you.")
        .open()
}

/**
 * The player passes the raw bass in [fishSlot] off as a small shark, and Fossegrimen accepts it.
 */
fun charmBass(plr: Player, fishSlot: Int) {
    plr.newDialogue()
        .player("That's not a bass, it's just a very small shark.")
        .npc(FOSSEGRIMEN, "That is a very small shark indeed, outerlander. Small,", "but worthy.")
        .then {
            if (plr.equipment.ring?.id == RING_OF_CHAROS_A) {
                enchant(plr, RAW_BASS, fishSlot, BASS_LYRE)
            }
        }
        .open()
}

/**
 * Brings Fossegrimen up beside the altar for [FOSSEGRIMEN_TICKS] if she isn't there already, and turns her and
 * [plr] to face each other.
 */
fun summonFossegrimen(plr: Player) {
    var spirit = fossegrimen
    if (spirit == null || spirit.state != EntityState.ACTIVE || spirit.position != FOSSEGRIMEN_SPAWN) {
        val summoned = world.addNpc(FOSSEGRIMEN, FOSSEGRIMEN_SPAWN.x, FOSSEGRIMEN_SPAWN.y, FOSSEGRIMEN_SPAWN.z)
        world.scheduleOnce(FOSSEGRIMEN_TICKS) { world.removeNpc(summoned) }
        fossegrimen = summoned
        spirit = summoned
    }
    spirit.face(plr.position)
    plr.face(spirit.position)
}

on(ItemOnObjectEvent::class, altarReach)
    .filter { objectId == STRANGE_ALTAR }
    .then { offer(plr, usedItemId, usedItemIndex) }
