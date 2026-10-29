package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.*
import game.content.miningGuild.MiningGuild
import game.content.craftingGuild.CraftingGuild
import game.obj.doors.BrassKeyDoor
import game.obj.doors.Doors
import game.obj.doors.JewellersDoor
import game.skill.thieving.lockedDoor.ChaosDruidTowerDoorAction
import game.skill.thieving.lockedDoor.YanilleDoorAction
import kotlin.time.Duration.Companion.seconds

/**
 * Crosses skill-gated and scripted doors through their normal click handlers, retrying failed lockpicks.
 *
 * @author lare96
 */
object ConditionalDoorCrossing : CrossingHandler {

    const val ID = "conditional_door"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the leg has no object"
        val id = action.objectId ?: return "the leg has no object id"
        val position = action.objectPosition ?: return "the leg has no object position"
        if (id != YanilleDoorAction.DOOR && id != ChaosDruidTowerDoorAction.DOOR && id != MiningGuild.DOOR &&
            id != CraftingGuild.DOOR && id != JewellersDoor.DOOR && id != BrassKeyDoor.DOOR) {
            return "unsupported conditional door $id"
        }
        val bot = context.bot
        repeat(20) {
            if (context.arrived(leg, 0)) {
                return null
            }
            context.walk(leg.from, 0)?.let { return it }
            if (!waitFor(10.seconds) { context.find(position, id) != null }) {
                return "door $id at $position did not close"
            }
            val door = context.find(position, id) ?: return@repeat
            val yanillePick = id == YanilleDoorAction.DOOR &&
                    YanilleDoorAction.isLocked(position, door.direction, bot.position)
            if (yanillePick && (bot.thieving.level < YanilleDoorAction.REQUIRED_LEVEL ||
                        !bot.inventory.contains(YanilleDoorAction.LOCKPICK))) {
                return "the door requires level 82 Thieving and a carried lockpick"
            }
            val towerPick = id == ChaosDruidTowerDoorAction.DOOR &&
                    ChaosDruidTowerDoorAction.isLocked(position, door.direction, bot.position)
            if (towerPick && bot.thieving.level < ChaosDruidTowerDoorAction.REQUIRED_LEVEL) {
                return "the door requires level ${ChaosDruidTowerDoorAction.REQUIRED_LEVEL} Thieving"
            }
            val picking = yanillePick || towerPick
            if (id == MiningGuild.DOOR && bot.mining.level < MiningGuild.REQUIRED_LEVEL) {
                return "the door requires level 60 Mining"
            }
            if (id == CraftingGuild.DOOR && Doors.isEntering(position, door.direction, bot.position) &&
                !CraftingGuild.canEnter(bot)) {
                return "the door requires level 40 Crafting and a worn brown apron"
            }
            if (id == BrassKeyDoor.DOOR) {
                val key = bot.inventory.computeIndexForId(BrassKeyDoor.KEY)
                if (key == -1) {
                    return "the door requires a carried brass key"
                }
                bot.output.useItemOnObject(key, BrassKeyDoor.KEY, door)
            } else if (!context.handler.interactions.interactOnLeg(if (picking) 2 else 1, door)) {
                return "could not reach door $id at $position"
            }
            if (context.awaitArrival(leg, 0, 4.seconds)) {
                return null
            }
        }
        return "could not cross door $id after 20 attempts"
    }
}
