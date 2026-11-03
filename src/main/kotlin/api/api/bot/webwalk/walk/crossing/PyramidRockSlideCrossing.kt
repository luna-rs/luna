package api.bot.webwalk.walk.crossing

import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import game.skill.agility.pyramid.AgilityPyramid
import io.luna.game.model.Position
import io.luna.game.model.mob.Skill
import kotlin.time.Duration.Companion.seconds

/**
 * Clicks the Pyramid yard's existing rock-slide action, preserving its current-level check and obstacle experience.
 * Only the exact opposite stand completes the leg; the raised course and its progress remain separate.
 *
 * @author lare96
 */
object PyramidRockSlideCrossing : CrossingHandler {
    const val ID = "pyramid_rock_slide"
    const val ENTRY_READY = "pyramid_rock_slide_entry"

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Pyramid rock slide has no object"
        val position = action.objectPosition ?: return "the Pyramid rock slide has no position"
        val down = leg.from.x == 3343
        val fromX = if (down) 3343 else 3349
        val toX = if (down) 3349 else 3343
        val objectX = if (down) 3344 else 3348
        if (action.objectId != AgilityPyramid.ROCK_SLIDE || leg.from.y !in 2843..2846 ||
            leg.from != Position(fromX, leg.from.y) || leg.to != Position(toX, leg.from.y) ||
            position != Position(objectX, leg.from.y)) return "unsupported Pyramid rock-slide passage"
        if (context.arrived(leg, 0)) return null
        context.walk(leg.from, 0)?.let { return it }
        if (down && context.bot.skill(Skill.AGILITY).level < 30)
            return "entering the Pyramid yard requires current Agility 30"
        val slide = context.find(position, AgilityPyramid.ROCK_SLIDE) ?: return "the Pyramid rock slide is missing"
        if (!context.handler.interactions.interactOnLeg(1, slide)) return "could not reach the Pyramid rock slide"
        return if (context.awaitArrival(leg, 0, 8.seconds)) null else "could not cross the Pyramid rock slide"
    }
}
