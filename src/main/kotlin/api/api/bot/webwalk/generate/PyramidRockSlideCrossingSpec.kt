package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.PyramidRockSlideCrossing
import game.skill.agility.pyramid.AgilityPyramid
import io.luna.game.model.Position

/**
 * The native western rock slide into the Agility Pyramid yard and its unrestricted climb back out.
 * Each direction clicks the slide on its own side, retaining the script's six-tile movement and row.
 *
 * @author lare96
 */
object PyramidRockSlideCrossingSpec {
    val SPEC = CrossingSpec(PyramidRockSlideCrossing.ID, setOf(AgilityPyramid.ROCK_SLIDE),
        bidirectional = false, cost = 7,
        placements = (2843..2846).flatMap { y ->
            listOf(
                CrossingPlacement(AgilityPyramid.ROCK_SLIDE, Position(3344, y), Position(3343, y), Position(3349, y),
                    requirements = Requirements(flags = setOf(PyramidRockSlideCrossing.ENTRY_READY))),
                CrossingPlacement(AgilityPyramid.ROCK_SLIDE, Position(3348, y), Position(3349, y), Position(3343, y)))
        })
}
