package game.skill.agility.course

import game.skill.agility.Agility.course
import game.skill.agility.AgilityCourse.YANILLE_DUNGEON
import game.skill.agility.AgilityObstacle.*
import game.skill.agility.yanille.YanilleDungeon.BARS_NORTH
import game.skill.agility.yanille.YanilleDungeon.BARS_SOUTH
import game.skill.agility.yanille.YanilleDungeon.LEDGE
import game.skill.agility.yanille.YanilleDungeon.LEDGE_NORTH
import game.skill.agility.yanille.YanilleDungeon.LEDGE_SOUTH
import game.skill.agility.yanille.YanilleDungeon.MONKEY_BARS
import game.skill.agility.yanille.YanilleDungeon.PIPE
import game.skill.agility.yanille.YanilleDungeon.PIPE_EAST
import game.skill.agility.yanille.YanilleDungeon.PIPE_WEST
import game.skill.agility.yanille.YanilleDungeon.RUBBLE_DOWN
import game.skill.agility.yanille.YanilleDungeon.RUBBLE_UP
import game.skill.agility.yanille.YanilleDungeon.climbRubble
import game.skill.agility.yanille.YanilleDungeon.crossLedge
import game.skill.agility.yanille.YanilleDungeon.fallFromBars
import game.skill.agility.yanille.YanilleDungeon.fallFromLedge
import game.skill.agility.yanille.YanilleDungeon.slipsOnBars
import game.skill.agility.yanille.YanilleDungeon.slipsOnLedge
import game.skill.agility.yanille.YanilleDungeon.squeezeThroughPipe
import game.skill.agility.yanille.YanilleDungeon.swingAcrossBars
import io.luna.game.model.Position

course(YANILLE_DUNGEON) {
    obstacle(LEDGE, YANILLE_LEDGE,
        checkpoint = { false },
        startTile = { if (y >= 9516) LEDGE_NORTH else LEDGE_SOUTH },
        failsIf = { slipsOnLedge() },
        fail = { fallFromLedge() }) {
        crossLedge()
    }

    obstacle(PIPE, YANILLE_PIPE,
        checkpoint = { false },
        startTile = { if (x >= 2575) PIPE_EAST else PIPE_WEST }) {
        squeezeThroughPipe()
    }

    obstacle(MONKEY_BARS, YANILLE_MONKEY_BARS,
        checkpoint = { false },
        startTile = { Position(x.coerceIn(2598, 2599), if (y >= 9492) BARS_NORTH else BARS_SOUTH) },
        failsIf = { slipsOnBars() },
        fail = { fallFromBars() }) {
        swingAcrossBars()
    }

    obstacle(RUBBLE_UP, YANILLE_RUBBLE_UP,
        checkpoint = { false },
        startTile = { Position(x.coerceIn(2616, 2617), 9571) }) {
        climbRubble(true)
    }

    obstacle(RUBBLE_DOWN, YANILLE_RUBBLE_DOWN, checkpoint = { false }) {
        climbRubble(false)
    }
}
