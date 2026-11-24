package game.content.dragonSlayer

import api.combat.death.DeathHookHandler
import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

on(LoginEvent::class) { DragonSlayer.refresh(plr) }
button(DragonSlayer.JOURNAL_BUTTON) { DragonSlayer.journal(plr) }

val clues = listOf("Melzar's Maze", "Wormbrain", "the Dwarven Mine")
for ((index, part) in DragonSlayer.mapParts.withIndex()) {
    item1(part) { plr.newDialogue().text("This piece of the Crandor map came from ${clues[index]}.").open() }
    for (other in DragonSlayer.mapParts.drop(index + 1)) {
        useItem(part).onItem(other) { DragonSlayer.assembleMap(plr) }
    }
}
item1(DragonSlayer.CRANDOR_MAP) {
    plr.newDialogue().text("The chart marks a route through the reefs to Crandor.").open()
}

npc1(DragonSlayerShip.KLARENSE) { DragonSlayerShip.klarense(plr, targetNpc) }
npc1(DragonSlayerShip.NED_IN_DRAYNOR) { DragonSlayerShip.nedInDraynor(plr, targetNpc) }
npc1(DragonSlayerShip.NED_ON_SHIP) { DragonSlayerShip.nedOnShip(plr, targetNpc) }
useItem(DragonSlayer.CRANDOR_MAP).onNpc(DragonSlayerShip.NED_IN_DRAYNOR) { DragonSlayerShip.giveMap(plr, targetNpc) }
npc1(DragonSlayer.GUILD_MASTER) { DragonSlayerDialogue.guildMaster(plr, targetNpc) }
npc1(DragonSlayerDialogue.ORACLE) { DragonSlayerDialogue.oracle(plr, targetNpc) }
npc1(DragonSlayerDialogue.DUKE) { DragonSlayerDialogue.duke(plr, targetNpc) }
object1(DragonSlayerShip.HOLE) { DragonSlayerShip.inspectHole(plr) }
useItem(DragonSlayerShip.PLANK).onObject(DragonSlayerShip.HOLE) { DragonSlayerShip.repair(plr) }

npc1(DragonSlayerMap.WORMBRAIN, { _, _ -> InteractionPolicy(InteractionType.LINE_OF_SIGHT, 3) }) {
    DragonSlayerMap.wormbrain(plr, targetNpc)
}
useItem(DragonSlayerDialogue.MAZE_KEY).onObject(DragonSlayerMap.MAZE_ENTRANCE) {
    DragonSlayerMap.useMazeKey(plr, gameObject, DragonSlayerDialogue.MAZE_KEY)
}
for ((index, key) in DragonSlayerMap.colouredKeys.withIndex()) {
    useItem(key).onObject(DragonSlayerMap.mazeDoors[index]) { DragonSlayerMap.useMazeKey(plr, gameObject, key) }
}
// The rat, ghost and Melzar already drop their keys in Luna's native drop tables.
for (index in listOf(2, 3, 5)) {
    DeathHookHandler.addNpcHook(DragonSlayerMap.mazeKeyNpcs[index]) {
        death { (source as? Player)?.let { DragonSlayerMap.mazeKeyDrop(it, victim, index) } }
        DeathHookHandler.defaultNpcHook?.invoke(this)
    }
}
for (item in DragonSlayerMap.offerings) {
    useItem(item.id).onObject(DragonSlayerMap.MAGIC_DOOR) { DragonSlayerMap.offer(plr, gameObject) }
}
object1(DragonSlayerMap.MAZE_CHEST) { DragonSlayerMap.open(plr, gameObject, DragonSlayerMap.MAZE_CHEST_OPEN) }
object1(DragonSlayerMap.MAZE_CHEST_OPEN) { DragonSlayerMap.search(plr, DragonSlayer.MAZE_MAP_PART) }
object2(DragonSlayerMap.MAZE_CHEST_OPEN) { DragonSlayerMap.close(plr, gameObject, DragonSlayerMap.MAZE_CHEST) }
object1(DragonSlayerMap.ORACLE_CHEST) { DragonSlayerMap.open(plr, gameObject, DragonSlayerMap.ORACLE_CHEST_OPEN) }
object1(DragonSlayerMap.ORACLE_CHEST_OPEN) { DragonSlayerMap.search(plr, DragonSlayer.ORACLE_MAP_PART) }
object2(DragonSlayerMap.ORACLE_CHEST_OPEN) { DragonSlayerMap.close(plr, gameObject, DragonSlayerMap.ORACLE_CHEST) }

DeathHookHandler.addNpcHook(DragonSlayer.ELVARG) {
    DeathHookHandler.defaultNpcHook?.invoke(this)
    death { (source as? Player)?.let { DragonSlayer.elvargDefeated(it, victim) } }
}
