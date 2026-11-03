package api.bot.webwalk.generate

import api.bot.webwalk.walk.crossing.AgilityShortcutCrossing
import api.bot.webwalk.walk.crossing.ConditionalDoorCrossing
import game.obj.doors.TenzingDoors
import game.skill.agility.DeathPlateauStile

/**
 * The native hut doors and unrestricted stile on the southern Troll Stronghold approach.
 *
 * @author lare96
 */
object TenzingCrossingSpec {

    val DOORS = CrossingSpec(ConditionalDoorCrossing.ID, TenzingDoors.IDS, cost = 4,
        placements = TenzingDoors.PASSAGES.map {
            CrossingPlacement(it.id, it.position, it.position, it.otherSide)
        })

    val STILE = CrossingSpec(AgilityShortcutCrossing.ID, setOf(DeathPlateauStile.ID), cost = 6,
        placements = listOf(CrossingPlacement(DeathPlateauStile.ID, DeathPlateauStile.POSITION,
            DeathPlateauStile.SOUTH, DeathPlateauStile.NORTH)))
}
