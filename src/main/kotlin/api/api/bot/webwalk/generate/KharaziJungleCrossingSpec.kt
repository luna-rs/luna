package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.KharaziJungleCrossing
import api.predef.ctx
import game.skill.woodcutting.jungle.KharaziJungle
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.model.Direction

/**
 * Generates the source's two-tile jungle cuts from actual revision-377 tree/bush placements. Each cardinal
 * direction follows KharaziJungle.destination exactly; reverse traversal has the same native tool checks.
 * Source quest admission remains unchanged, including its existing Legends placeholder.
 *
 * @author lare96
 */
object KharaziJungleCrossingSpec {
    val SPEC = CrossingSpec(KharaziJungleCrossing.ID, KharaziJungle.STUMPS.keys,
        requirements = Requirements(flags = setOf(KharaziJungleCrossing.READY)), cost = 20)

    /**
     * Only one-tile source obstacles are represented. Two axes plus bidirectional edges cover all four
     * ordinary native approaches; no guessed coordinate or fabricated stand is exported.
     */
    fun placements(table: MapIndexTable): List<CrossingPlacement> = table.objectSet.asSequence().filter {
        it.objectId in KharaziJungle.STUMPS && it.position.z == 0
    }.filter {
        val definition = it.toGameObject(ctx).def()
        definition.sizeX == 1 && definition.sizeY == 1
    }.flatMap { obj ->
        sequenceOf(Direction.NORTH, Direction.EAST).map { direction ->
            CrossingPlacement(obj.objectId, obj.position, obj.position.translate(1, direction.opposite()),
                obj.position.translate(1, direction))
        }
    }.toList()
}
