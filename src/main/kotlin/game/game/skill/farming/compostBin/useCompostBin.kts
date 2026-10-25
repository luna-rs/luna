package game.skill.farming.compostBin

import api.predef.*
import game.player.Sound
import game.skill.farming.*
import io.luna.game.model.item.*
import io.luna.game.model.mob.*

/**
 * Puts as many of [itemId] as the player has, up to a full bin, in the compost bin [objectId].
 */
fun fillCompostBin(plr: Player, objectId: Int, itemId: Int) {
    val bin = Farming.findCompostBin(objectId, plr) ?: return
    val type = CompostBinType.lookup(itemId) ?: return
    when {
        bin.closed -> plr.sendMessage("The compost bin is closed.")
        bin.open -> plr.sendMessage("You need to empty the compost bin first.")
        !bin.canFill() -> plr.sendMessage("The compost bin is already full.")
        else -> {
            val count = minOf(plr.inventory.computeAmountForId(itemId), CompostBin.MAX_ITEMS - bin.amount)
            plr.inventory.remove(Item(itemId, count))
            bin.fill(type, count)
            plr.playSound(Sound.FARMING_PUTIN)
            plr.sendMessage("You put ${itemName(itemId).lowercase()} in the compost bin.")
            Farming.commit(plr)
        }
    }
}

/**
 * Handles the first option of the compost bin [objectId]: closing a full bin, opening a rotted one, or taking a rotten
 * tomato out of an open bin of rotten tomatoes.
 */
fun compostBinOption(plr: Player, objectId: Int) {
    val bin = Farming.findCompostBin(objectId, plr) ?: return
    val now = System.currentTimeMillis()
    when {
        bin.canClose() -> {
            bin.close(now, CompostBin.ROT_MINUTES.random())
            plr.playSound(Sound.COMPOST_CLOSE)
            plr.sendMessage("You close the compost bin.")
            plr.sendMessage("The contents have begun to rot.")
            Farming.commit(plr)
        }

        bin.closed && !bin.isRotted(now) -> plr.sendMessage("The contents of the compost bin are still rotting.")
        bin.closed -> {
            bin.open()
            plr.playSound(Sound.COMPOST_OPEN)
            plr.sendMessage("You open the compost bin.")
            Farming.commit(plr)
        }

        bin.canTakeOut() && bin.type == CompostBinType.TOMATOES -> {
            val tomato = Item(CompostBinType.TOMATOES.product)
            if (!plr.inventory.hasSpaceFor(tomato)) {
                plr.sendMessage("You don't have enough inventory space.")
                return
            }
            plr.inventory.add(tomato)
            plr.farming.addExperience(CompostBinType.TOMATOES.experience)
            plr.sendMessage("You take a rotten tomato out of the compost bin.")
            bin.takeOut()
            Farming.commit(plr)
        }
    }
}

/**
 * Fills an empty bucket with compost or supercompost from the open compost bin [objectId].
 */
fun takeCompost(plr: Player, objectId: Int) {
    val bin = Farming.findCompostBin(objectId, plr) ?: return
    val type = bin.type
    if (type == null || type == CompostBinType.TOMATOES || !bin.canTakeOut()) {
        return
    }
    plr.inventory.replace(Farming.BUCKET, type.product)
    plr.farming.addExperience(type.experience)
    plr.sendMessage("You fill the bucket with ${itemName(type.product).lowercase()}.")
    bin.takeOut()
    Farming.commit(plr)
}

CompostBinLocation.values().forEach { location ->
    val objectId = location.objectId

    // Hook for filling compost bins
    CompostBinType.itemIds().forEach { itemId ->
        useItem(itemId).onObject(objectId) { fillCompostBin(plr, objectId, itemId) }
    }

    // Hook for closing, opening and taking rotten tomatoes out of compost bins
    object1(objectId) { compostBinOption(plr, objectId) }

    // Hook for taking compost and supercompost out of compost bins
    useItem(Farming.BUCKET).onObject(objectId) { takeCompost(plr, objectId) }
}
