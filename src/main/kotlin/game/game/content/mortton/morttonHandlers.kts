package game.content.mortton

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Sound
import game.skill.firemaking.Firemaking
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

for (wall in FlamtaerTemple.walls) {
    object1(wall) { FlamtaerTemple.repair(plr, gameObject) }
}
object1(FlamtaerTemple.UNLIT_ALTAR) { FlamtaerTemple.light(plr, gameObject) }
useItem(Firemaking.TINDERBOX).onObject(FlamtaerTemple.UNLIT_ALTAR) { FlamtaerTemple.light(plr, gameObject) }
for (oil in FlamtaerTemple.oliveOils) {
    for (altar in FlamtaerTemple.FLAME..FlamtaerTemple.BROKEN_ALTAR) {
        useItem(oil).onObject(altar) { FlamtaerTemple.bless(plr, gameObject, usedItemIndex, oil) }
    }
}

for (logs in PyreLogs.entries) {
    for (oil in FlamtaerTemple.sacredOils) {
        useItem(oil).onItem(logs.wood) {
            val oilSlot = if (usedItemId == oil) usedItemIndex else targetItemIndex
            val woodSlot = if (usedItemId == oil) targetItemIndex else usedItemIndex
            ShadePyres.makeLogs(plr, logs, oilSlot, woodSlot, oil)
        }
    }
    useItem(logs.item).onObject(ShadePyres.EMPTY) { ShadePyres.load(plr, gameObject, logs) }
    for (remains in ShadeRewards.remains) {
        useItem(remains).onObject(logs.loadedObject) { ShadePyres.place(plr, gameObject, remains) }
    }
    object1(logs.readyObject) { ShadePyres.light(plr, gameObject) }
    useItem(Firemaking.TINDERBOX).onObject(logs.readyObject) { ShadePyres.light(plr, gameObject) }
}

fun openShadeChest(player: Player, chest: GameObject, usedKey: Int? = null) {
    if (chest.state != EntityState.ACTIVE) return
    val key = ShadeRewards.keyForChest(chest.id)
    if (usedKey != null && usedKey != key || !player.inventory.remove(key)) {
        player.sendMessage("You need a ${itemName(key).lowercase()} to open this chest.")
        player.playSound(Sound.LOCKED)
        return
    }
    val world = player.world
    val opened = world.addObject(4131, chest.position, chest.objectType, chest.direction)
    world.scheduleOnce(4) {
        if (opened.state == EntityState.ACTIVE && world.removeObject(opened)) {
            world.addObject(chest.id, chest.position, chest.objectType, chest.direction)
        }
    }
    player.animation(Animations.PICKPOCKET)
    player.playSound(Sound.CHEST_OPEN)
    player.sendMessage("You find some treasure. The key dissolves in the lock.")
    ShadeRewards.chest(key, player.equipment.contains(2572)).forEach(player::giveItem)
}

for (chest in ShadeRewards.chests) {
    object1(chest) { openShadeChest(plr, gameObject) }
    for (key in 3450..3469) {
        useItem(key).onObject(chest) { openShadeChest(plr, gameObject, key) }
    }
}
