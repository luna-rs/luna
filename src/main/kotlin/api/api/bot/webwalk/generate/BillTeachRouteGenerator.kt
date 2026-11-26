package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.BillTeachCrossing
import api.predef.world
import engine.obj.LadderDestination
import game.content.mosLeHarmless.BillTeach
import game.content.sailing.ShipGangplanks
import io.luna.game.model.Direction
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject

/**
 * Connects each dock and deck through the native gangplanks, and both decks through Bill's dialogue.
 *
 * @author lare96
 */
internal object BillTeachRouteGenerator {

    private val ports = BillTeach.Port.entries
    val plankIds = ports.flatMap { listOf(it.boardPlank, it.leavePlank) }.toSet()

    fun gather(planks: List<GameObject>, report: GenerationReport): List<GeneratedObstacle> = buildList {
        for (port in ports) {
            plank(port, planks, aboard = true, report)?.let(::add)
            plank(port, planks, aboard = false, report)?.let(::add)

            val bill = world.locator.findNpcs(port.deck, 8) { it.id == port.npcId }.firstOrNull()
            val stand = bill?.let { npc ->
                Direction.NESW.map { npc.position.translate(1, it) }.firstOrNull {
                    LadderDestination.canLand(it) && world.collisionManager.reached(it, npc, InteractionPolicy.STANDARD_SIZE)
                }
            }
            val landing = port.other.deck
            if (bill == null || stand == null || !LadderDestination.canLand(landing)) {
                report.skipped += "Bill Teach voyage from $port: missing crew, Talk-to stand or deck landing"
                continue
            }
            add(GeneratedObstacle(EdgeType.CROSSING, null, bill.position, stand, landing, option = "Talk-to",
                handler = BillTeachCrossing.ID, cost = BillTeach.SAIL_TICKS + 8,
                requirements = Requirements(flags = setOf(BillTeachCrossing.ADMISSION)),
                bidirectional = false, npcId = port.npcId))
            report.count("Bill Teach voyages")
        }
    }

    private fun plank(port: BillTeach.Port, planks: List<GameObject>, aboard: Boolean,
                      report: GenerationReport): GeneratedObstacle? {
        val id = if (aboard) port.boardPlank else port.leavePlank
        val plank = planks.singleOrNull { it.id == id && it.position.computeLongestDistance(port.deck) <= 8 }
        val plane = port.deck.z - if (aboard) 1 else 0
        val stand = plank?.let { obj ->
            val approach = if (aboard) ShipGangplanks.landing(obj, plane + 1, false) else port.deck
            EntranceStands.of(obj).filter { it.z == plane && LadderDestination.canLand(it) }
                .minByOrNull { it.computeLongestDistance(approach) }
        }
        val landing = plank?.let { ShipGangplanks.landing(it, plane, aboard) }
        val option = plank?.def()?.actions?.firstOrNull()
        if (plank == null || stand == null || landing == null || option == null || !LadderDestination.canLand(landing)) {
            report.skipped += "Bill Teach gangplank $id at $port: missing object, click option, stand or landing"
            return null
        }
        report.count("Bill Teach gangplanks")
        return GeneratedObstacle(EdgeType.ENTRANCE, id, plank.position, stand, landing, option,
            cost = 3, bidirectional = false)
    }
}
